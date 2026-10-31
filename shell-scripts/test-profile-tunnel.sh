#!/usr/bin/env bash
# Verify general tool resolution and profile-driven tunnel ports.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
general="$work/general"
mkdir -p "$general" "$work/lib" "$work/deployment/bin"
for file in chat-shell-portal-tunnel node-inputs.sh node-profile.sh tunnel-ports.sh; do
    [[ ! -f "$repo/shell-scripts/$file" ]] || cp "$repo/shell-scripts/$file" "$general/$file"
done
cat > "$general/isTCPOpen" <<'EOF'
#!/usr/bin/env bash
printf 'tcp:%s\n' "$*" >> "$CHAT_TEST_CALLS"
exit 0
EOF
cat > "$general/chat-tunnel" <<'EOF'
#!/usr/bin/env bash
printf 'tunnel:%s\n' "$*" >> "$CHAT_TEST_CALLS"
printf '999999\n'
EOF
cat > "$general/chat-shell-cli" <<'EOF'
#!/usr/bin/env bash
printf 'cli:%s:%s\n' "$CORE_PORT" "$CORE_MGMT_PORT" >> "$CHAT_TEST_CALLS"
exit 1
EOF
for file in ps pgrep; do
    printf '#!/usr/bin/env bash\nexit 1\n' > "$general/$file"
done
for file in node-9-tunnel isTCPOpen chat-shell-cli; do
    printf '#!/usr/bin/env bash\nprintf "deployment sentinel\\n" >> "$CHAT_TEST_SENTINEL"\nexit 89\n' \
        > "$work/deployment/bin/$file"
done
chmod +x "$general"/* "$work/deployment/bin"/*
checks=0
failures=0
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
for deployment in absent sentinel; do
    : > "$work/calls"
    rm -f "$work/sentinel"
    case "$deployment" in absent) selected_bin="$work/absent-deployment/bin" ;; sentinel) selected_bin="$work/deployment/bin" ;; esac
    status=0
    env PATH="$general:$PATH" NODE_9_BIN="$selected_bin" \
        CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
        CHAT_PORTAL_HOST=example.test CHAT_SHELL_USER=Agent CHAT_SHELL_PASSWORD=fixture-password \
        CHAT_SHELL_LIB="$work/lib" CHAT_PORTAL_OFFSET=0 \
        CHAT_PORTAL_STATE_DIR="$work/state-$deployment" CHAT_PORTAL_SOCKET_DIR="$work/socket-$deployment" \
        CHAT_TEST_CALLS="$work/calls" CHAT_TEST_SENTINEL="$work/sentinel" \
        bash "$general/chat-shell-portal-tunnel" start > "$work/out" 2> "$work/error" || status=$?
    label="$deployment deployment bin is unnecessary for the general TCP check"
    check grep -Fxq 'tcp:15890 15891 15892' "$work/calls"
    label="$deployment deployment bin is unnecessary for the general CLI"
    check grep -Fxq 'cli:15890:15891' "$work/calls"
    label="$deployment deployment executables are never invoked"
    check test ! -e "$work/sentinel"
done
for selected in alpha beta; do
    case "$selected" in alpha) port=15890 ;; beta) port=16890 ;; esac
    status=0
    CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/$selected.env" \
        bash "$repo/shell-scripts/chat-tunnel" --dry-run example.test 100 non-persistent /bin/true \
        > "$work/out" 2> "$work/error" || status=$?
    label="$selected profile supplies the SSH forwarding ports"
    check grep -Fq "127.0.0.1:$((port + 100)):127.0.0.1:$port" "$work/out"
    for delta in 1 2; do
        label="$selected profile supplies forwarding port $((port + delta))"
        check grep -Fq "127.0.0.1:$((port + delta + 100)):127.0.0.1:$((port + delta))" "$work/out"
    done
done
cat > "$work/refused-check" <<'EOF'
#!/usr/bin/env bash
printf 'refused\n' >> "$CHAT_TEST_CALLS"
exit 2
EOF
chmod +x "$work/refused-check"
: > "$work/calls"
status=0
CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
    CHAT_REMOTE_TCP_CHECK="$work/refused-check" CHAT_REMOTE_TUNNEL="$general/chat-tunnel" \
    CHAT_TEST_CALLS="$work/calls" bash "$repo/shell-scripts/chat-remote" example.test non-persistent true \
    > "$work/out" 2> "$work/error" || status=$?
label='a refused availability probe preserves status 2'
check test "$status" -eq 2
label='a refused probe stops search before another probe or tunnel'
check test "$(cat "$work/calls")" = refused
sed -e 's/15890/65000/' -e 's/15891/65001/' -e 's/15892/65002/' \
    "$repo/shell-scripts/fixtures/profiles/alpha.env" > "$work/high-ports.env"
: > "$work/calls"
status=0
env PATH="$general:$PATH" CHAT_NODE_PROFILE="$work/high-ports.env" \
    CHAT_PORTAL_HOST=example.test CHAT_SHELL_USER=Agent CHAT_SHELL_PASSWORD=fixture-password \
    CHAT_SHELL_LIB="$work/lib" CHAT_PORTAL_OFFSET=1000 \
    CHAT_PORTAL_STATE_DIR="$work/state-overflow" CHAT_PORTAL_SOCKET_DIR="$work/socket-overflow" \
    CHAT_TEST_CALLS="$work/calls" \
    bash "$general/chat-shell-portal-tunnel" start > "$work/out" 2> "$work/error" || status=$?
label='a profile offset overflow returns status 2'
check test "$status" -eq 2
label='an overflowing offset starts no TCP probe or CLI'
check test ! -s "$work/calls"
mkdir -p "$work/source/shell-scripts"
cat > "$work/source/shell-scripts/chat-shell-portal" <<'EOF'
#!/usr/bin/env bash
printf 'unexpected source fallback\n' > "$CHAT_TEST_SENTINEL"
exit 1
EOF
chmod +x "$work/source/shell-scripts/chat-shell-portal"
rm -f "$work/sentinel"
status=0
env PATH="$general:$PATH" CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
    CHAT_SHELL_REPO="$work/source" CHAT_SHELL_CLI="$work/missing-cli" \
    CHAT_PORTAL_HOST=example.test CHAT_SHELL_USER=Agent CHAT_SHELL_PASSWORD=fixture-password \
    CHAT_SHELL_LIB="$work/lib" CHAT_PORTAL_OFFSET=0 \
    CHAT_PORTAL_STATE_DIR="$work/state-explicit" CHAT_PORTAL_SOCKET_DIR="$work/socket-explicit" \
    CHAT_TEST_CALLS="$work/calls" CHAT_TEST_SENTINEL="$work/sentinel" \
    bash "$general/chat-shell-portal-tunnel" start > "$work/out" 2> "$work/error" || status=$?
label='a missing explicit CLI cannot select the source portal'
check test ! -e "$work/sentinel"
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
