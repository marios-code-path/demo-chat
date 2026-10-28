#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/bin" "$fixture/home" "$fixture/lib" "$fixture/secrets"
fixture_bin="$(cd "$fixture/bin" && pwd -P)"
cp "$repo_root/node-9/bin/setup-portal" "$fixture/bin/setup-portal"
: > "$fixture/.installed"
help_text="$(CHAT_SHELL_PASSWORD=fixture-secret "$fixture/bin/setup-portal" --help 2>&1)"
[[ "$help_text" == *'CHAT_SHELL_PASSWORD_FILE'* ]]
[[ "$help_text" == *'CHAT_SHELL_PASSWORD'* ]]
[[ "$help_text" == *'CHAT_PORTAL_HOST'* ]]
[[ "$help_text" == *'CHAT_PORTAL_OFFSET'* ]]
[[ "$help_text" == *'NODE_9_BIN'* ]]
[[ "$help_text" == *'CHAT_SHELL_LIB'* ]]
[[ "$help_text" != *'fixture-secret'* ]]

if "$fixture/bin/setup-portal" >/dev/null 2> "$fixture/usage.err"; then
    printf 'setup-portal accepted missing arguments.\n' >&2
    exit 1
fi
grep -Fq 'CHAT_SHELL_PASSWORD_FILE' "$fixture/usage.err"
grep -Fq 'CHAT_PORTAL_HOST' "$fixture/usage.err"
cat > "$fixture/bin/chat-shell-portal-tunnel" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$1" "$NODE_9_BIN" "$CHAT_PORTAL_HOST" "$CHAT_SHELL_USER" \
    "$CHAT_PORTAL_STATE_DIR" "$CHAT_PORTAL_SOCKET_DIR"
EOF
chmod +x "$fixture/bin/chat-shell-portal-tunnel"

result="$(HOME="$fixture/home" "$fixture/bin/setup-portal" Theta demo-chat)"
expected="$(printf '%s\n' start "$fixture_bin" node2.lan Theta \
    "$fixture/home/.local/state/demo-chat/node2-portal-Theta" \
    "/tmp/demo-chat-portal-${UID}-Theta")"
[[ "$result" == "$expected" ]]

result="$(HOME="$fixture/home" "$fixture/bin/setup-portal" Theta demo-chat stop)"
expected="$(printf '%s\n' stop "$fixture_bin" node2.lan Theta \
    "$fixture/home/.local/state/demo-chat/node2-portal-Theta" \
    "/tmp/demo-chat-portal-${UID}-Theta")"
[[ "$result" == "$expected" ]]

result="$(HOME="$fixture/home" "$fixture/bin/setup-portal" Theta demo-chat status)"
[[ "$result" == "${expected/stop/status}" ]]

if HOME="$fixture/home" "$fixture/bin/setup-portal" Theta demo-chat restart \
    > "$fixture/bad-action.out" 2>&1; then
    printf 'setup-portal accepted an unknown action.\n' >&2
    exit 1
fi
grep -Fq 'usage: setup-portal AGENT PROJECT' "$fixture/bad-action.out"

result="$(HOME="$fixture/home" CHAT_PORTAL_HOST=example.lan \
    "$fixture/bin/setup-portal" Sigma example)"
[[ "$result" == *$'\nexample.lan\nSigma\n'* ]]
[[ "$result" == *"$fixture/home/.local/state/example/example-portal-Sigma"* ]]

if HOME="$fixture/home" "$fixture/bin/setup-portal" Theta '../bad' >/dev/null 2>&1; then
    printf 'setup-portal accepted an unsafe project name.\n' >&2
    exit 1
fi

cp "$repo_root/shell-scripts/chat-shell-portal-tunnel" "$fixture/bin/chat-shell-portal-tunnel"
cat > "$fixture/bin/node-9-tunnel" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" > "$CHAT_PORTAL_STATE_DIR/tunnel-arguments"
exit 1
EOF
cat > "$fixture/bin/isTCPOpen" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "$fixture/bin/chat-shell-cli" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$fixture/bin/node-9-tunnel" "$fixture/bin/isTCPOpen" "$fixture/bin/chat-shell-cli"
printf 'fixture-password\n' > "$fixture/secrets/Theta.password"
if HOME="$fixture/home" NODE_9_BIN="$fixture/bin" CHAT_PORTAL_HOST=example.lan \
    CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE="$fixture/secrets/Theta.password" \
    CHAT_PORTAL_STATE_DIR="$fixture/state" CHAT_PORTAL_SOCKET_DIR="$fixture/socket" \
    "$fixture/bin/chat-shell-portal-tunnel" start >/dev/null 2>&1; then
    printf 'portal helper accepted a failed tunnel.\n' >&2
    exit 1
fi
[[ "$(cat "$fixture/state/tunnel-arguments")" == 'example.lan 0 true' ]]
printf 'setup-portal and generic tunnel checks passed\n'
