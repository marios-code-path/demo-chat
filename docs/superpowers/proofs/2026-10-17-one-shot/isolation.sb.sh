#!/usr/bin/env bash
# Writes the sandbox profile of the stage 0 proof. The profile denies an
# outbound connection to each host port that a running container publishes,
# so no proof process can reach node 16 or its services. The rules use the
# any-host form "*:<port>". A "localhost:<port>" rule blocks curl, but it
# does not block a JVM. See CHAT-jmnjvupr and the stage 0 plan.
set -euo pipefail
S="${1:?usage: isolation.sb.sh <scratch-dir>}"
docker ps --format '{{.Ports}}' | tr ',' '\n' \
    | sed -nE 's/^ *[^ ]*:([0-9]+)(-([0-9]+))?->.*/\1 \3/p' \
    | while read -r first last; do seq "$first" "${last:-$first}"; done \
    | sort -nu > "$S/denied-ports"
if ! { grep -qx 6790 "$S/denied-ports" && grep -qx 6791 "$S/denied-ports"; } \
    && ! { grep -qx 6890 "$S/denied-ports" && grep -qx 6891 "$S/denied-ports"; }; then
    echo "isolation: expected Node 9 host ports 6790/6791 or 6890/6891" >&2
    exit 2
fi
{
    echo '(version 1)'
    echo '(allow default)'
    printf '(deny network-outbound'
    while read -r port; do printf ' (remote ip "*:%s")' "$port"; done < "$S/denied-ports"
    echo ')'
} > "$S/isolation.sb"
