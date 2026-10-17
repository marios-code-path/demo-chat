#!/usr/bin/env bash
# Checks the flags of `just launch-shell` through `just dry-run-shell`, which
# calls launch-shell with --dry-run. No case starts the shell. See
# CHAT-msanybaw.
set -uo pipefail

cd "$(dirname "$0")/.."

failures=0

# expect_plan NAME PATTERN... -- ARGS...
# The dry-run plan must exit 0 and hold every pattern.
expect_plan() {
    local name="$1"
    shift
    local patterns=()
    while [[ "$1" != -- ]]; do
        patterns+=("$1")
        shift
    done
    shift
    local output
    if ! output="$(just dry-run-shell "$@" 2>&1)"; then
        echo "FAIL $name: the recipe exited non-zero"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    local pattern
    for pattern in "${patterns[@]}"; do
        if ! grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the plan does not hold $pattern"
            failures=$((failures + 1))
            return
        fi
    done
    echo "ok   $name"
}

# expect_refusal NAME MESSAGE -- ARGS...
# The recipe must exit non-zero and print the message.
expect_refusal() {
    local name="$1" message="$2"
    shift 3
    local output status
    output="$(just dry-run-shell "$@" 2>&1)"
    status=$?
    if [[ $status -eq 0 ]] || ! grep -qF -- "$message" <<< "$output"; then
        echo "FAIL $name: expected a refusal with '$message', got exit $status"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    echo "ok   $name"
}

expect_plan 'the default stays long and node 1' \
    -Dapp.key.type=long -Dapp.nodeid=1 --
expect_plan 'a node id alone keeps long' \
    -Dapp.key.type=long -Dapp.nodeid=3 -- 3
expect_plan '--key-type uuid matches a uuid core' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- 1 --key-type uuid
expect_plan '--key-type=uuid form' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- 1 --key-type=uuid
expect_plan '--key-type keeps the default node id' \
    -Dapp.key.type=uuid -Dapp.nodeid=1 -- --key-type uuid
expect_plan 'node_id= form' \
    -Dapp.key.type=uuid -Dapp.nodeid=4 -- node_id=4 --key-type uuid
expect_refusal 'chat-build refuses an unknown key type' "invalid choice: 'bogus'" \
    -- 1 --key-type bogus

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all shell recipe cases passed'
