#!/usr/bin/env bash
# Check literal profile loading without a service or build.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0
checks=0
tool="$repo/shell-scripts/node-profile"

check() {
    checks=$((checks + 1))
    if "$@"; then
        printf 'ok %s\n' "$label"
    else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
run() {
    status=0
    env -u NODE_PROJECT -u NODE_ID -u NODE_KEY_TYPE \
        CHAT_NODE_PROFILE="$profile" "$@" bash "$tool" \
        --require NODE_PROJECT --require NODE_ID > "$work/out" 2> "$work/error" || status=$?
}
refused() { test "$status" -eq 2 && test ! -s "$work/out"; }

profile="$repo/shell-scripts/fixtures/profiles/alpha.env"
run env
label='alpha profile supplies records'
check test "$status" -eq 0
label='alpha project is literal'
check grep -Fxq 'NODE_PROJECT=chat-alpha' "$work/out"
profile="$repo/shell-scripts/fixtures/profiles/beta.env"
run env
label='second profile supplies a different node'
check grep -Fxq 'NODE_ID=1001' "$work/out"

cp "$repo/shell-scripts/fixtures/profiles/alpha.env" "$work/profile"
profile="$work/profile"
printf 'CHAT_EMBEDDING_OPENAI_MODEL=$(touch %s/executed)\n' "$work" >> "$profile"
run env
label='substitution text remains literal'
check test "$status" -eq 0
label='profile executes no command'
check test ! -e "$work/executed"
label='substitution text survives emission'
check grep -Fxq "CHAT_EMBEDDING_OPENAI_MODEL=\$(touch $work/executed)" "$work/out"

run env NODE_PROJECT=override
label='explicit neutral environment wins'
check grep -Fxq 'NODE_PROJECT=override' "$work/out"
run env NODE_PROJECT=''
label='explicit empty required input fails'
check refused

for mutation in duplicate unknown version assignment empty node key_type port collision control nul; do
    cp "$repo/shell-scripts/fixtures/profiles/alpha.env" "$profile"
    case "$mutation" in
        duplicate) printf 'NODE_ID=8\n' >> "$profile" ;;
        unknown) printf 'PASSWORD=secret\n' >> "$profile" ;;
        version) sed 's/NODE_PROFILE_VERSION=1/NODE_PROFILE_VERSION=2/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        assignment) printf 'export NODE_ID=8\n' >> "$profile" ;;
        empty) sed 's/NODE_PROJECT=chat-alpha/NODE_PROJECT=/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        node) sed 's/NODE_ID=7/NODE_ID=1024/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        key_type) sed 's/NODE_KEY_TYPE=uuid/NODE_KEY_TYPE=other/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        port) sed 's/NODE_MGMT_PORT=15891/NODE_MGMT_PORT=65536/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        collision) sed 's/NODE_MGMT_PORT=15891/NODE_MGMT_PORT=15890/' "$profile" > "$work/new"; mv "$work/new" "$profile" ;;
        control) printf 'CHAT_EMBEDDING_IDENTITY=bad\rvalue\n' >> "$profile" ;;
        nul) printf 'CHAT_EMBEDDING_IDENTITY=bad\000value\n' >> "$profile" ;;
    esac
    run env
    label="$mutation profile fails without partial records"
    check refused
done
profile="$work/missing"
run env
label='missing explicit path fails'
check refused
profile='relative.env'
run env
label='relative path fails'
check refused
profile=''
run env
label='empty path fails'
check refused
profile="$work/profile"
printf '# Comment\n\nNODE_PROFILE_VERSION=1\nNODE_PROJECT=literal\nNODE_ID=0' > "$profile"
run env
label='comments and final record without LF work'
check test "$status" -eq 0

status=0
CHAT_NODE_PROFILE="$profile" bash "$tool" --require NO_SUCH_KEY > "$work/out" 2> "$work/error" || status=$?
label='unknown required key fails'
check refused
status=0
CHAT_NODE_PROFILE="$profile" bash "$tool" --require NODE_IMAGE_REPO > "$work/out" 2> "$work/error" || status=$?
label='missing operation key fails'
check refused

profile="$repo/shell-scripts/fixtures/profiles/alpha.env"
status=0
CHAT_NODE_PROFILE="$profile" bash -c \
    '. "$1"; chat_node_profile_load NODE_ID; test "$NODE_ID" = 7' \
    _ "$repo/shell-scripts/node-profile.sh" > "$work/out" 2> "$work/error" || status=$?
label='library exports validated values'
check test "$status" -eq 0
profile="$repo/shell-scripts/fixtures/profiles/alpha.env"
run env NODE_PROJECT=$'valid\n'
label='an environment value keeps its trailing newline for validation'
check refused
profile="$work/no-version"
printf 'NODE_PROJECT=neutral\nNODE_ID=7\n' > "$profile"
run env
label='a neutral profile cannot omit its version'
check refused
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
