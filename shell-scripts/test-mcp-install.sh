#!/usr/bin/env bash
# Check explicit tool homes and source tools without real client configuration.
# A fake home and a fake client keep the test off the real configuration.
set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin"

# setup_home DIR  Make an installed home with a register that answers config.
setup_home() {
    mkdir -p "$1/bin"
    : > "$1/.installed"
    cat > "$1/bin/register" <<EOF
#!/usr/bin/env bash
[[ "\$1" == config ]] || { printf 'register: unexpected %s\n' "\$1" >&2; exit 2; }
printf '%s\n' "$fixture/config.properties"
EOF
    : > "$1/bin/chat-mcp-stdio"
    chmod +x "$1/bin/register" "$1/bin/chat-mcp-stdio"
}

cat > "$fixture/bin/claude" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$fixture/claude.log"
EOF

cat > "$fixture/bin/codex" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$fixture/codex.log"
EOF

chmod +x "$fixture/bin/claude" "$fixture/bin/codex"
setup_home "$fixture/home"

run_claude() {
    : > "$fixture/claude.log"
    env PATH="$fixture/bin:$PATH" "$@" \
        "$repo_root/shell-scripts/claude-mcp-install.sh" other-agent other-project demo-register /bin/echo local
    cat "$fixture/claude.log"
}

# 1. The project changes the config and the server name. The home stays one.
line="$(run_claude CHAT_DEPLOYMENT_HOME="$fixture/home")"
[[ "$line" == *demo-register-other-agent* ]]
[[ "$line" == *"--scope local"* ]]
[[ "$line" == *"$fixture/config.properties"* ]]
[[ "$line" == *"$fixture/home/bin/chat-mcp-stdio"* ]]
[[ "$line" == *CHAT_MCP_JAVA=/bin/echo* ]]

# 2. Codex takes the same paths and names the server after the register.
PATH="$fixture/bin:$PATH" CHAT_DEPLOYMENT_HOME="$fixture/home" \
    "$repo_root/shell-scripts/codex-mcp-install.sh" other-agent other-project demo-register /bin/echo
codex_line="$(cat "$fixture/codex.log")"
[[ "$codex_line" == *"mcp add demo-register"* ]]
[[ "$codex_line" == *"$fixture/config.properties"* ]]
[[ "$codex_line" == *"$fixture/home/bin/chat-mcp-stdio"* ]]

# 3. A home with no launcher falls back to the checkout launcher.
mkdir -p "$fixture/nolauncher/bin"
: > "$fixture/nolauncher/.installed"
cp "$fixture/home/bin/register" "$fixture/nolauncher/bin/register"
chmod +x "$fixture/nolauncher/bin/register"
line="$(run_claude CHAT_DEPLOYMENT_HOME="$fixture/nolauncher")"
[[ "$line" == *"$repo_root/shell-scripts/chat-mcp-stdio"* ]]

# No deployment input selects the general source tools.
resolved="$(env -u CHAT_DEPLOYMENT_HOME -u NODE9_HOME bash -c \
    'source "$1"; mcp_install_resolve "$2"; printf "%s|%s" "$MCP_REGISTER" "$MCP_LAUNCHER"' \
    _ "$repo_root/shell-scripts/mcp-install-lib.sh" "$repo_root")"
[[ "$resolved" == "$repo_root/shell-scripts/register|$repo_root/shell-scripts/chat-mcp-stdio" ]]

# 5. The installer runs from another project directory. The repository comes
#    from the script path, and the project comes from the argument.
other_project="$(mktemp -d)"
line="$(cd "$other_project" && run_claude CHAT_DEPLOYMENT_HOME="$fixture/home")"
rm -rf "$other_project"
[[ "$line" == *demo-register-other-agent* ]]
[[ "$line" == *"$fixture/home/bin/chat-mcp-stdio"* ]]

# 6. A bad scope stops before any client call.
if PATH="$fixture/bin:$PATH" CHAT_DEPLOYMENT_HOME="$fixture/home" \
    "$repo_root/shell-scripts/claude-mcp-install.sh" other-agent other-project demo-register /bin/echo sideways \
    > /dev/null 2>&1; then
    printf 'the installer accepted a bad scope\n' >&2
    exit 1
fi

printf 'MCP installers passed\n'
