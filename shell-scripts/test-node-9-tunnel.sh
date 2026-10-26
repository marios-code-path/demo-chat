#!/usr/bin/env bash
# Offline checks for node-9-tunnel. No ssh connection. See CHAT-bheycntz.
#
# A stub ssh records its process id and sleeps. The suite sets the readiness
# budget to zero, so it never probes a port. The command in each case is a
# local bash call.
set -uo pipefail

cd "$(dirname "$0")/.."
tool="$PWD/node-9/bin/node-9-tunnel"

work="$(mktemp -d)"
trap 'rm -rf "$work"; stop_persistent' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

persistent_pid=''
stop_persistent() {
    if [[ -n "$persistent_pid" ]]; then
        kill "$persistent_pid" 2> /dev/null
        persistent_pid=''
    fi
}

# One stub ssh. It records its pid and its arguments, then sleeps.
stub="$work/ssh-stub"
cat > "$stub" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$$" > "${STUB_SSH_PIDFILE:?}"
printf '%s\n' "$*" > "${STUB_SSH_ARGSFILE:?}"
exec sleep 30
STUB
chmod +x "$stub"

pidfile="$work/ssh.pid"
argsfile="$work/ssh.args"

# run_tunnel [ARGS...]  Run the tool against the stub ssh.
run_tunnel() {
    out="$(CHAT_TUNNEL_SSH="$stub" CHAT_TUNNEL_READY_SECONDS=0 \
        STUB_SSH_PIDFILE="$pidfile" STUB_SSH_ARGSFILE="$argsfile" \
        "$tool" "$@" 2> "$work/stderr")"
    status=$?
    err="$(cat "$work/stderr")"
}

# -- Dry run -------------------------------------------------------------

rm -f "$pidfile" "$argsfile"
run_tunnel --dry-run node2.lan 100 non-persistent bash -c 'exit 0'
if [[ $status -eq 0 ]] \
   && [[ "$out" == *'-L 127.0.0.1:6990:127.0.0.1:6890'* ]] \
   && [[ "$out" == *'-L 127.0.0.1:6991:127.0.0.1:6891'* ]] \
   && [[ "$out" == *'-L 127.0.0.1:6992:127.0.0.1:6892'* ]] \
   && [[ "$out" == *'run bash -c exit 0'* ]]; then
    ok 'dry_run_prints_the_steps'
else
    bad 'dry_run_prints_the_steps' "exit $status, output: $out"
fi

if [[ ! -f "$pidfile" ]]; then
    ok 'dry_run_starts_nothing'
else
    bad 'dry_run_starts_nothing' "the stub ssh ran"
fi

# -- Argument refusals ---------------------------------------------------

run_tunnel node2.lan 100 sometimes bash -c 'exit 0'
if [[ $status -eq 2 && "$err" == *'not a known mode'* ]]; then
    ok 'bad_mode_exit_2'
else
    bad 'bad_mode_exit_2' "exit $status, error: $err"
fi

run_tunnel node2.lan 150 non-persistent bash -c 'exit 0'
if [[ $status -eq 2 && "$err" == *'not a multiple of 100'* ]]; then
    ok 'bad_offset_exit_2'
else
    bad 'bad_offset_exit_2' "exit $status, error: $err"
fi

run_tunnel node2.lan -100 non-persistent bash -c 'exit 0'
if [[ $status -eq 2 && "$err" == *'is not a number'* ]]; then
    ok 'negative_offset_exit_2'
else
    bad 'negative_offset_exit_2' "exit $status, error: $err"
fi

run_tunnel node2.lan 40000 non-persistent bash -c 'exit 0'
if [[ $status -eq 2 && "$err" == *'passes 32768'* ]]; then
    ok 'offset_limit_exit_2'
else
    bad 'offset_limit_exit_2' "exit $status, error: $err"
fi

run_tunnel node2.lan 100 non-persistent
if [[ $status -eq 2 && "$err" == *'needs a command'* ]]; then
    ok 'missing_command_exit_2'
else
    bad 'missing_command_exit_2' "exit $status, error: $err"
fi

run_tunnel node2.lan 100 persistent bash -c 'exit 0'
if [[ $status -eq 2 && "$err" == *'takes no command'* ]]; then
    ok 'persistent_with_command_exit_2'
else
    bad 'persistent_with_command_exit_2' "exit $status, error: $err"
fi

# -- Non-persistent mode -------------------------------------------------

# The short sleep lets the stub ssh start before the command exits.
rm -f "$pidfile"
run_tunnel node2.lan 100 non-persistent bash -c 'sleep 0.5; exit 7'
tunnel_pid="$(cat "$pidfile" 2> /dev/null || true)"
if [[ $status -eq 7 ]]; then
    ok 'non_persistent_returns_command_status'
else
    bad 'non_persistent_returns_command_status' "exit $status, error: $err"
fi

if [[ -n "$tunnel_pid" ]] && ! kill -0 "$tunnel_pid" 2> /dev/null; then
    ok 'non_persistent_stops_the_tunnel'
else
    bad 'non_persistent_stops_the_tunnel' "pid $tunnel_pid still runs"
fi

if [[ -f "$argsfile" ]] && grep -q -- '-N' "$argsfile" \
   && grep -q -- 'ExitOnForwardFailure=yes' "$argsfile"; then
    ok 'ssh_carries_the_tunnel_flags'
else
    bad 'ssh_carries_the_tunnel_flags' "args: $(cat "$argsfile" 2> /dev/null)"
fi

# -- Persistent mode -----------------------------------------------------

rm -f "$pidfile"
run_tunnel node2.lan 200 true
persistent_pid="$(printf '%s' "$out" | tr -d '[:space:]')"
if [[ $status -eq 0 && "$persistent_pid" =~ ^[0-9]+$ ]] \
   && kill -0 "$persistent_pid" 2> /dev/null; then
    ok 'persistent_prints_a_live_pid'
else
    bad 'persistent_prints_a_live_pid' "exit $status, output: $out"
fi

# -- Result --------------------------------------------------------------

if (( failures > 0 )); then
    printf '%d failures\n' "$failures"
    exit 1
fi
printf '%d checks, 0 failures\n' 12
