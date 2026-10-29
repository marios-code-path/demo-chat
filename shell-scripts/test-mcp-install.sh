#!/usr/bin/env bash
# The MCP installers must work from a checkout, and they must point the client
# at the config that `register config` writes. A fake installed home and a fake
# client keep the test off the real configuration.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin" "$fixture/home/bin"
: > "$fixture/home/.installed"

cat > "$fixture/home/bin/register" <<EOF
#!/usr/bin/env bash
[[ "\$1" == config ]] || { printf 'register: unexpected %s\n' "\$1" >&2; exit 2; }
printf '%s\n' "$fixture/config.properties"
EOF

cat > "$fixture/bin/claude" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$fixture/claude.log"
EOF

cat > "$fixture/bin/codex" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "$fixture/codex.log"
EOF

: > "$fixture/home/bin/chat-mcp-stdio"
chmod +x "$fixture/home/bin/register" "$fixture/home/bin/chat-mcp-stdio" \
    "$fixture/bin/claude" "$fixture/bin/codex"

PATH="$fixture/bin:$PATH" NODE9_HOME="$fixture/home" \
    "$repo_root/shell-scripts/claude-mcp-install.sh" theta demo-chat demo-chat-register /bin/echo local

PATH="$fixture/bin:$PATH" NODE9_HOME="$fixture/home" \
    "$repo_root/shell-scripts/codex-mcp-install.sh" theta demo-chat demo-chat-register /bin/echo

claude_line="$(cat "$fixture/claude.log")"
[[ "$claude_line" == *demo-chat-register-theta* ]]
[[ "$claude_line" == *"--scope local"* ]]
[[ "$claude_line" == *"$fixture/config.properties"* ]]
[[ "$claude_line" == *"$fixture/home/bin/chat-mcp-stdio"* ]]
[[ "$claude_line" == *CHAT_MCP_JAVA=/bin/echo* ]]

codex_line="$(cat "$fixture/codex.log")"
[[ "$codex_line" == *"mcp add demo-chat-register"* ]]
[[ "$codex_line" == *"$fixture/config.properties"* ]]
[[ "$codex_line" == *"$fixture/home/bin/chat-mcp-stdio"* ]]

if PATH="$fixture/bin:$PATH" NODE9_HOME="$fixture/home" \
    "$repo_root/shell-scripts/claude-mcp-install.sh" theta demo-chat demo-chat-register /bin/echo sideways \
    > /dev/null 2>&1; then
    printf 'the installer accepted a bad scope\n' >&2
    exit 1
fi

printf 'MCP installers passed\n'
