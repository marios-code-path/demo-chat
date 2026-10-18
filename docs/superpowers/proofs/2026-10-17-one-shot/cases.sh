#!/usr/bin/env bash
# Runs the stage 0 cases. Each case is one shell JVM in its own process group,
# with a 60-second limit. The sandbox protects node 16. The lsof record is
# evidence for Q6 alone. Exit 3: a case hit a denied port. Exit 4: a case left
# a process or a 9101 listener. See the stage 0 plan of CHAT-jmnjvupr.
set -uo pipefail
S="${1:?usage: cases.sh <scratch-dir>}"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
password="$(awk '/^      Admin:/ {f=1} f && /password:/ {print $2; exit}' \
    "$root/shared-deploy-configuration/src/main/config/userinit.yml")"
now_ms() { perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }
listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

printf '%s\n' "login --username Admin --password $password" \
    'add-topic --name proof-room' 'messages proof-room' > "$S/script.txt"
printf '%s\n' "$password" > "$S/password.stdin"
printf 'id\targv\tstdin\texit\twall_ms\tstdout_bytes\tstderr_bytes\ttimed_out\tremote_ports\n' \
    > "$S/results.tsv"

# run_case ID STDIN-FILE INTERACTIVE ARGS...
run_case() {
    local id="$1" stdin="$2" interactive="$3"
    shift 3
    local start end pid status timed_out=0 ports=""
    start="$(now_ms)"
    perl -e 'setpgrp(0,0); exec @ARGV' env INTERACTIVE="$interactive" \
        "$here/launch.sh" "$S" "$@" < "$stdin" > "$S/$id.out" 2> "$S/$id.err" &
    pid=$!
    while kill -0 "$pid" 2>/dev/null; do
        for p in $(pgrep -g "$pid"); do
            ports+=" $(lsof -nP -a -p "$p" -iTCP -sTCP:ESTABLISHED 2>/dev/null \
                | awk 'NR > 1 {n = split($9, a, ":"); print a[n]}' | tr '\n' ' ')"
        done
        if (( $(now_ms) - start >= 60000 )); then
            timed_out=1
            kill -TERM -- "-$pid" 2>/dev/null
            sleep 5
            kill -KILL -- "-$pid" 2>/dev/null
            break
        fi
        sleep 0.2
    done
    wait "$pid"
    status=$?
    end="$(now_ms)"
    ports="$(tr ' ' '\n' <<< "$ports" | grep -v '^$' | sort -nu | tr '\n' ',' | sed 's/,$//')"
    local argv="${*//$password/<password>}"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$argv" "$(basename "$stdin")" \
        "$status" "$((end - start))" "$(wc -c < "$S/$id.out" | tr -d ' ')" \
        "$(wc -c < "$S/$id.err" | tr -d ' ')" "$timed_out" "${ports:-none}" >> "$S/results.tsv"
    if grep -q 'Operation not permitted' "$S/$id.out" "$S/$id.err"; then
        echo "cases: $id tried a denied port" >&2
        "$here/core.sh" stop "$S"
        exit 3
    fi
    sleep 1
    if pgrep -g "$pid" >/dev/null || listening 9101; then
        echo "cases: $id left a process or a 9101 listener" >&2
        "$here/core.sh" stop "$S"
        exit 4
    fi
}

run_case C1 /dev/null false help
run_case C2 /dev/null false whoami
run_case C3 /dev/null false login --username Admin --password "$password"
run_case C4 /dev/null false login --username Admin --password wrong
run_case C5 /dev/null false no-such-command
run_case C6 /dev/null false add-topic --name proof-room
run_case C7 /dev/null false "@$S/script.txt"
run_case C8 "$S/password.stdin" false login --username Admin
run_case C9 /dev/null true whoami
run_case C10 /dev/null false show-topics
"$here/core.sh" stop "$S" || exit 1
run_case C11 /dev/null false whoami
