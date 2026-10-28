#!/usr/bin/env bash
# Check the socket ownership rules of chat-shell-portal-tunnel.
#
# The stubs answer the process and port questions, so the check needs no
# portal, no tunnel, and no node. See CHAT-hmbigvmf.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tunnel_script="$repo_root/shell-scripts/chat-shell-portal-tunnel"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() {
    printf 'FAIL %s\n' "$1" >&2
    failures=$(( failures + 1 ))
}

state="$fixture/state"
socket_dir="$fixture/sockets"
socket="$socket_dir/p"
lib="$fixture/lib"
bin="$fixture/bin"
mkdir -p "$state" "$socket_dir" "$lib" "$bin"

# The stubs answer the questions that the script asks about other processes.
cat > "$bin/ps" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "${FAKE_PS_COMMAND:-}"
STUB
cat > "$bin/pgrep" <<'STUB'
#!/usr/bin/env bash
if [[ -n "${FAKE_PORTAL_OWNER:-}" ]]; then
    printf '%s\n' "$FAKE_PORTAL_OWNER"
    exit 0
fi
exit 1
STUB
cat > "$bin/isTCPOpen" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB
cat > "$bin/node-9-tunnel" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "${FAKE_TUNNEL_PID:-999}"
exit "${FAKE_TUNNEL_EXIT:-0}"
STUB
chmod +x "$bin/ps" "$bin/pgrep" "$bin/isTCPOpen" "$bin/node-9-tunnel"

run_tunnel() {
    local action="$1" code=0
    shift
    env PATH="$bin:/usr/bin:/bin" HOME="$fixture" \
        NODE_9_BIN="$bin" CHAT_SHELL_REPO="$repo_root" CHAT_SHELL_LIB="$lib" \
        CHAT_PORTAL_HOST=node2.lan CHAT_SHELL_USER=theta \
        CHAT_SHELL_PASSWORD=fixture-secret \
        CHAT_PORTAL_STATE_DIR="$state" CHAT_PORTAL_SOCKET_DIR="$socket_dir" \
        "$@" "$tunnel_script" "$action" > "$fixture/stdout" 2> "$fixture/stderr" || code=$?
    printf '%s' "$code"
}

# A socket that another portal owns stays after stop.
: > "$socket"
code="$(run_tunnel stop FAKE_PORTAL_OWNER=4242)"
if [[ "$code" == 0 && -e "$socket" ]]; then
    ok 'stop_keeps_a_socket_that_another_portal_owns'
else
    bad 'stop_keeps_a_socket_that_another_portal_owns' \
        "exit $code, socket exists: $([[ -e "$socket" ]] && echo yes || echo no)"
fi

# A socket with no owner is stale, so stop removes it.
code="$(run_tunnel stop FAKE_PORTAL_OWNER=)"
if [[ "$code" == 0 && ! -e "$socket" ]]; then
    ok 'stop_removes_a_stale_socket'
else
    bad 'stop_removes_a_stale_socket' \
        "exit $code, socket exists: $([[ -e "$socket" ]] && echo yes || echo no)"
fi

# A start refuses a socket that another portal owns, and keeps the file.
: > "$socket"
code="$(run_tunnel start FAKE_PORTAL_OWNER=4242)"
if [[ "$code" != 0 && -e "$socket" && "$(cat "$fixture/stderr")" == *'4242'* ]]; then
    ok 'start_refuses_a_socket_that_another_portal_owns'
else
    bad 'start_refuses_a_socket_that_another_portal_owns' \
        "exit $code, stderr: $(cat "$fixture/stderr")"
fi

# A start removes a stale socket and then fails on the tunnel, so the stale
# file does not outlive the attempt.
code="$(run_tunnel start FAKE_PORTAL_OWNER= FAKE_TUNNEL_EXIT=1)"
if [[ "$code" != 0 && ! -e "$socket" ]]; then
    ok 'start_removes_a_stale_socket'
else
    bad 'start_removes_a_stale_socket' \
        "exit $code, socket exists: $([[ -e "$socket" ]] && echo yes || echo no)"
fi

if (( failures > 0 )); then
    printf '%d case(s) failed\n' "$failures" >&2
    exit 1
fi
printf 'chat-shell-portal-tunnel socket rules passed\n'
