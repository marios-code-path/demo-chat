#!/usr/bin/env bash
# Prove the register portal path. The script uses one private root.
# It starts one portal, records its processes, and stops only that portal.
# The backend register store is shared. The script creates one register and
# deletes nothing. See CHAT-lmwuxiiq.
set -euo pipefail
umask 077

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(git -C "$here" rev-parse --show-toplevel)"
proof_root="$(mktemp -d "${TMPDIR:-/tmp}/register-portal-proof.XXXXXX")"
chmod 700 "$proof_root"

host="${CHAT_PORTAL_HOST:-node2.lan}"
home_dir="$proof_root/home"
socket_dir="$proof_root/portal"
state_dir="$proof_root/state/node2-portal-theta"
socket_file="$home_dir/.local/state/demo-chat/portal-theta/socket"
config_home="$proof_root/config"
empty_secrets="$proof_root/empty-secrets"
stub_secrets="$proof_root/stub-secrets"
one_shot_stub="$proof_root/bin/chat-shell-cli"
fixture="$proof_root/fixture.md"
tunnel="$root/shell-scripts/chat-shell-portal-tunnel"
register_cmd="$root/node-9/bin/register"
remote_create="${PROOF_REMOTE_CREATE:-/opt/demo-chat/node-9/bin/register-create}"
read_target_ms="${PROOF_READ_TARGET_MS:-10000}"
password_file="${PROOF_PASSWORD_FILE:-$root/node-9/secrets/Theta.password}"
register_name="demo-chat-register-proof-$(date +%H%M%S)$(printf '%02x' $((RANDOM % 256)))"
offset=
portal_started=0
checks=0

fail() {
    printf 'acceptance: %s\n' "$1" >&2
    exit 1
}

ok() {
    checks=$(( checks + 1 ))
    printf 'ok   %s\n' "$1"
}

mkdir -p "$home_dir" "$socket_dir" "$empty_secrets" "$stub_secrets" \
    "$(dirname "$one_shot_stub")"

cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [[ "$portal_started" == 1 && -s "$state_dir/portal.pid" ]]; then
        CHAT_PORTAL_HOST="$host" NODE_9_BIN="$root/node-9/bin" \
            CHAT_SHELL_REPO="$root" HOME="$home_dir" \
            CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE="$password_file" \
            CHAT_PORTAL_STATE_DIR="$state_dir" \
            CHAT_PORTAL_SOCKET_DIR="$socket_dir" \
            CHAT_PORTAL_SOCKET_FILE="$socket_file" \
            "$tunnel" stop > "$proof_root/stop.log" 2>&1 || status=1
    fi
    if (( status != 0 )); then
        printf 'acceptance: failed. Private logs are in %s\n' "$proof_root" >&2
        return "$status"
    fi
    rm -rf "$proof_root"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for command in curl java jq nc perl ps scp ssh; do
    command -v "$command" > /dev/null 2>&1 || fail "missing command: $command"
done
[[ -r "$password_file" ]] || fail "cannot read $password_file"
[[ -s "$root/chat-deploy/target/shell-cli.classpath" ]] \
    || fail 'run just build-shell-cli first'
[[ -s "$root/chat-mcp/target/mcp-runtime.classpath" ]] \
    || fail 'run just build-mcp-stdio first'

# start_portal  Start one portal with private paths.
start_portal() {
    CHAT_PORTAL_HOST="$host" NODE_9_BIN="$root/node-9/bin" \
        CHAT_SHELL_REPO="$root" HOME="$home_dir" \
        CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE="$password_file" \
        CHAT_PORTAL_STATE_DIR="$state_dir" \
        CHAT_PORTAL_SOCKET_DIR="$socket_dir" \
        CHAT_PORTAL_SOCKET_FILE="$socket_file" \
        "$tunnel" start > "$proof_root/start.log" 2>&1
}

# check_ownership  Prove that our two processes are the ones we shall stop.
check_ownership() {
    local portal_pid tunnel_pid
    portal_pid="$(cat "$state_dir/portal.pid")"
    tunnel_pid="$(cat "$state_dir/tunnel.pid")"
    kill -0 "$portal_pid" 2> /dev/null || fail 'the portal pid is not alive'
    ps -p "$portal_pid" -o command= \
        | grep -Fq -- "-Dchat.shell.portal.socket=$socket_dir/p" \
        || fail 'the portal pid holds another socket'
    kill -0 "$tunnel_pid" 2> /dev/null || fail 'the tunnel pid is not alive'
    ps -p "$tunnel_pid" -o command= | grep -Fq -- "-- $host" \
        || fail 'the tunnel pid holds another host'
    ps -p "$tunnel_pid" -o command= | grep -Fq "127.0.0.1:6890" \
        || fail 'the tunnel pid forwards another port'
    printf '%s\n' "$portal_pid" > "$proof_root/portal.pid"
    printf '%s\n' "$tunnel_pid" > "$proof_root/tunnel.pid"
}

# run_register  Run the wrapper on the portal path.
run_register() {
    HOME="$home_dir" XDG_CONFIG_HOME="$config_home" \
        NODE9_ALLOW_SOURCE_TREE=1 NODE9_SECRETS_DIR="$empty_secrets" \
        CHAT_MCP_STDIO="$root/shell-scripts/chat-mcp-stdio" \
        "$register_cmd" "$@"
}

# The imported section key must not collide with the short name in step 6.
# register-create mints one topic per section: <register>-fr-<key>.
printf '# Proof register\n\nThe portal path creates this register.\n\n## Imported section\n\nOne entry for the portal proof.\n' \
    > "$fixture"

# 1. Start one portal.
start_portal || fail "the portal did not start. See $proof_root/start.log"
portal_started=1
offset="$(cat "$state_dir/offset")"
ok 'portal_started'

# 2. Record ownership, and check it before any stop.
check_ownership
{
    printf 'socket=%s\n' "$socket_dir/p"
    printf 'state=%s\n' "$state_dir"
    printf 'register=%s\n' "$register_name"
    printf 'portal.pid=%s\n' "$(cat "$state_dir/portal.pid")"
    printf 'tunnel.pid=%s\n' "$(cat "$state_dir/tunnel.pid")"
} > "$proof_root/ownership.txt"
ok 'ownership_recorded'

# 3. Create the controlled register on the node, where the CQL client lives.
#    The node reads its own installed secrets. The fixture goes to a private
#    temporary path on the node, and the script removes that file.
remote_fixture="/tmp/${register_name}.md"
scp -q "$fixture" "$host:$remote_fixture" \
    || fail 'cannot send the fixture to the node'
if ! ssh -o BatchMode=yes "$host" \
        "REGISTER_IMPORT_READ_TARGET_MS=$read_target_ms $remote_create $register_name $remote_fixture --source-date 2026-10-28 --as Mario" \
        > "$proof_root/create.log" 2>&1; then
    ssh -o BatchMode=yes "$host" "rm -f $remote_fixture" || true
    fail "register-create failed. See $proof_root/create.log"
fi
ssh -o BatchMode=yes "$host" "rm -f $remote_fixture" \
    || fail 'cannot remove the remote fixture'
ok "register_created=$register_name"

# 4. Write the app config into the private config home.
HOME="$home_dir" XDG_CONFIG_HOME="$config_home" NODE9_ALLOW_SOURCE_TREE=1 \
    env -u NODE_9_REGISTER_TOPIC_NAME -u NODE_9_REGISTER_TOPIC_ID \
    "$register_cmd" config --agent Theta --project demo-chat \
    --register "$register_name" > /dev/null \
    || fail 'register config failed'
[[ "$(sed -n '1p' "$socket_file")" == "$socket_dir/p" ]] \
    || fail 'the socket file holds another path'
ok 'config_written'

# 5. Read through the portal. The empty secrets directory makes a silent
#    one-shot fallback fail.
run_register read --agent Theta --project demo-chat \
    > "$proof_root/read.out" 2> "$proof_root/read.err" \
    || fail "the portal read failed. See $proof_root/read.err"
jq -e 'any(.[]; .type == "record")' --slurp "$proof_root/read.out" > /dev/null \
    || fail 'the portal read printed no record'
ok 'portal_read=ok'

# 6. Open the pointer of the fixture key.
run_register open proof-key proof-entry 'Proof entry' \
    --agent Theta --project demo-chat \
    > "$proof_root/open.out" 2> "$proof_root/open.err" \
    || fail "the open failed. See $proof_root/open.err"
[[ "$(jq -r 'select(.type == "open") | .state' "$proof_root/open.out")" == 'created' ]] \
    || fail 'the open did not create the pointer'
ok 'portal_open=ok'

# 7. Post the fixture text.
printf 'Proof text through the portal.\n' \
    | run_register post proof-key --agent Theta --project demo-chat \
        > "$proof_root/post.out" 2> "$proof_root/post.err" \
    || fail "the post failed. See $proof_root/post.err"
jq -e 'any(.[]; .type == "post")' --slurp "$proof_root/post.out" > /dev/null \
    || fail 'the post printed no post line'
ok 'portal_write=ok'

# 8. Read again, and expect the pointer.
run_register read --agent Theta --project demo-chat \
    > "$proof_root/read2.out" 2> "$proof_root/read2.err" \
    || fail "the second read failed. See $proof_root/read2.err"
jq -e 'any(.[]; .type == "record" and (.text | test("proof-key")))' \
    --slurp "$proof_root/read2.out" > /dev/null \
    || fail 'the second read holds no pointer'
ok 'portal_readback=ok'

# 9. Stop the proof portal, after the ownership check runs again.
check_ownership
CHAT_PORTAL_HOST="$host" NODE_9_BIN="$root/node-9/bin" \
    CHAT_SHELL_REPO="$root" HOME="$home_dir" \
    CHAT_SHELL_USER=Theta CHAT_SHELL_PASSWORD_FILE="$password_file" \
    CHAT_PORTAL_STATE_DIR="$state_dir" \
    CHAT_PORTAL_SOCKET_DIR="$socket_dir" \
    CHAT_PORTAL_SOCKET_FILE="$socket_file" \
    "$tunnel" stop > "$proof_root/stop.log" 2>&1 \
    || fail 'the proof portal did not stop'
portal_started=0
ok 'portal_stopped'

# 10. Prove the one-shot fallback with a stub shell. The stub prints one
#     fixture record and records its arguments. The stub credential is a
#     test value, not a real secret.
cat > "$one_shot_stub" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
dir="${STUB_DIR:?Set STUB_DIR}"
printf '%s\n' "$@" >> "$dir/args"
printf '{"id":"stub-1","topic":"lobby","sender":"Theta","timestamp":"2026-10-28T00:00:00Z","text":"stub record"}\n'
exit 0
STUB
chmod +x "$one_shot_stub"
printf 'fixture-password\n' > "$stub_secrets/Theta.password"
chmod 600 "$stub_secrets/Theta.password"
mkdir -p "$proof_root/stub-calls"
rm -f "$socket_file"
HOME="$home_dir" XDG_CONFIG_HOME="$config_home" \
    NODE9_ALLOW_SOURCE_TREE=1 NODE9_SECRETS_DIR="$stub_secrets" \
    STUB_DIR="$proof_root/stub-calls" CHAT_SHELL_CLI="$one_shot_stub" \
    "$register_cmd" read --as Theta --agent Theta --project demo-chat \
    > "$proof_root/fallback.out" 2> "$proof_root/fallback.err" \
    || fail "the fallback read failed. See $proof_root/fallback.err"
grep -q 'register: portal unavailable, using the one-shot login' \
    "$proof_root/fallback.err" \
    || fail 'the fallback read printed no notice'
[[ -f "$proof_root/stub-calls/args" ]] \
    || fail 'the fallback read called no shell'
ok 'fallback=ok'

# The proof root goes at exit, so print the ownership record for RECORD.md.
sed 's/^/ownership: /' "$proof_root/ownership.txt"
printf 'portal_read=ok portal_write=ok fallback=ok\n'
printf 'register=%s\n' "$register_name"
