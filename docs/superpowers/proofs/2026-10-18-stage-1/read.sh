#!/usr/bin/env bash
# Stage 1, task 8: the time of a complete register read for each one-shot
# launcher. A complete read is 1 index read and 3 topic reads, so 4 one-shot
# processes in a row. Every run goes inside the stage 0 sandbox, against the
# disposable core on 6990 and 6991.
#
#   read.sh seed <scratch-dir>
#   read.sh measure <scratch-dir> cli|native
#
# seed     Creates the representative data: 4 topics, 20 pointer lines in
#          register-index, and 5 entries of about 2 KB in each of 3 topics. It
#          needs add-topic. While CHAT-siaetqtr is open, add-topic fails, seed
#          ends with status 3, and measure runs the exploratory stand-in.
# measure  Runs the 4 reads 5 times, and prints each total in milliseconds and
#          the median. The stand-in is 4 show-topics reads with no data. It
#          leaves out message loading, filtering, JSON output, and realistic
#          response sizes, so its numbers decide nothing.
set -uo pipefail
action="${1:?usage: read.sh seed|measure <scratch-dir> [cli|native]}"
S="${2:?usage: read.sh seed|measure <scratch-dir> [cli|native]}"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
now_ms() { perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }

# The Admin password of the disposable core, from the file that the core loads.
password_file="$S/admin.password"
if [[ ! -s "$password_file" ]]; then
    (umask 077; awk '/^      Admin:/ {f=1} f && /password:/ {print $2; exit}' \
        "$root/shared-deploy-configuration/src/main/config/userinit.yml" > "$password_file")
fi

# one_shot LAUNCHER WORDS...  The standard input is the command input.
one_shot() {
    local launcher="$1"
    shift
    case "$launcher" in
    cli)
        sandbox-exec -f "$S/isolation.sb" env CHAT_SHELL_KEY_TYPE=uuid CORE_PORT=6990 CORE_MGMT_PORT=6991 \
            CHAT_SHELL_USER=Admin CHAT_SHELL_PASSWORD_FILE="$password_file" \
            "$root/shell-scripts/chat-shell-cli" "$@"
        ;;
    native)
        CHAT_SHELL_USER=Admin CHAT_SHELL_PASSWORD_FILE="$password_file" "$here/native.sh" run "$S" "$@"
        ;;
    esac
}

topics=(register-topic-1 register-topic-2 register-topic-3)

case "$action" in
seed)
    for name in register-index "${topics[@]}"; do
        if ! one_shot cli add-topic "$name" < /dev/null > "$S/seed-$name.out" 2> "$S/seed-$name.err"; then
            echo "seed: add-topic $name failed, see $S/seed-$name.err. Blocked by CHAT-siaetqtr?" >&2
            exit 3
        fi
    done
    for i in $(seq 20); do
        printf 'pointer %s to register-topic-%s' "$i" "$(( (i - 1) % 3 + 1 ))" \
            | one_shot cli post register-index > /dev/null 2>> "$S/seed.err" || { echo "seed: post $i failed" >&2; exit 1; }
    done
    entry="$(perl -e 'print "entry line " . ("x" x 60) . "\n" for 1 .. 32')"
    for name in "${topics[@]}"; do
        for i in $(seq 5); do
            printf '%s\nentry %s\n' "$entry" "$i" \
                | one_shot cli post "$name" > /dev/null 2>> "$S/seed.err" || { echo "seed: post $name $i failed" >&2; exit 1; }
        done
    done
    echo "seed: done"
    ;;
measure)
    launcher="${3:?usage: read.sh measure <scratch-dir> cli|native}"
    if [[ -s "$S/seed-register-index.out" || -f "$S/seeded" ]]; then kind=representative; else kind=exploratory; fi
    echo "kind: $kind"
    totals=()
    for run in 1 2 3 4 5; do
        start="$(now_ms)"
        if [[ "$kind" == representative ]]; then
            one_shot "$launcher" messages register-index --format records < /dev/null > /dev/null 2>> "$S/read-$launcher.err" || echo "read: index failed" >&2
            for name in "${topics[@]}"; do
                one_shot "$launcher" messages "$name" --format records --limit 1 < /dev/null > /dev/null 2>> "$S/read-$launcher.err" || echo "read: $name failed" >&2
            done
        else
            for _ in 1 2 3 4; do
                one_shot "$launcher" show-topics < /dev/null > /dev/null 2>> "$S/read-$launcher.err" || echo "read: show-topics failed" >&2
            done
        fi
        total=$(( $(now_ms) - start ))
        totals+=("$total")
        echo "run $run: $total ms"
    done
    median="$(printf '%s\n' "${totals[@]}" | sort -n | sed -n 3p)"
    echo "median: $median ms"
    ;;
*) echo "usage: read.sh seed|measure <scratch-dir> [cli|native]" >&2; exit 2 ;;
esac
