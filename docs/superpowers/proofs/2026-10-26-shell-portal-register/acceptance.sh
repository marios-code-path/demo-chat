#!/usr/bin/env bash
# Prove two MCP reads through one portal JVM and a disposable core.
set -euo pipefail
umask 077

S="${1:?usage: acceptance.sh <private-log-directory>}"
mkdir -p "$S"
chmod 700 "$S"
here="$(cd "$(dirname "$0")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
stage="docs/superpowers/proofs/2026-10-17-one-shot"
core_script="$root/$stage/core.sh"
isolation_script="$root/$stage/isolation.sb.sh"
portal_script="$root/shell-scripts/chat-shell-portal"
mcp_harness="$root/chat-mcp/src/test/client/harness.mjs"
user_init="$root/shared-deploy-configuration/src/main/config/userinit.yml"
core_started=0
portal_group=""
portal_started=0

fail() {
    printf 'acceptance: %s\n' "$1" >&2
    exit 1
}

shell_pids() {
    "${JPS_BIN:?}" -l | awk '$2 == "com.demo.chat.ShellCliKt" { print $1 }' | sort -n
}

cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [[ "$portal_started" == 1 ]]; then
        kill -TERM -- "-$portal_group" 2>/dev/null || true
        for _ in $(seq 30); do
            if ! pgrep -g "$portal_group" >/dev/null 2>&1; then break; fi
            sleep 1
        done
        if pgrep -g "$portal_group" >/dev/null 2>&1; then
            kill -KILL -- "-$portal_group" 2>/dev/null || true
        fi
        wait "$portal_group" 2>/dev/null || true
    fi
    if [[ "$core_started" == 1 && -s "$S/core.pgid" ]]; then
        "$core_script" stop "$S" > "$S/core-stop.log" 2>&1 || status=1
    fi
    rm -f "$S/admin.password" "$S/adapter.token" "$S/service.password"
    if (( status != 0 )); then
        printf 'acceptance: failed. Private logs are in %s\n' "$S" >&2
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command in awk chmod curl docker java jps just lsof mvn node openssl perl pgrep ps sandbox-exec; do
    command -v "$command" >/dev/null 2>&1 || fail "missing command: $command"
done
[[ -s "$root/chat-deploy/target/shell-cli.classpath" ]] || fail 'run just build-shell-cli first'
[[ -f "$mcp_harness" ]] || fail 'the pinned MCP client harness is missing'

java_bin="$(command -v java)"
jps_bin="$(command -v jps)"
JPS_BIN="$jps_bin"
export JPS_BIN

# Copy the disposable core login into a private file without printing it.
awk '/^      Admin:/ { admin=1 } admin && /password:/ { print $2; exit }' "$user_init" > "$S/admin.password"
[[ -s "$S/admin.password" ]] || fail 'the disposable Admin password is missing'
chmod 600 "$S/admin.password"
printf 'test-token\n' > "$S/adapter.token"
chmod 600 "$S/adapter.token"

# Build the adapter classes and its runtime classpath.
mvn -B -ntp -pl chat-mcp -am -DskipTests package > "$S/mcp-package.log" 2>&1
mvn -B -ntp -pl chat-mcp dependency:build-classpath \
    -DincludeScope=runtime -Dmdep.outputFile="$S/mcp-runtime.classpath" \
    > "$S/mcp-classpath.log" 2>&1
[[ -s "$S/mcp-runtime.classpath" ]] || fail 'Maven wrote no adapter classpath'
mcp_classpath="$root/chat-mcp/target/classes:$(cat "$S/mcp-runtime.classpath")"

# Create an empty index in the disposable core before measuring portal reuse.
"$isolation_script" "$S" > "$S/isolation.log" 2>&1
core_started=1
"$core_script" start "$S" > "$S/core-start.log" 2>&1
CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SHELL_KEY_TYPE=uuid \
CHAT_SHELL_USER=Admin CHAT_SHELL_PASSWORD_FILE="$S/admin.password" \
    "$root/shell-scripts/chat-shell-cli" add-topic --name register-index \
    > "$S/seed-index.out" 2> "$S/seed-index.err" \
    || fail 'the disposable core could not create the register index'

# Start one portal JVM in its own process group and inside the sandbox.
CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SHELL_KEY_TYPE=uuid \
CHAT_SHELL_USER=Admin CHAT_SHELL_PASSWORD_FILE="$S/admin.password" \
CHAT_SHELL_PORTAL_SOCKET="$S/portal.sock" \
    perl -e 'setpgrp(0,0); exec @ARGV' sandbox-exec -f "$S/isolation.sb" \
    env CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SHELL_KEY_TYPE=uuid \
    CHAT_SHELL_USER=Admin CHAT_SHELL_PASSWORD_FILE="$S/admin.password" \
    CHAT_SHELL_PORTAL_SOCKET="$S/portal.sock" "$portal_script" \
    > "$S/portal.out" 2> "$S/portal.err" &
portal_group=$!
portal_started=1
for _ in $(seq 120); do
    [[ -S "$S/portal.sock" ]] && break
    kill -0 "$portal_group" 2>/dev/null || fail 'the portal process ended before bind'
    sleep 1
done
[[ -S "$S/portal.sock" ]] || fail 'the portal socket did not appear within 120 seconds'

before="$(shell_pids)"
[[ "$(wc -l <<< "$before" | tr -d ' ')" == 1 ]] || fail 'expected one shell JVM before the reads'
portal_pid="$(tr -d ' ' <<< "$before")"
portal_pgid="$(ps -o pgid= -p "$portal_pid" | tr -d ' ')"
[[ "$portal_pgid" == "$portal_group" ]] || fail 'the portal JVM left its process group'

# Send two calls through one MCP session and one portal JVM.
cat > "$S/calls.json" <<'EOF'
[
  {"name":"chat_read_register","arguments":{}},
  {"name":"chat_read_register","arguments":{}}
]
EOF
cat > "$S/adapter.properties" <<EOF
backendBaseUrl=http://127.0.0.1:6990
credentialFile=$S/adapter.token
keyType=uuid
topicIds=00000000-0000-0000-0000-000000000001
portalSocket=$S/portal.sock
registerName=register-index
EOF
node "$mcp_harness" --calls-file "$S/calls.json" -- \
    "$java_bin" -cp "$mcp_classpath" com.demo.chat.mcp.McpAdapterMainKt \
    --config "$S/adapter.properties" > "$S/transcript.json" 2> "$S/harness.err" \
    || fail 'the pinned MCP client or adapter failed'
[[ "$(shell_pids)" == "$before" ]] || fail 'the shell JVM PID changed during the calls'
[[ -S "$S/portal.sock" ]] || fail 'the portal socket disappeared during the calls'

# Check both replies without printing register content.
node - "$S/transcript.json" <<'NODE'
const fs = require("node:fs");
const transcript = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
if (transcript.connectError !== null) throw new Error("MCP connect failed");
if (transcript.exit?.withinBound !== true || transcript.exit?.code !== 0) throw new Error("adapter did not exit cleanly");
if (transcript.stdoutParseFailures?.length !== 0) throw new Error("adapter stdout contains non-protocol text");
if (!transcript.tools?.some((tool) => tool.name === "chat_read_register")) throw new Error("register tool was not discovered");
if (transcript.calls?.length !== 2) throw new Error("expected two register calls");
for (const call of transcript.calls) {
  const result = call.structuredContent;
  if (call.name !== "chat_read_register" || call.isError !== false) throw new Error("register call failed");
  if (result?.status !== 0 || result?.complete !== true || !Array.isArray(result?.lines)) throw new Error("register reply has an unexpected shape");
}
console.log(`tool=discovered calls=${transcript.calls.length} status=0 complete=true line_counts=${transcript.calls.map((call) => call.structuredContent.lines.length).join(",")}`);
NODE

after="$(shell_pids)"
[[ "$after" == "$before" ]] || fail 'the shell JVM PID changed or another shell JVM appeared'
[[ -S "$S/portal.sock" ]] || fail 'the portal socket closed before both calls finished'
printf 'portal_pid=%s process_group=%s calls=2 adapter_sessions=1\n' "$portal_pid" "$portal_pgid" \
    > "$S/process-summary.txt"

# Stop the portal before the disposable core.
kill -TERM -- "-$portal_group" 2>/dev/null || true
for _ in $(seq 30); do
    if ! pgrep -g "$portal_group" >/dev/null 2>&1; then break; fi
    sleep 1
done
if pgrep -g "$portal_group" >/dev/null 2>&1; then
    kill -KILL -- "-$portal_group" 2>/dev/null || true
    fail 'the portal process group ignored TERM'
fi
wait "$portal_group" 2>/dev/null || true
portal_started=0
[[ ! -e "$S/portal.sock" ]] || fail 'the portal left its socket path after shutdown'
[[ -z "$(shell_pids)" ]] || fail 'a shell JVM remained after portal shutdown'

"$core_script" stop "$S" > "$S/core-stop.log" 2>&1
core_started=0
rm -f "$S/admin.password" "$S/adapter.token" "$S/service.password"
printf 'acceptance: two reads used one portal JVM; private logs are in %s\n' "$S"
