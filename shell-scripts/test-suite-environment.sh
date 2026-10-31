#!/usr/bin/env bash
# Check the test boundary against inherited legacy inputs.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
helper="$repo/shell-scripts/test-support/clear-legacy-inputs.sh"
reader="$repo/shell-scripts/legacy-node-inputs.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
pairs=$(bash -c '. "$1"; chat_legacy_pairs' _ "$reader") || exit $?
poison=(NODE9_DIR=ambient-root STUB_REQUIRE_NODE9_ENV=ambient-control UNRELATED_TEST_INPUT=keep)
while read -r legacy neutral; do
    poison+=("$legacy=ambient" "$neutral=fixture")
done <<< "$pairs"
status=0
env -i PATH="$PATH" "${poison[@]}" bash -c '
    . "$1" || exit $?
    . "$2" || exit $?
    chat_legacy_node_inputs || exit $?
    env
' _ "$helper" "$reader" > "$work/out" 2> "$work/error" || status=$?
label='the cleared test boundary accepts neutral fixtures'
check test "$status" -eq 0
while read -r legacy neutral; do
    label="the boundary removes inherited $legacy"
    check bash -c '! grep -q "^$1=" "$2"' _ "$legacy" "$work/out"
    label="the boundary preserves explicit $neutral"
    check grep -Fxq "$neutral=fixture" "$work/out"
done <<< "$pairs"
label='the boundary removes the derived-root override'
check bash -c '! grep -q "^NODE9_DIR=" "$1"' _ "$work/out"
label='the boundary removes the legacy test control'
check bash -c '! grep -q "^STUB_REQUIRE_NODE9_ENV=" "$1"' _ "$work/out"
label='the boundary preserves unrelated inputs'
check grep -Fxq UNRELATED_TEST_INPUT=keep "$work/out"
status=0
env -i PATH="$PATH" NODE9_PROJECT=ambient bash -c '
    . "$1" || exit $?
    . "$2" || exit $?
    export NODE9_PROJECT=deliberate NODE_PROJECT=fixture
    chat_legacy_node_inputs
' _ "$helper" "$reader" > "$work/out" 2> "$work/error" || status=$?
label='a deliberate post-boundary conflict still returns status 2'
check test "$status" -eq 2
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
