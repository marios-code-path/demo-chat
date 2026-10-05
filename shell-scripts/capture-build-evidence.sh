#!/bin/bash
#
# Keeps the evidence of a build whose compiled output went missing. Run it
# before the next build, because a build cleans target/ and the Kotlin daemon
# rotates its logs. See B13 in docs/BUILD-HEALTH.md and CHAT-lantftth.
#
#   ./shell-scripts/capture-build-evidence.sh chat-core chat-persistence-cassandra
#   ./shell-scripts/capture-build-evidence.sh -o /some/dir chat-core
#
# It writes to a new directory and prints its path. It changes nothing in the
# checkout. It keeps:
#
#   1. For each module, every file under target/classes and
#      target/test-classes, with its time to the second, and the count of
#      .class and .kt files.
#   2. The Kotlin daemon logs of the last day, from the temporary directory.
#   3. The Kotlin daemon, Maven and IDE processes, with their start times.
#   4. The Docker kill events of the last two hours.
#   5. The branch, the head commit and the short status of the checkout.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/.." && pwd )"

OUT=""
if [ "${1:-}" = "-o" ]; then
    OUT="${2:?-o needs a directory}"
    shift 2
fi
if [ "$#" -eq 0 ] || [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"
    exit 2
fi
[ -z "$OUT" ] && OUT="$(mktemp -d "${TMPDIR:-/tmp}/build-evidence.XXXXXX")"
mkdir -p "$OUT"

# A file list with times to the second. macOS and Linux spell this differently.
list_files() {
    if ls -lT / >/dev/null 2>&1; then
        find "$1" -type f -exec ls -lT {} + 2>/dev/null
    else
        find "$1" -type f -exec ls -l --time-style=full-iso {} + 2>/dev/null
    fi
}

for module in "$@"; do
    target="$ROOT/$module/target"
    {
        echo "== $module"
        for sub in classes test-classes; do
            echo "-- $sub: $(find "$target/$sub" -name '*.class' 2>/dev/null | wc -l | tr -d ' ') .class," \
                "$(find "$target/$sub" -name '*.kt' 2>/dev/null | wc -l | tr -d ' ') .kt"
        done
        ls -la "$target" 2>&1
        for sub in classes test-classes; do
            echo "-- files in $sub"
            list_files "$target/$sub"
        done
    } > "$OUT/target-$module.txt"
done

tmp="${TMPDIR:-/tmp}"
mkdir -p "$OUT/kotlin-daemon-logs"
find "$tmp" -maxdepth 1 -name 'kotlin-daemon.*.log' -mtime -1 -exec cp {} "$OUT/kotlin-daemon-logs/" \; 2>/dev/null
ls -la "$tmp" 2>/dev/null | grep -i kotlin > "$OUT/kotlin-tmp-files.txt"

ps -axo pid,ppid,lstart,command 2>/dev/null \
    | grep -i -E 'kotlin|maven|plexus-classworlds|idea|codex|surefire' \
    | grep -v -E 'grep|capture-build-evidence' > "$OUT/processes.txt"

# docker events can wait past its --until bound. Measured on 2026-10-04, with
# relative and with absolute bounds. So the call stops after 30 seconds, and
# the file then says so. Read it as incomplete in that case.
if command -v docker >/dev/null 2>&1; then
    now="$(date +%s)"
    docker events --since "$((now - 7200))" --until "$now" --filter event=oom --filter event=die \
        --format '{{.Time}} {{.Action}} {{.Actor.Attributes.image}}' > "$OUT/docker-events.txt" 2>&1 &
    events=$!
    for _ in $(seq 30); do kill -0 "$events" 2>/dev/null || break; sleep 1; done
    if kill -0 "$events" 2>/dev/null; then
        kill "$events" 2>/dev/null
        echo "docker events did not answer in 30 seconds" >> "$OUT/docker-events.txt"
    fi
fi

{
    git -C "$ROOT" branch --show-current
    git -C "$ROOT" log --oneline -1
    git -C "$ROOT" status --short
} > "$OUT/git.txt" 2>&1

echo "$OUT"
