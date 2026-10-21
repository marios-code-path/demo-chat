#!/usr/bin/env bash
# Proves one sandboxed register read for task 7 item 15 of stage 3.
#
#   sandbox-read.sh <scratch-dir> [name]
#
# The profile denies an outbound connection to each published host port
# except 6890 and 6891, which are node 9's ports. The rule uses the any-host
# form "*:<port>", because a "localhost:<port>" rule does not block a JVM.
# A Java probe proves both sides of the profile before the read runs.
#
# The read runs as <name>, which defaults to Theta. It must give status 0,
# empty standard error, 15 records, and one duplicate-pointer warning. No
# denied warning may appear. See the design, decision 39, and the stage 0
# isolation script.
set -uo pipefail
S="${1:?usage: sandbox-read.sh <scratch-dir> [name]}"
name="${2:-Theta}"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
probe="$root/docs/superpowers/proofs/2026-10-17-one-shot/Probe.java"
mkdir -p "$S"

# The denied ports are every published host port except node 9's two.
docker ps --format '{{.Ports}}' | tr ',' '\n' \
    | sed -nE 's/^ *[^ ]*:([0-9]+)(-([0-9]+))?->.*/\1 \3/p' \
    | while read -r first last; do seq "$first" "${last:-$first}"; done \
    | sort -nu | grep -vx 6890 | grep -vx 6891 > "$S/denied-ports"
if ! grep -qx 9042 "$S/denied-ports" || ! grep -qx 5432 "$S/denied-ports"; then
    echo "sandbox: the deny list does not hold the node 16 ports" >&2
    exit 2
fi

{
    echo '(version 1)'
    echo '(allow default)'
    printf '(deny network-outbound'
    while read -r port; do printf ' (remote ip "*:%s")' "$port"; done < "$S/denied-ports"
    echo ')'
} > "$S/isolation.sb"
cp "$S/isolation.sb" "$here/live-sandbox.sb"

# Prove the profile. Every denied port must fail and 6890 must connect.
sandbox-exec -f "$S/isolation.sb" java "$probe" \
    $(cat "$S/denied-ports") 6890 > "$here/live-sandbox-probe.out" 2>&1
probe_status=$?
denied_ok=$(grep -c ' denied:' "$here/live-sandbox-probe.out")
denied_all=$(wc -l < "$S/denied-ports" | tr -d ' ')
open_ok=$(grep -c '^6890 connected$' "$here/live-sandbox-probe.out")

# Run the read inside the same profile.
sandbox-exec -f "$S/isolation.sb" "$root/node-9/bin/register" read --as "$name" \
    > "$here/live-sandbox-read.out" 2> "$here/live-sandbox-read.err"
read_status=$?
records=$(jq -r 'select((.type // "record") == "record") | .id' \
    "$here/live-sandbox-read.out" 2> /dev/null | wc -l | tr -d ' ')
warnings=$(jq -r 'select(.type == "warning") | .kind' \
    "$here/live-sandbox-read.out" 2> /dev/null | wc -l | tr -d ' ')
denied=$(jq -r 'select(.type == "warning" and (.kind == "denied-index" or .kind == "denied-topic")) | .kind' \
    "$here/live-sandbox-read.out" 2> /dev/null | wc -l | tr -d ' ')
err_bytes=$(wc -c < "$here/live-sandbox-read.err" | tr -d ' ')

printf 'probe_status=%s denied_ports=%s denied_seen=%s open_6890=%s\n' \
    "$probe_status" "$denied_all" "$denied_ok" "$open_ok"
printf 'read_status=%s records=%s warnings=%s denied_warnings=%s err_bytes=%s\n' \
    "$read_status" "$records" "$warnings" "$denied" "$err_bytes"

(( probe_status == 0 && denied_ok == denied_all && open_ok == 1 )) || exit 1
(( read_status == 0 && records == 15 && denied == 0 && err_bytes == 0 )) || exit 1
