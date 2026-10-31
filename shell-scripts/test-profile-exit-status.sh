#!/usr/bin/env bash
# Preserve existing refusal statuses and isolate new profile failures.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
builder="${CHAT_TEST_BUILD:-$repo/shell-scripts/chat-build}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
expect() {
    local expected="$1" status=0
    shift
    env -u CHAT_NODE_PROFILE CONSUL_HOST=127.0.0.1 "$builder" "$@" \
        > "$work/out" 2> "$work/error" || status=$?
    checks=$((checks + 1))
    if [[ "$status" == "$expected" ]]; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s: expected %s, received %s\n' "$label" "$expected" "$status"
        failures=$((failures + 1))
    fi
}
label='index without Cassandra retains status 1'
expect 1 core --memory --index lucene --node-id 0 --key-type uuid --notls --dry-run
label='vector pairing retains status 2'
expect 2 core --memory --vector embedded --node-id 0 --key-type uuid --notls --dry-run
label='JWK bake refusal retains status 1'
expect 1 authserv --build --jwk "$work/key" --node-id 0 --key-type uuid --notls --dry-run
label='agent bake refusal retains status 1'
expect 1 authserv --build --agent a=Agent --node-id 0 --key-type uuid --notls --dry-run
label='index-root bake refusal retains status 1'
expect 1 core --memory --build --index-root "$work/index" --node-id 0 --key-type uuid --notls --dry-run
label='JWKS parser refusal retains status 1'
expect 1 authserv --agent-json --agent a=Agent --agent-jwks-uri a=http://example.invalid/jwks \
    --node-id 0 --key-type uuid --notls
status=0
CHAT_NODE_PROFILE="$work/absent" "$builder" core --memory --node-id 0 --key-type uuid \
    --notls --dry-run > "$work/out" 2> "$work/error" || status=$?
checks=$((checks + 1))
if [[ "$status" == 2 && ! -s "$work/out" ]]; then
    printf 'ok profile failure returns 2 before builder output\n'
else
    printf 'FAIL profile failure: expected status 2 without output, received %s\n' "$status"
    failures=$((failures + 1))
fi
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
