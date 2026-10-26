#!/usr/bin/env bash
# Offline checks for isTCPOpen. No node starts. See CHAT-gkzjwrkz.
#
# The suite finds three free ports for the pass case. It starts one nc
# listener for the busy case. Both use loopback alone.
set -uo pipefail

cd "$(dirname "$0")/.."
tool="$PWD/node-9/bin/isTCPOpen"

work="$(mktemp -d)"
trap 'rm -rf "$work"; stop_listener' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

listener_pid=''
stop_listener() {
    if [[ -n "$listener_pid" ]]; then
        kill "$listener_pid" 2> /dev/null
        wait "$listener_pid" 2> /dev/null
        listener_pid=''
    fi
}

# port_free PORT  True when nc finds no listener.
port_free() {
    ! nc -z -w 1 127.0.0.1 "$1" > /dev/null 2>&1
}

# Four free ports near the top of the range. Three give the pass case. The
# fourth gives the busy case.
free_ports=''
for candidate in $(seq 61200 61300); do
    if port_free "$candidate"; then
        free_ports="$free_ports $candidate"
        [[ "$(printf '%s\n' $free_ports | wc -l)" -ge 4 ]] && break
    fi
done
if [[ "$(printf '%s\n' $free_ports | wc -l)" -lt 4 ]]; then
    printf 'FAIL the suite cannot find four free ports\n'
    exit 1
fi
# shellcheck disable=SC2086
set -- $free_ports
free_a="$1"; free_b="$2"; free_c="$3"; busy="$4"

# start_listener PORT
# Start one nc listener. The nc variants disagree on the listen form, so try
# each. Hold standard input open, because GNU netcat exits at end of input.
# A probe would consume the listener, so the suite checks that it still runs
# instead of connecting to it.
start_listener() {
    local port="$1" form
    for form in gnu openbsd mac; do
        case "$form" in
            gnu) nc -l -p "$port" < <(sleep 30) > /dev/null 2>&1 & ;;
            openbsd) nc -l "$port" < <(sleep 30) > /dev/null 2>&1 & ;;
            mac) nc -l 127.0.0.1 "$port" < <(sleep 30) > /dev/null 2>&1 & ;;
        esac
        listener_pid=$!
        sleep 0.3
        if kill -0 "$listener_pid" 2> /dev/null; then
            return 0
        fi
        stop_listener
    done
    return 1
}

# -- Free ports ----------------------------------------------------------

out="$("$tool" "$free_a" "$free_b" "$free_c" 2>&1)"
status=$?
want="$(printf '%s\n' "$free_a free" "$free_b free" "$free_c free")"
if [[ $status -eq 0 && "$out" == "$want" ]]; then
    ok 'free_ports_exit_0'
else
    bad 'free_ports_exit_0' "exit $status, output: $out"
fi

# -- One busy port -------------------------------------------------------

if ! start_listener "$busy"; then
    printf 'FAIL the suite cannot start a listener\n'
    exit 1
fi

out="$("$tool" "$free_a" "$busy" "$free_c" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *"$busy in use"* ]]; then
    ok 'busy_port_exit_1'
else
    bad 'busy_port_exit_1' "exit $status, output: $out"
fi
stop_listener

# -- Argument refusals ---------------------------------------------------

out="$("$tool" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *usage* ]]; then
    ok 'no_argument_exit_2'
else
    bad 'no_argument_exit_2' "exit $status, output: $out"
fi

out="$("$tool" "$free_a" 'x1' 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'not a port number'* ]]; then
    ok 'bad_value_exit_2'
else
    bad 'bad_value_exit_2' "exit $status, output: $out"
fi

out="$("$tool" 70000 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'outside 1 to 65535'* ]]; then
    ok 'range_exit_2'
else
    bad 'range_exit_2' "exit $status, output: $out"
fi

out="$("$tool" "$free_a" 'x1' 2>&1)"
if [[ "$out" != *"$free_a free"* ]]; then
    ok 'bad_value_writes_no_port_line'
else
    bad 'bad_value_writes_no_port_line' "output: $out"
fi

# A missing nc is a refusal, not a free port.
mkdir -p "$work/empty"
out="$(PATH="$work/empty" /bin/bash "$tool" "$free_a" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'no nc in PATH'* ]]; then
    ok 'missing_nc_exit_2'
else
    bad 'missing_nc_exit_2' "exit $status, output: $out"
fi

if [[ "$(printf '%s\n' "$out" | wc -l)" -eq 1 ]]; then
    ok 'refusal_writes_one_line'
else
    bad 'refusal_writes_one_line' "output: $out"
fi

# -- Result --------------------------------------------------------------

if (( failures > 0 )); then
    printf '%d failures\n' "$failures"
    exit 1
fi
printf '%d checks, 0 failures\n' 8
