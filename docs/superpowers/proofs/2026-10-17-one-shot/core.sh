#!/usr/bin/env bash
# Starts or stops the disposable memory core of the stage 0 proof on 6990 and
# 6991. The core runs in its own process group, inside the isolation sandbox.
# chat-build starts Maven, and Maven starts the Java core, so stop signals the
# whole group. Never use chat-deploy here: it controls node 16.
set -uo pipefail
action="${1:?usage: core.sh start|stop <scratch-dir>}"
S="${2:?usage: core.sh start|stop <scratch-dir>}"
cd "$(git rev-parse --show-toplevel)"

listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

case "$action" in
start)
    for port in 6990 6991; do
        if listening "$port"; then echo "core: $port is busy" >&2; exit 2; fi
    done
    (umask 077; openssl rand -hex 16 > "$S/service.password")
    perl -e 'setpgrp(0,0); exec @ARGV' \
        sandbox-exec -f "$S/isolation.sb" \
        env CORE_PORT=6990 CORE_MGMT_PORT=6991 \
        CHAT_SERVICE_PASSWORD="$(cat "$S/service.password")" \
        ./shell-scripts/chat-build core --memory --run --notls --node-id 0 \
        --key-type uuid --init users,rootkeys > "$S/core.log" 2>&1 < /dev/null &
    echo $! > "$S/core.pgid"
    for _ in $(seq 120); do
        if curl -s -m 2 -u actuator:actuator http://127.0.0.1:6991/actuator/health \
            | grep -q '"status":"UP"'; then
            exit 0
        fi
        if ! kill -0 "$(cat "$S/core.pgid")" 2>/dev/null; then
            echo "core: the core process ended, see $S/core.log" >&2
            exit 1
        fi
        sleep 1
    done
    echo "core: no UP health answer in 120 s" >&2
    exit 1
    ;;
stop)
    pgid="$(cat "$S/core.pgid")"
    kill -TERM -- "-$pgid" 2>/dev/null
    for _ in $(seq 30); do
        if ! listening 6990 && ! listening 6991; then break; fi
        sleep 1
    done
    kill -KILL -- "-$pgid" 2>/dev/null
    sleep 1
    if listening 6990 || listening 6991 || pgrep -g "$pgid" >/dev/null; then
        echo "core: the core group is still alive" >&2
        exit 1
    fi
    ;;
*) echo "usage: core.sh start|stop <scratch-dir>" >&2; exit 2 ;;
esac
