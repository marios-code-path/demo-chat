#!/usr/bin/env bash
# Stage 1, task 10: the acceptance cases A1 to A13 of the shell command tool.
# Every case is one chat-shell-cli process in its own process group, inside the
# stage 0 sandbox, with a 60-second limit. The disposable memory core runs on
# 6990 and 6991. Exit 0: every case has its expected status. Exit 1: a case
# differs. Exit 3: a case hit a denied port. Exit 4: the end state is not clean.
#
#   cases.sh <scratch-dir>
#
# A11 needs resident mode (task 9). The script marks it "not built". The native
# image does not start (task 7), so N2, N5, and N10 are marked "not run".
set -uo pipefail
S="${1:?usage: cases.sh <scratch-dir>}"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
stage0="$root/docs/superpowers/proofs/2026-10-17-one-shot"
cli="$root/shell-scripts/chat-shell-cli"
now_ms() { perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }
listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# The preconditions: a sandbox profile, a clean port state, a record of docker.
"$stage0/isolation.sb.sh" "$S" || exit 4
docker ps --format '{{.Names}} {{.Image}}' | sort > "$S/docker-before"
for port in 6990 6991 9101; do
    if listening "$port"; then echo "cases: $port is busy" >&2; exit 4; fi
done

# The isolation probe: every denied port must refuse a connect from a JVM.
sandbox-exec -f "$S/isolation.sb" java "$stage0/Probe.java" $(cat "$S/denied-ports") > "$S/probe.out" 2>&1
if grep -q ' connected$' "$S/probe.out"; then
    echo "cases: the isolation probe connected to a denied port" >&2
    exit 3
fi

"$stage0/core.sh" start "$S" || exit 4

password="$(awk '/^      Admin:/ {f=1} f && /password:/ {print $2; exit}' \
    "$root/shared-deploy-configuration/src/main/config/userinit.yml")"
(umask 077; printf '%s\n' "$password" > "$S/admin.password"; printf 'wrong-password\n' > "$S/wrong.password")
printf 'line one\nzwei ünïcode ✓\nline three, no final newline' > "$S/entry.txt"

printf 'id\tcommand\texpected\texit\tverdict\twall_ms\tstdout_bytes\tstderr_bytes\tnote\n' > "$S/results.tsv"
failures=0

# run_case ID EXPECTED STDIN-FILE ENV-ASSIGNMENTS... -- WORDS...
# It leaves the exit status in $status and the verdict in $verdict.
run_case() {
    local id="$1" expected="$2" stdin="$3"
    shift 3
    local env_args=()
    while [[ "$1" != -- ]]; do env_args+=("$1"); shift; done
    shift
    local start end pid timed_out=0
    start="$(now_ms)"
    perl -e 'setpgrp(0,0); exec @ARGV' sandbox-exec -f "$S/isolation.sb" env \
        CHAT_SHELL_KEY_TYPE=uuid CORE_PORT=6990 CORE_MGMT_PORT=6991 "${env_args[@]}" \
        "$cli" "$@" < "$stdin" > "$S/$id.out" 2> "$S/$id.err" &
    pid=$!
    while kill -0 "$pid" 2>/dev/null; do
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
    wall=$((end - start))
    verdict=PASS
    [[ "$status" == "$expected" ]] || verdict=FAIL
    [[ "$timed_out" == 0 ]] || verdict=FAIL
    if grep -q 'Operation not permitted' "$S/$id.out" "$S/$id.err"; then
        echo "cases: $id tried a denied port" >&2
        "$stage0/core.sh" stop "$S"
        exit 3
    fi
    sleep 1
    if pgrep -g "$pid" >/dev/null || listening 9101; then
        echo "cases: $id left a process or a 9101 listener" >&2
        "$stage0/core.sh" stop "$S"
        exit 4
    fi
}

record() { # ID COMMAND EXPECTED NOTE
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$status" "$verdict" "$wall" \
        "$(wc -c < "$S/$1.out" | tr -d ' ')" "$(wc -c < "$S/$1.err" | tr -d ' ')" "${4:-}" >> "$S/results.tsv"
    # A blocked or not built row is not a failure. Its note says why.
    [[ "$verdict" != FAIL ]] || failures=$((failures + 1))
}

# A rule for a case: its standard error must be empty.
needs_empty_err() {
    if [[ -s "$S/$1.err" ]]; then verdict=FAIL; fi
}

login=(CHAT_SHELL_USER=Admin "CHAT_SHELL_PASSWORD_FILE=$S/admin.password")

run_case A1 0 /dev/null -- whoami;                                needs_empty_err A1; record A1 whoami 0
run_case A2 0 /dev/null "${login[@]}" -- whoami;                  needs_empty_err A2; record A2 'whoami (login)' 0
run_case A3 77 /dev/null CHAT_SHELL_USER=Admin "CHAT_SHELL_PASSWORD_FILE=$S/wrong.password" -- whoami
record A3 'whoami (wrong password)' 77
run_case A4 2 /dev/null CHAT_SHELL_USER=Admin -- whoami;          record A4 'whoami (user alone)' 2
run_case A5 2 /dev/null -- no-such-command;                       record A5 no-such-command 2
run_case A6 2 /dev/null -- @x;                                    record A6 @x 2
run_case A7 2 /dev/null -- listen lobby;                          record A7 'listen lobby' 2
run_case A13 2 /dev/null -- '!' false;                            record A13 '! false' 2

run_case A8 0 /dev/null "${login[@]}" -- add-topic stage1-room
blocked=false
if [[ "$status" != 0 ]] && grep -q 'A key id cannot be a byte\[\]' "$S/A8.err"; then
    blocked=true
    verdict=BLOCKED
    record A8 'add-topic stage1-room' 0 'blocked by CHAT-siaetqtr'
else
    record A8 'add-topic stage1-room' 0
fi

if $blocked; then
    for id in A9 A10; do
        status=-; verdict=BLOCKED; wall=-
        : > "$S/$id.out"; : > "$S/$id.err"
        record "$id" "$([[ $id == A9 ]] && echo 'post stage1-room' || echo 'messages stage1-room --format records')" 0 'blocked by CHAT-siaetqtr'
    done
else
    run_case A9 0 "$S/entry.txt" "${login[@]}" -- post stage1-room;  record A9 'post stage1-room' 0
    run_case A10 0 /dev/null "${login[@]}" -- messages stage1-room --format records
    # The text of the record must equal the entry byte for byte.
    if [[ "$(jq -sj '.[-1].text' "$S/A10.out" 2>/dev/null)" != "$(cat "$S/entry.txt")" ]]; then verdict=FAIL; fi
    record A10 'messages stage1-room --format records' 0 "topic read ${wall} ms"
fi
status=-; verdict='NOT BUILT'; wall=-; : > "$S/A11.out"; : > "$S/A11.err"
record A11 'resident messages' 0 'task 9 is not built'

"$stage0/core.sh" stop "$S" || exit 4
run_case A12 69 /dev/null -- whoami;                              record A12 'whoami (core stopped)' 69

# The end state: no listener, an empty core group, and the same containers.
state=ok
for port in 6990 6991 9101; do listening "$port" && state="listener on $port"; done
pgrep -g "$(cat "$S/core.pgid")" >/dev/null 2>&1 && state="core group alive"
docker ps --format '{{.Names}} {{.Image}}' | sort > "$S/docker-after"
cmp -s "$S/docker-before" "$S/docker-after" || state="docker ps changed"
echo "end state: $state" | tee -a "$S/results.tsv"

column -t -s $'\t' "$S/results.tsv"
[[ "$state" == ok ]] || exit 4
(( failures == 0 )) || exit 1
exit 0
