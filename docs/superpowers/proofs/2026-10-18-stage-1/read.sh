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
#          then reads every topic back and checks the record counts. Only after
#          that check does it write the marker <scratch-dir>/seeded. It needs
#          add-topic. While CHAT-siaetqtr is open, add-topic fails, seed ends
#          with status 3, and measure runs the exploratory stand-in.
# measure  Runs the 4 reads 5 times, and prints each total in milliseconds and
#          the median. A representative measure needs the marker. It checks
#          every response: exit status 0, empty standard error, and the expected
#          number of records of the expected topic. Any failed read ends the
#          measure with status 1 and prints no median. The stand-in is 4
#          show-topics reads with no data. It leaves out message loading,
#          filtering, JSON output, and realistic response sizes, so its numbers
#          decide nothing.
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

# The 4 reads of a complete register read: NAME|OPTIONS|EXPECTED-RECORDS.
# The index read takes every record. A topic read takes the newest one.
reads=("register-index||20")
for name in "${topics[@]}"; do reads+=("$name|--limit 1|1"); done

# check_records FILE ERR-FILE NAME COUNT  Exit 0 only if the response is right.
check_records() {
    [[ -s "$2" ]] && return 1
    jq -e -s --arg topic "$3" --argjson count "$4" \
        'length == $count and all(.[]; .topic == $topic and (.text | length) > 0)' "$1" > /dev/null 2>&1
}

case "$action" in
seed)
    rm -f "$S/seeded"
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
    # Read every topic back with no limit. The marker proves the writes are visible.
    one_shot cli messages register-index --format records < /dev/null > "$S/seed-check-index.out" 2> "$S/seed-check-index.err" \
        && check_records "$S/seed-check-index.out" "$S/seed-check-index.err" register-index 20 \
        || { echo "seed: register-index does not hold 20 records, see $S/seed-check-index.out" >&2; exit 1; }
    for name in "${topics[@]}"; do
        one_shot cli messages "$name" --format records < /dev/null > "$S/seed-check-$name.out" 2> "$S/seed-check-$name.err" \
            && check_records "$S/seed-check-$name.out" "$S/seed-check-$name.err" "$name" 5 \
            || { echo "seed: $name does not hold 5 records, see $S/seed-check-$name.out" >&2; exit 1; }
    done
    date -u +%Y-%m-%dT%H:%M:%SZ > "$S/seeded"
    echo "seed: done"
    ;;
measure)
    launcher="${3:?usage: read.sh measure <scratch-dir> cli|native}"
    if [[ -f "$S/seeded" ]]; then kind=representative; else kind=exploratory; fi
    echo "kind: $kind"
    totals=()
    failed=0
    for run in 1 2 3 4 5; do
        start="$(now_ms)"
        if [[ "$kind" == representative ]]; then
            n=0
            for spec in "${reads[@]}"; do
                IFS='|' read -r name options count <<< "$spec"
                n=$((n + 1))
                # shellcheck disable=SC2086
                one_shot "$launcher" messages "$name" --format records $options < /dev/null \
                    > "$S/read-$run-$n.out" 2> "$S/read-$run-$n.err" || { echo "read: run $run, $name exited non-zero" >&2; failed=1; }
            done
        else
            for n in 1 2 3 4; do
                one_shot "$launcher" show-topics < /dev/null > "$S/read-$run-$n.out" 2> "$S/read-$run-$n.err" \
                    || { echo "read: run $run, show-topics $n exited non-zero" >&2; failed=1; }
                [[ -s "$S/read-$run-$n.err" ]] && { echo "read: run $run, show-topics $n wrote standard error" >&2; failed=1; }
            done
        fi
        total=$(( $(now_ms) - start ))
        totals+=("$total")
        echo "run $run: $total ms"
        if [[ "$kind" == representative ]]; then
            n=0
            for spec in "${reads[@]}"; do
                IFS='|' read -r name options count <<< "$spec"
                n=$((n + 1))
                check_records "$S/read-$run-$n.out" "$S/read-$run-$n.err" "$name" "$count" \
                    || { echo "read: run $run, $name did not answer $count record(s) of that topic with empty standard error" >&2; failed=1; }
            done
        fi
    done
    if (( failed != 0 )); then
        echo "measure: FAILED. The times above decide nothing." >&2
        exit 1
    fi
    median="$(printf '%s\n' "${totals[@]}" | sort -n | sed -n 3p)"
    echo "median: $median ms"
    ;;
*) echo "usage: read.sh seed|measure <scratch-dir> [cli|native]" >&2; exit 2 ;;
esac
