#!/bin/bash
# Check partial build inputs and the Maven process boundary.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
SCRIPT="$DIR/build-partial.sh"

if [[ ! -f "$SCRIPT" ]]; then
    echo 'FAIL: The partial build script does not exist.' >&2
    exit 1
fi

TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
ln -s "$DIR/fixtures/partial-build/mvn" "$TEMP_DIR/mvn"
export PATH="$TEMP_DIR:$PATH"

require_line() {
    if ! printf '%s\n' "$OUTPUT" | grep -Fxq -- "$1"; then
        printf 'FAIL: Expected output line: %s\n%s\n' "$1" "$OUTPUT" >&2
        exit 1
    fi
}

reject() {
    local result=0
    OUTPUT="$(bash "$SCRIPT" "$@" 2>&1)" || result=$?
    if [[ "$result" != 2 || "$OUTPUT" == *'argument: '* ]]; then
        printf 'FAIL: Invalid input reached Maven or returned status %s.\n%s\n' "$result" "$OUTPUT" >&2
        exit 1
    fi
}

OUTPUT="$(cd "$TEMP_DIR" && bash "$SCRIPT" --modules chat-security,chat-mcp)"
require_line "directory: $ROOT"
require_line 'argument: chat-security,chat-mcp'
require_line 'argument: -am'
require_line 'argument: clean'
require_line 'argument: verify'
if [[ "$OUTPUT" == *'argument: -P'* ]]; then
    echo 'FAIL: Unit mode enabled a Maven profile.' >&2
    exit 1
fi

OUTPUT="$(bash "$SCRIPT" --modules chat-shell --mode integration)"
require_line 'argument: chat-shell,chat-deploy-memory-integration-test'
require_line 'argument: -Ptest-build,integration'

OUTPUT="$(bash "$SCRIPT" --modules chat-security --mode integration)"
require_line 'argument: -Pintegration'
if [[ "$OUTPUT" == *'chat-deploy-memory-integration-test'* ]]; then
    echo 'FAIL: A non-shell selection included the test image.' >&2
    exit 1
fi

OUTPUT="$(bash "$SCRIPT" --modules chat-shell,chat-deploy-memory-integration-test,chat-shell --mode integration)"
require_line 'argument: chat-shell,chat-deploy-memory-integration-test'

OUTPUT="$(bash "$SCRIPT" --modules chat-deploy-memory-integration-test --mode integration)"
require_line 'argument: -Ptest-build,integration'

OUTPUT="$(bash "$SCRIPT" --modules chat-shell)"
require_line 'argument: chat-shell'
if [[ "$OUTPUT" == *'argument: -P'* || "$OUTPUT" == *'chat-deploy-memory-integration-test'* ]]; then
    echo 'FAIL: Unit mode included the shell test image.' >&2
    exit 1
fi

OUTPUT="$(bash "$SCRIPT" --modules chat-mcp --dry-run)"
if [[ "$OUTPUT" != *'mvn '* || "$OUTPUT" == *'argument: '* ]]; then
    echo 'FAIL: The dry run started Maven or omitted the command.' >&2
    exit 1
fi

reject
reject --modules
reject --modules ''
reject --modules chat-missing
reject --modules ../chat-mcp
reject --modules=-DskipTests
reject --modules 'chat-mcp,-DskipTests'
reject --modules 'chat-mcp, chat-security'
reject --modules 'chat-mcp,'
reject --modules ',chat-mcp'
reject --modules 'chat-mcp,,chat-security'
reject --modules 'chat-mcp;false'
reject --modules '$(false)'
reject --modules chat-mcp --mode unknown
reject --modules chat-mcp --mode
reject --modules chat-mcp --unknown

result=0
OUTPUT="$(PARTIAL_TEST_EXIT=37 bash "$SCRIPT" --modules chat-mcp 2>&1)" || result=$?
if [[ "$result" != 37 ]]; then
    printf 'FAIL: Maven returned 37, but the script returned %s.\n' "$result" >&2
    exit 1
fi

echo 'PASS: Partial build validation, selection, preview, working directory, and exit status.'
