#!/usr/bin/env bash
# Resolve MCP tools from explicit neutral inputs or their own source directory.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/home/bin" "$work/custom"
for file in register chat-mcp-stdio; do
    printf '#!/usr/bin/env bash\nexit 0\n' > "$work/home/bin/$file"
    cp "$work/home/bin/$file" "$work/custom/$file"
done
chmod +x "$work/home/bin"/* "$work/custom"/*
checks=0
failures=0
resolve() {
    status=0
    env -i PATH="$PATH" "$@" bash -c \
        '. "$1"; mcp_install_resolve "$2" || exit $?; printf "%s\n%s\n" "$MCP_REGISTER" "$MCP_LAUNCHER"' \
        _ "$repo/shell-scripts/mcp-install-lib.sh" "$repo" > "$work/out" 2> "$work/error" || status=$?
}
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
resolve CHAT_DEPLOYMENT_HOME="$work/home"
label='an explicit neutral home selects its register'
check grep -Fxq "$work/home/bin/register" "$work/out"
label='an explicit neutral home selects its adapter'
check grep -Fxq "$work/home/bin/chat-mcp-stdio" "$work/out"
resolve CHAT_DEPLOYMENT_HOME="$work/absent"
label='an invalid explicit home refuses fallback'
check test "$status" -eq 2
resolve CHAT_REGISTER_COMMAND="$work/custom/register" CHAT_MCP_STDIO="$work/custom/chat-mcp-stdio"
label='explicit general commands select the register'
check grep -Fxq "$work/custom/register" "$work/out"
label='explicit general commands select the adapter'
check grep -Fxq "$work/custom/chat-mcp-stdio" "$work/out"
resolve
label='no deployment input uses the general source register'
check grep -Fxq "$repo/shell-scripts/register" "$work/out"
label='no deployment input uses the general source adapter'
check grep -Fxq "$repo/shell-scripts/chat-mcp-stdio" "$work/out"
resolve NODE9_HOME="$work/home"
label='the legacy home remains accepted during transition'
check grep -Fxq "$work/home/bin/register" "$work/out"
resolve NODE9_HOME="$work/home" CHAT_DEPLOYMENT_HOME="$work/custom"
label='unequal home aliases fail before client registration'
check test "$status" -eq 2
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
