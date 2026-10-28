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
pid="${2:-}"
if [[ -n "${FAKE_PS_PORTAL_ANY:-}" ]]; then
    printf -- '-Dchat.shell.portal.socket=%s\n' "${FAKE_PS_SOCKET:-}"
    exit 0
fi
if [[ -n "${FAKE_PS_PORTAL_PID:-}" && "$pid" == "$FAKE_PS_PORTAL_PID" ]]; then
    printf -- '-Dchat.shell.portal.socket=%s\n' "${FAKE_PS_SOCKET:-}"
    exit 0
fi
if [[ -n "${FAKE_PS_TUNNEL_PID:-}" && "$pid" == "$FAKE_PS_TUNNEL_PID" ]]; then
    printf 'ssh -- node2.lan 127.0.0.1:6890\n'
    exit 0
fi
printf '%s\n' "${FAKE_PS_COMMAND:-}"
STUB
cat > "$bin/pgrep" <<'STUB'
#!/usr/bin/env bash
if [[ -n "${FAKE_PGREP_EXIT:-}" ]]; then
    exit "$FAKE_PGREP_EXIT"
fi
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

# A probe that cannot read the process list proves nothing. The socket stays.
: > "$socket"
code="$(run_tunnel stop FAKE_PGREP_EXIT=3)"
if [[ "$code" == 0 && -e "$socket" ]]; then
    ok 'stop_keeps_a_socket_when_the_probe_fails'
else
    bad 'stop_keeps_a_socket_when_the_probe_fails' \
        "exit $code, socket exists: $([[ -e "$socket" ]] && echo yes || echo no)"
fi

# A start cannot clear a path that it cannot check, so it refuses.
: > "$socket"
code="$(run_tunnel start FAKE_PGREP_EXIT=3)"
if [[ "$code" != 0 && -e "$socket" && "$(cat "$fixture/stderr")" == *'cannot check'* ]]; then
    ok 'start_refuses_a_socket_when_the_probe_fails'
else
    bad 'start_refuses_a_socket_when_the_probe_fails' \
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

# status names the part that runs, so an operator sees a partial pair.
# The pid files and the ps stub decide which part answers. The running pair
# case needs a real socket file, so a helper holds one.
start_socket_holder() {
    python3 - "$socket" <<'PY' &
import socket, sys, time

handle = socket.socket(socket.AF_UNIX)
handle.bind(sys.argv[1])
handle.listen(1)
time.sleep(60)
PY
    socket_holder=$!
    local waited=0
    while [[ ! -S "$socket" ]] && (( waited < 50 )); do
        sleep 0.1
        waited=$(( waited + 1 ))
    done
    [[ -S "$socket" ]]
}

stop_socket_holder() {
    kill "$socket_holder" 2>/dev/null || true
    wait "$socket_holder" 2>/dev/null || true
    rm -f "$socket"
}

start_socket_holder || bad 'the socket helper did not start'
printf '111\n' > "$state/portal.pid"
printf '222\n' > "$state/tunnel.pid"
status_env=(FAKE_PS_PORTAL_PID=111 FAKE_PS_TUNNEL_PID=222 FAKE_PS_SOCKET="$socket")

code="$(run_tunnel status "${status_env[@]}")"
if [[ "$code" == 0 && "$(cat "$fixture/stdout")" == *'portal and tunnel are running'* ]]; then
    ok 'status_reports_a_running_pair'
else
    bad 'status_reports_a_running_pair' \
        "exit $code, stdout: $(cat "$fixture/stdout")"
fi

rm -f "$state/portal.pid"
code="$(run_tunnel status "${status_env[@]}")"
if [[ "$code" != 0 && "$(cat "$fixture/stdout")" == *'tunnel is running but the portal is not'* ]]; then
    ok 'status_names_a_running_tunnel'
else
    bad 'status_names_a_running_tunnel' \
        "exit $code, stdout: $(cat "$fixture/stdout")"
fi

printf '111\n' > "$state/portal.pid"
rm -f "$state/tunnel.pid"
code="$(run_tunnel status "${status_env[@]}")"
if [[ "$code" != 0 && "$(cat "$fixture/stdout")" == *'portal is running but the tunnel is not'* ]]; then
    ok 'status_names_a_running_portal'
else
    bad 'status_names_a_running_portal' \
        "exit $code, stdout: $(cat "$fixture/stdout")"
fi

printf '222\n' > "$state/tunnel.pid"
stop_socket_holder
code="$(run_tunnel status "${status_env[@]}")"
if [[ "$code" != 0 && "$(cat "$fixture/stdout")" == *'socket'* ]]; then
    ok 'status_names_a_missing_socket'
else
    bad 'status_names_a_missing_socket' \
        "exit $code, stdout: $(cat "$fixture/stdout")"
fi

# A successful start publishes the live socket path.
portal_repo="$fixture/portal-repo"
mkdir -p "$portal_repo/shell-scripts" "$portal_repo/chat-deploy/target"
printf 'fixture-classpath\n' > "$portal_repo/chat-deploy/target/shell-cli.classpath"
cat > "$portal_repo/shell-scripts/chat-shell-portal" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
[[ -z "${FAIL_PORTAL:-}" ]] || exit 1
socket="${CHAT_SHELL_PORTAL_SOCKET:-}"
[[ -n "$socket" ]] || exit 2
perl -MIO::Socket::UNIX -e 'my $s = IO::Socket::UNIX->new(Type => SOCK_STREAM, Local => $ARGV[0], Listen => 1) or die $!; $SIG{TERM} = sub { exit 0 }; sleep 60' "$socket" &
child=$!
trap 'kill "$child" 2>/dev/null' TERM INT
wait "$child"
STUB
chmod +x "$portal_repo/shell-scripts/chat-shell-portal"

proof_state="$fixture/proof-state/node2-portal-theta"
proof_socket_dir="$fixture/proof-sockets"
proof_socket="$proof_socket_dir/p"
proof_file="$fixture/proof/portal-theta/socket"
mkdir -p "$(dirname "$proof_file")"
run_proof() {
    local action="$1" code=0
    shift
    env PATH="$bin:/usr/bin:/bin" HOME="$fixture" NODE_9_BIN="$bin" \
        CHAT_SHELL_REPO="$portal_repo" CHAT_PORTAL_HOST=node2.lan \
        CHAT_SHELL_USER=theta CHAT_SHELL_PASSWORD=fixture-secret \
        CHAT_PORTAL_STATE_DIR="$proof_state" CHAT_PORTAL_SOCKET_DIR="$proof_socket_dir" \
        CHAT_PORTAL_SOCKET_FILE="$proof_file" \
        FAKE_PS_REQUIRE_ALIVE=1 FAKE_PS_PORTAL_ANY=1 FAKE_PS_SOCKET="$proof_socket" \
        "$@" "$tunnel_script" "$action" > "$fixture/proof.out" 2>&1 || code=$?
    printf '%s' "$code"
}

code="$(run_proof start)"
if [[ "$code" == 0 && "$(cat "$proof_file" 2>/dev/null)" == "$proof_socket" ]]; then
    ok 'start_publishes_the_socket_file'
else
    bad 'start_publishes_the_socket_file' \
        "exit $code, file: $(cat "$proof_file" 2>/dev/null), output: $(cat "$fixture/proof.out")"
fi
proof_mode="$(perl -e 'printf "%o", (stat($ARGV[0]))[2] & 07777' "$proof_file" 2>/dev/null || true)"
if [[ "$proof_mode" == 600 ]]; then
    ok 'the_socket_file_is_private'
else
    bad 'the_socket_file_is_private' "mode $proof_mode"
fi

code="$(run_proof stop)"
if [[ "$code" == 0 && ! -e "$proof_file" ]]; then
    ok 'stop_removes_the_socket_file'
else
    bad 'stop_removes_the_socket_file' \
        "exit $code, file exists: $([[ -e "$proof_file" ]] && echo yes || echo no)"
fi

# A file that names another socket belongs to another portal.
printf '/tmp/other-portal/p\n' > "$proof_file"
rm -f "$proof_state/portal.pid" "$proof_state/tunnel.pid"
code="$(run_proof stop)"
if [[ "$code" == 0 && -f "$proof_file" ]]; then
    ok 'stop_keeps_a_socket_file_for_another_socket'
else
    bad 'stop_keeps_a_socket_file_for_another_socket' \
        "exit $code, file exists: $([[ -e "$proof_file" ]] && echo yes || echo no)"
fi
rm -f "$proof_file"

# A failed start writes no socket file.
code="$(run_proof start FAIL_PORTAL=1)"
if [[ "$code" != 0 && ! -e "$proof_file" ]]; then
    ok 'a_failed_start_writes_no_socket_file'
else
    bad 'a_failed_start_writes_no_socket_file' \
        "exit $code, file exists: $([[ -e "$proof_file" ]] && echo yes || echo no)"
fi

if (( failures > 0 )); then
    printf '%d case(s) failed\n' "$failures" >&2
    exit 1
fi
printf 'chat-shell-portal-tunnel socket rules passed\n'
