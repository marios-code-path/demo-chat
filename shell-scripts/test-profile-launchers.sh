#!/usr/bin/env bash
# Inspect profile-driven JVM arguments without starting Java or Maven.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
work=$(cd "$work" && pwd -P)
trap 'rm -rf "$work"' EXIT
checks=0
failures=0
mkdir -p "$work/bin" "$work/lib"
cat > "$work/bin/java" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "$CAPTURE"
printf '%s\n%s\n' "${CORE_PORT:-}" "${CORE_MGMT_PORT:-}" > "$CAPTURE.env"
EOF
chmod +x "$work/bin/java"
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
for selected in alpha beta; do
    case "$selected" in alpha) node=7; port=15890; mgmt=15891 ;; beta) node=1001; port=16890; mgmt=16891 ;; esac
    cat > "$work/lib/shell-cli.options" <<EOF
# key-type uuid
# node-id $node
# core-port $port
# core-mgmt-port $mgmt
-Dapp.nodeid=$node
-Dapp.rootkeys.consume.source=http://127.0.0.1:$mgmt
EOF
    status=0
    env -u CORE_PORT -u CORE_MGMT_PORT -u CHAT_SHELL_NODE_ID -u CHAT_SHELL_KEY_TYPE \
        CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/$selected.env" \
        CHAT_SHELL_LIB="$work/lib" CHAT_SHELL_JAVA="$work/bin/java" CAPTURE="$work/args" \
        bash "$repo/shell-scripts/chat-shell-cli" whoami > "$work/out" 2> "$work/error" || status=$?
    label="$selected installed launch succeeds"
    check test "$status" -eq 0
    label="$selected exports the effective core port"
    check grep -Fxq "$port" "$work/args.env"
    status=0
    CORE_PORT=$((port + 100)) CORE_MGMT_PORT=$((mgmt + 100)) \
        CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/$selected.env" \
        CHAT_SHELL_LIB="$work/lib" CHAT_SHELL_JAVA="$work/bin/java" CAPTURE="$work/args" \
        bash "$repo/shell-scripts/chat-shell-cli" whoami > "$work/out" 2> "$work/error" || status=$?
    label="$selected uses the tunnel management port in JVM properties"
    check grep -Fxq -- "-Dapp.rootkeys.consume.source=http://127.0.0.1:$((mgmt + 100))" "$work/args"
done

status=0
CHAT_MCP_LIB="$work/absent" CHAT_MCP_JAVA="$work/bin/java" CAPTURE="$work/mcp" \
    bash "$repo/shell-scripts/chat-mcp-stdio" --config "$work/config" > "$work/out" 2> "$work/error" || status=$?
label='an explicit absent MCP runtime is named in the refusal'
check grep -Fq "$work/absent" "$work/error"
label='an absent MCP runtime starts no Java'
check test ! -e "$work/mcp"
mkdir -p "$work/mcp"
: > "$work/mcp/adapter.jar"
status=0
CHAT_MCP_LIB="$work/mcp" CHAT_MCP_JAVA="$work/bin/java" CAPTURE="$work/mcp-args" \
    bash "$repo/shell-scripts/chat-mcp-stdio" --config "$work/config" > "$work/out" 2> "$work/error" || status=$?
label='explicit MCP jars work without an installation marker'
check test "$status" -eq 0
label='explicit MCP runtime selects its own classpath'
check grep -Fxq "$work/mcp/adapter.jar" "$work/mcp-args"

status=0
CHAT_NODE_PROFILE="$work/absent-profile" CHAT_SHELL_LIB="$work/lib" \
    CHAT_SHELL_JAVA="$work/bin/java" CAPTURE="$work/unexpected-java" \
    bash "$repo/shell-scripts/chat-shell-cli" whoami > "$work/out" 2> "$work/error" || status=$?
label='invalid selected profiles fail before Java'
check test "$status" -eq 2
label='invalid selected profiles execute no Java'
check test ! -e "$work/unexpected-java"

status=0
CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
    "$repo/shell-scripts/chat-build" shell --notls --dry-run > "$work/build" 2> "$work/error" || status=$?
label='the builder obtains explicit identity from the selected profile'
check test "$status" -eq 0
label='the builder emits the profile node identity'
check grep -Fq -- '-Dapp.nodeid=7' "$work/build"
printf '# java %s\n' "$work/absent-build-java" >> "$work/lib/shell-cli.options"
status=0
env -u CHAT_SHELL_JAVA PATH="$work/bin:$PATH" CHAT_SHELL_LIB="$work/lib" CAPTURE="$work/portable" \
    bash "$repo/shell-scripts/chat-shell-cli" whoami > "$work/out" 2> "$work/error" || status=$?
label='a build-host Java path does not prevent portable execution'
check test "$status" -eq 0
status=0
CHAT_SHELL_LIB="$work/absent-shell-runtime" CHAT_SHELL_KEY_TYPE=uuid \
    CHAT_SHELL_JAVA="$work/bin/java" CAPTURE="$work/unexpected-shell" \
    bash "$repo/shell-scripts/chat-shell-cli" whoami > "$work/out" 2> "$work/error" || status=$?
label='an explicit absent shell runtime refuses source fallback'
check test "$status" -eq 2
label='the shell refusal names the selected runtime'
check grep -Fq "$work/absent-shell-runtime" "$work/error"
cp "$repo/shell-scripts/fixtures/profiles/alpha.env" "$work/unicode.env"
printf 'CHAT_EMBEDDING_OPENAI_MODEL=literal\342\200\250NODE_ID=8\n' >> "$work/unicode.env"
status=0
CHAT_NODE_PROFILE="$work/unicode.env" "$repo/shell-scripts/chat-build" shell --notls --dry-run \
    > "$work/build" 2> "$work/error" || status=$?
label='Unicode text cannot introduce a different profile record'
check grep -Fq -- '-Dapp.nodeid=7' "$work/build"

printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
