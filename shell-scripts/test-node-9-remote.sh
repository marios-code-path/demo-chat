#!/usr/bin/env bash
# Offline checks for node-9-remote. No ssh connection. See CHAT-egdtmwfc.
#
# A stub port check answers the free calls. A stub tunnel records its
# arguments, its environment, and a chosen status.
set -uo pipefail

cd "$(dirname "$0")/.."
tool="$PWD/node-9/bin/node-9-remote"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

# One stub port check. STUB_BUSY holds the busy ports. STUB_ALL_BUSY answers
# busy for every call.
tcp_stub="$work/isTCPOpen"
cat > "$tcp_stub" <<'STUB'
#!/usr/bin/env bash
[[ -n "${STUB_CHECK_STATUS:-}" ]] && exit "$STUB_CHECK_STATUS"
[[ "${STUB_ALL_BUSY:-0}" == '1' ]] && exit 1
for port in "$@"; do
    for busy in ${STUB_BUSY:-}; do
        [[ "$port" == "$busy" ]] && exit 1
    done
done
exit 0
STUB
chmod +x "$tcp_stub"

# One stub tunnel. It records the arguments and the two ports.
tunnel_stub="$work/node-9-tunnel"
cat > "$tunnel_stub" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" > "${STUB_TUNNEL_ARGS:?}"
printf '%s\n' "${CORE_PORT-}" "${CORE_MGMT_PORT-}" > "${STUB_TUNNEL_ENV:?}"
exit "${STUB_TUNNEL_STATUS:-0}"
STUB
chmod +x "$tunnel_stub"

argsfile="$work/tunnel.args"
envfile="$work/tunnel.env"

# The stub settings are exported, so every child sees them. A test sets one
# value and resets it after the call.
export STUB_BUSY=''
export STUB_ALL_BUSY=0
export STUB_TUNNEL_STATUS=0
export STUB_CHECK_STATUS=''

# run_remote [ARGS...]  Run the tool against the two stubs.
run_remote() {
    out="$(CHAT_REMOTE_TCP_CHECK="$tcp_stub" CHAT_REMOTE_TUNNEL="$tunnel_stub" \
        STUB_TUNNEL_ARGS="$argsfile" STUB_TUNNEL_ENV="$envfile" \
        "$tool" "$@" 2> "$work/stderr")"
    status=$?
    err="$(cat "$work/stderr")"
    args="$(cat "$argsfile" 2> /dev/null || true)"
    if [[ -f "$envfile" ]]; then
        env_ports="$(tr '\n' ' ' < "$envfile")"
    else
        env_ports=''
    fi
}

# -- A free base set -----------------------------------------------------

rm -f "$argsfile" "$envfile"
run_remote node2.lan non-persistent curl -s http://127.0.0.1:6891/health
if [[ $status -eq 0 && "$args" == 'node2.lan 0 non-persistent curl -s http://127.0.0.1:6891/health' ]]; then
    ok 'free_base_takes_offset_0'
else
    bad 'free_base_takes_offset_0' "exit $status, args: $args"
fi

if [[ "$env_ports" == '6890 6891 ' ]]; then
    ok 'base_ports_reach_the_command'
else
    bad 'base_ports_reach_the_command' "ports: $env_ports"
fi

if [[ "$err" == *'registry 127.0.0.1:6892'* ]]; then
    ok 'registry_line_names_the_endpoint'
else
    bad 'registry_line_names_the_endpoint' "error: $err"
fi

# -- A busy base set -----------------------------------------------------

rm -f "$argsfile" "$envfile"
STUB_BUSY='6890'
run_remote node2.lan non-persistent true
STUB_BUSY=''
if [[ $status -eq 0 && "$args" == 'node2.lan 100 non-persistent true' \
      && "$env_ports" == '6990 6991 ' ]]; then
    ok 'busy_base_steps_by_100'
else
    bad 'busy_base_steps_by_100' "exit $status, args: $args, ports: $env_ports"
fi

# -- Two busy sets -------------------------------------------------------

rm -f "$argsfile" "$envfile"
STUB_BUSY='6890 6990'
run_remote node2.lan non-persistent true
STUB_BUSY=''
if [[ $status -eq 0 && "$args" == 'node2.lan 200 non-persistent true' \
      && "$env_ports" == '7090 7091 ' ]]; then
    ok 'second_busy_set_steps_again'
else
    bad 'second_busy_set_steps_again' "exit $status, args: $args, ports: $env_ports"
fi

# -- Every set busy ------------------------------------------------------

rm -f "$argsfile" "$envfile"
STUB_ALL_BUSY=1
run_remote node2.lan non-persistent true
STUB_ALL_BUSY=0
if [[ $status -eq 1 && "$err" == *'no free port set'* && ! -f "$argsfile" ]]; then
    ok 'no_free_offset_exits_1'
else
    bad 'no_free_offset_exits_1' "exit $status, error: $err"
fi

# -- The port check refuses ---------------------------------------------

# Status 2 from isTCPOpen is a bad tool or a missing nc. It is not a busy
# port, so the search must stop at once with the same cause.
rm -f "$argsfile" "$envfile"
STUB_CHECK_STATUS=2
run_remote node2.lan non-persistent true
STUB_CHECK_STATUS=''
if [[ $status -eq 2 && "$err" == *'port check failed'* && ! -f "$argsfile" ]]; then
    ok 'check_refusal_exits_2'
else
    bad 'check_refusal_exits_2' "exit $status, error: $err"
fi

# -- Status pass-through -------------------------------------------------

rm -f "$argsfile" "$envfile"
STUB_TUNNEL_STATUS=7
run_remote node2.lan non-persistent true
STUB_TUNNEL_STATUS=0
if [[ $status -eq 7 ]]; then
    ok 'tunnel_status_passes_through'
else
    bad 'tunnel_status_passes_through' "exit $status"
fi

# -- Dry run -------------------------------------------------------------

rm -f "$argsfile" "$envfile"
run_remote --dry-run node2.lan non-persistent true
if [[ $status -eq 0 && "$args" == '--dry-run node2.lan 0 non-persistent true' ]]; then
    ok 'dry_run_reaches_the_tunnel'
else
    bad 'dry_run_reaches_the_tunnel' "exit $status, args: $args"
fi

# -- A command flag reaches the tunnel -----------------------------------

# A client command holds its own flags, for example
# `register read --as Theta`. The parsing of this tool ends after the host
# and the mode. Every later word is the command. See CHAT-uhdbljkr.
rm -f "$argsfile" "$envfile"
run_remote node2.lan non-persistent register read --as Theta
if [[ $status -eq 0 \
      && "$args" == 'node2.lan 0 non-persistent register read --as Theta' ]]; then
    ok 'command_flags_reach_the_tunnel'
else
    bad 'command_flags_reach_the_tunnel' "exit $status, args: $args, error: $err"
fi

# -- An option after the operands is a caller error -----------------------

# A preview that a person asks for at the end must not run the command.
# A live run of `stop` meets this case. See CHAT-uhdbljkr.
rm -f "$argsfile" "$envfile"
run_remote node2.lan non-persistent stop --dry-run
if [[ $status -eq 2 && "$err" == *'--dry-run'* && ! -f "$argsfile" ]]; then
    ok 'trailing_dry_run_exits_2'
else
    bad 'trailing_dry_run_exits_2' "exit $status, args: $args, error: $err"
fi

# A mistyped option is the same case as a trailing one.
rm -f "$argsfile" "$envfile"
run_remote node2.lan non-persistent --dryrun stop
if [[ $status -eq 2 && "$err" == *'--dryrun'* && ! -f "$argsfile" ]]; then
    ok 'mistyped_option_exits_2'
else
    bad 'mistyped_option_exits_2' "exit $status, args: $args, error: $err"
fi

# A `--` word is a separator in any position. It never reaches the tunnel,
# because the tunnel would run it as a command name.
rm -f "$argsfile" "$envfile"
run_remote node2.lan non-persistent -- true
if [[ $status -eq 0 && "$args" == 'node2.lan 0 non-persistent true' ]]; then
    ok 'separator_word_is_dropped'
else
    bad 'separator_word_is_dropped' "exit $status, args: $args, error: $err"
fi

# A flag before the host is still a bad option of this tool.
rm -f "$argsfile" "$envfile"
run_remote --bogus node2.lan non-persistent true
if [[ $status -eq 2 && "$err" == *usage* && ! -f "$argsfile" ]]; then
    ok 'unknown_option_before_the_host_exits_2'
else
    bad 'unknown_option_before_the_host_exits_2' "exit $status, error: $err"
fi

# -- A missing tool ------------------------------------------------------

CHAT_REMOTE_TCP_CHECK="$work/absent" CHAT_REMOTE_TUNNEL="$tunnel_stub" \
    "$tool" node2.lan non-persistent true > /dev/null 2> "$work/stderr"
status=$?
if [[ $status -eq 2 && "$(cat "$work/stderr")" == *'not executable'* ]]; then
    ok 'missing_check_exits_2'
else
    bad 'missing_check_exits_2' "exit $status"
fi

# -- Argument refusal ----------------------------------------------------

run_remote node2.lan
if [[ $status -eq 2 && "$err" == *usage* ]]; then
    ok 'missing_mode_exits_2'
else
    bad 'missing_mode_exits_2' "exit $status, error: $err"
fi

rm -f "$argsfile"
run_remote '' non-persistent true
if [[ $status -eq 2 && "$err" == *'host is empty'* && ! -f "$argsfile" ]]; then
    ok 'empty_host_exits_2'
else
    bad 'empty_host_exits_2' "exit $status, error: $err"
fi

# -- Result --------------------------------------------------------------

if (( failures > 0 )); then
    printf '%d failures\n' "$failures"
    exit 1
fi
printf '%d checks, 0 failures\n' 17
