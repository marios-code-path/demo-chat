#!/usr/bin/env bash
# One time limit for every probe of a java. See CHAT-faqxupam.
#
# Source this file. It defines java_probe_run alone, and it starts nothing.
#
# A java can answer nothing. The stub at /usr/bin/java holds its caller when
# JAVA_HOME names no JDK, and it prints no line. So every probe of a java takes
# a time limit. The limit needs no timeout command, because a host can lack it,
# and a probe without a limit holds an install for hours.

# java_probe_run LIMIT CMD [ARG...]
#   Run CMD with a limit in whole seconds. Print the output of CMD, both
#   streams. Return the status of CMD, or 124 when the limit passes first.
java_probe_run() {
    local limit="$1"
    shift
    local out
    out="$(mktemp)" || return 2
    "$@" > "$out" 2>&1 &
    local pid=$!
    local waited=0
    local status=0
    while kill -0 "$pid" 2> /dev/null; do
        if (( waited >= limit )); then
            kill -9 "$pid" 2> /dev/null
            wait "$pid" 2> /dev/null
            rm -f "$out"
            return 124
        fi
        sleep 1
        waited=$(( waited + 1 ))
    done
    wait "$pid"
    status=$?
    cat "$out"
    rm -f "$out"
    return "$status"
}
