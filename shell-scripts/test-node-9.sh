#!/usr/bin/env bash
# Offline checks for node-9. No container starts. See CHAT-hcfgkbuz.
#
# Each case reads the Compose file with `docker compose config`. The tests
# never run `docker compose up`, and they never touch node 16.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
node9_root="$repo_root/node-9"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fixture="$work/secrets.env"
cat > "$fixture" <<'EOF'
CHAT_SERVICE_PASSWORD=fixture-service-password
CHAT_ADMIN_PASSWORD=fixture-admin-password
CHAT_EMBEDDING_OPENAI_API_KEY=fixture-embedding-key
EOF

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

# node9_args TOGGLE
# Print node9_compose_args from the repository root.
node9_args() {
    local toggle="$1"
    ( cd "$repo_root" \
      && NODE9_VECTOR_PERSIST="$toggle" \
         bash -c 'source node-9/bin/lib.sh; node9_load_env; node9_compose_args' )
}

# node9_config TOGGLE SECRETS_FILE
# Print the rendered Compose model.
node9_config() {
    local toggle="$1" secrets="$2"
    ( cd "$repo_root" \
      && NODE9_VECTOR_PERSIST="$toggle" NODE9_SECRETS_FILE="$secrets" \
         bash -c 'source node-9/bin/lib.sh; node9_load_env; node9_compose config' )
}

# node9_config_json TOGGLE SECRETS_FILE
# Print the rendered Compose model as JSON.
node9_config_json() {
    local toggle="$1" secrets="$2"
    ( cd "$repo_root" \
      && NODE9_VECTOR_PERSIST="$toggle" NODE9_SECRETS_FILE="$secrets" \
         bash -c 'source node-9/bin/lib.sh; node9_load_env; node9_compose config --format json' )
}

# -- The toggle ---------------------------------------------------------

args_on="$(node9_args on 2>/dev/null)"
if grep -qx 'compose.yaml' <<< "$args_on" \
   && grep -qx 'compose.vector-disk.yaml' <<< "$args_on"; then
    ok 'toggle_on_adds_overlay'
else
    bad 'toggle_on_adds_overlay' "args: $(tr '\n' ' ' <<< "$args_on")"
fi

args_off="$(node9_args off 2>/dev/null)"
if grep -qx 'compose.yaml' <<< "$args_off" \
   && ! grep -qx 'compose.vector-disk.yaml' <<< "$args_off"; then
    ok 'toggle_off_omits_overlay'
else
    bad 'toggle_off_omits_overlay' "args: $(tr '\n' ' ' <<< "$args_off")"
fi

toggle_case='ok'
for value in ON '' 1; do
    out="$( ( cd "$repo_root" \
        && NODE9_VECTOR_PERSIST="$value" \
           bash -c 'source node-9/bin/lib.sh; node9_load_env' ) 2>&1 )"
    status=$?
    if [[ $status -ne 2 || "$out" != *"got '$value'"* ]]; then
        toggle_case="value '$value': exit $status, output: $out"
    fi
done
if [[ "$toggle_case" == 'ok' ]]; then
    ok 'toggle_rejects_other_values'
else
    bad 'toggle_rejects_other_values' "$toggle_case"
fi

# -- Isolation values ---------------------------------------------------

stub_dir="$work/stub"
mkdir -p "$stub_dir"
cat > "$stub_dir/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALLS"
exit 0
EOF
chmod +x "$stub_dir/docker"

calls="$work/project-calls"
out="$( cd "$repo_root" \
    && NODE9_PROJECT=chat-deploy-core NODE9_SECRETS_FILE="$fixture" \
       DOCKER_CALLS="$calls" PATH="$stub_dir:$PATH" \
       bash -c 'source node-9/bin/lib.sh && node9_load_env && node9_compose config' 2>&1 )"
status=$?
if [[ $status -eq 2 && "$out" == *"got 'chat-deploy-core'"* && ! -e "$calls" ]]; then
    ok 'project_override_is_rejected'
else
    bad 'project_override_is_rejected' "exit $status, output: $out"
fi

out="$( cd "$repo_root" \
    && NODE9_DIR=/tmp/not-node-9 \
       bash -c 'source node-9/bin/lib.sh; printf "%s\n" "$NODE9_DIR"' )"
if [[ "$out" == "$node9_root" ]]; then
    ok 'dir_override_is_ignored'
else
    bad 'dir_override_is_ignored' "dir: $out"
fi

ports_case='ok'
for assignment in NODE9_RSOCKET_PORT=6790 NODE9_MGMT_PORT=6791; do
    name="${assignment%%=*}"
    value="${assignment#*=}"
    out="$( cd "$repo_root" \
        && env "$assignment" \
           bash -c 'source node-9/bin/lib.sh && node9_load_env' 2>&1 )"
    status=$?
    if [[ $status -ne 2 || "$out" != *"$name"* || "$out" != *"got '$value'"* ]]; then
        ports_case="$assignment: exit $status, output: $out"
    fi
done
if [[ "$ports_case" == 'ok' ]]; then
    ok 'fixed_ports_are_rejected'
else
    bad 'fixed_ports_are_rejected' "$ports_case"
fi

calls="$work/image-calls"
out="$( cd "$repo_root" \
    && CORE_IMAGE=docker.io/library/cassandra-core-service-rsocket:0.0.1 \
       NODE9_SECRETS_FILE="$fixture" DOCKER_CALLS="$calls" \
       PATH="$stub_dir:$PATH" \
       bash -c 'source node-9/bin/lib.sh && node9_load_env && node9_compose config' 2>&1 )"
status=$?
if [[ $status -eq 2 && "$out" == *CORE_IMAGE* \
      && "$out" == *docker.io/library* && ! -e "$calls" ]]; then
    ok 'image_override_is_rejected'
else
    bad 'image_override_is_rejected' "exit $status, output: $out"
fi

# -- The Compose model --------------------------------------------------

on_text="$(node9_config on "$fixture" 2>&1)"
on_status=$?
off_text="$(node9_config off "$fixture" 2>&1)"
off_status=$?

if [[ $on_status -eq 0 ]] \
   && grep -q 'APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH: /var/lib/chat/vector' <<< "$on_text" \
   && grep -q "source: $node9_root/data/vector" <<< "$on_text"; then
    ok 'config_on_sets_vector_path'
else
    bad 'config_on_sets_vector_path' "exit $on_status"
fi

if [[ $off_status -eq 0 ]] \
   && ! grep -q 'APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH' <<< "$off_text" \
   && ! grep -q 'data/vector' <<< "$off_text"; then
    ok 'config_off_has_no_vector_path'
else
    bad 'config_off_has_no_vector_path' "exit $off_status"
fi

embedding_case='ok'
for pair in \
    'APP_SERVICE_CORE_EMBEDDING_OPENAI_BASE_URL: http://node2.lan:6090/v1' \
    'APP_SERVICE_CORE_EMBEDDING_OPENAI_MODEL: nomic-embed-text-v1.5.Q8_0.gguf' \
    'APP_SERVICE_CORE_EMBEDDING_IDENTITY: nomic-embed-text-v1-5-768' \
    'APP_SERVICE_CORE_EMBEDDING_OPENAI_API_KEY: fixture-embedding-key'; do
    if ! grep -qF -- "$pair" <<< "$off_text"; then
        embedding_case="missing: $pair"
    fi
done
if grep -q 'CHAT_EMBEDDING_OPENAI_API_KEY' "$node9_root/node-9.env" 2>/dev/null; then
    embedding_case='node-9.env holds an API key'
fi

on_json="$(node9_config_json on "$fixture" 2>/dev/null)"
off_json="$(node9_config_json off "$fixture" 2>/dev/null)"
difference="$(diff \
    <(jq -S '
        del(.services["vector-volume-init"])
        | del(.services["core-service-rsocket"].depends_on["vector-volume-init"])
        | del(.services["core-service-rsocket"].environment["APP_SERVICE_CORE_VECTOR_EMBEDDED_PATH"])
        | .services["core-service-rsocket"].volumes
            |= map(select(.target != "/var/lib/chat/vector"))
    ' <<< "$on_json") \
    <(jq -S '.' <<< "$off_json") 2>&1)"
if [[ "$embedding_case" == 'ok' && -z "$difference" ]]; then
    ok 'config_off_keeps_embedding_settings'
else
    bad 'config_off_keeps_embedding_settings' "${embedding_case} ${difference}"
fi

if grep -q 'CHAT_ADMIN_PASSWORD: fixture-admin-password' <<< "$off_text"; then
    ok 'compose_passes_admin_password'
else
    bad 'compose_passes_admin_password' 'the core environment lacks the Admin password'
fi

no_key="$work/secrets-no-key.env"
grep -v '^CHAT_EMBEDDING_OPENAI_API_KEY=' "$fixture" > "$no_key"
missing_text="$(node9_config off "$no_key" 2>&1)"
missing_status=$?
if [[ $on_status -eq 0 && $missing_status -ne 0 \
      && "$missing_text" == *CHAT_EMBEDDING_OPENAI_API_KEY* ]]; then
    ok 'config_core_has_no_unset_variable'
else
    bad 'config_core_has_no_unset_variable' "missing-key exit $missing_status"
fi

mount_case='ok'
if [[ -d "$node9_root" ]]; then
    while IFS= read -r source; do
        [[ -z "$source" ]] && continue
        if [[ "$source" != "$node9_root"/* ]]; then
            mount_case="mount is not under node-9: $source"
        fi
    done < <(sed -n 's/^[[:space:]]*source:[[:space:]]*//p' <<< "$on_text")
    if grep -rqE '/Users|/home' "$node9_root"; then
        mount_case='node-9/ holds an absolute home path'
    fi
else
    mount_case='node-9/ is missing'
fi
if [[ "$mount_case" == 'ok' ]]; then
    ok 'no_absolute_host_mount'
else
    bad 'no_absolute_host_mount' "$mount_case"
fi

names_case='ok'
if grep -q 'chat-deploy-core' <<< "$on_text"; then
    names_case='the output names chat-deploy-core'
elif grep -q 'docker_chatnet' <<< "$on_text"; then
    names_case='the output names docker_chatnet'
elif grep -qE '^[[:space:]]*image:[[:space:]]*docker\.io/library/' <<< "$on_text"; then
    names_case='an image uses the docker.io/library prefix'
elif ! grep -q 'image: node-9/cassandra-core-service-rsocket:0.0.1' <<< "$on_text"; then
    names_case='the core image is not node-9/cassandra-core-service-rsocket:0.0.1'
fi
if [[ "$names_case" == 'ok' ]]; then
    ok 'no_node16_names'
else
    bad 'no_node16_names' "$names_case"
fi

published="$(sed -n 's/.*published:[[:space:]]*//p' <<< "$on_text" | tr -d '"')"
host_ips="$(sed -n 's/.*host_ip:[[:space:]]*//p' <<< "$on_text" | tr -d '"' | sort -u)"
port_case='ok'
if [[ "$(sort -u <<< "$published" | tr '\n' ' ')" != '6890 6891 ' ]]; then
    port_case="published ports: $(tr '\n' ' ' <<< "$published")"
elif [[ "$host_ips" != '127.0.0.1' ]]; then
    port_case="host IPs: $(tr '\n' ' ' <<< "$host_ips")"
fi
if [[ "$port_case" == 'ok' ]]; then
    ok 'ports_are_loopback_only'
else
    bad 'ports_are_loopback_only' "$port_case"
fi

# -- Secrets ------------------------------------------------------------

generated="$work/generated-secrets.env"
secrets_out="$( cd "$repo_root" \
    && NODE9_SECRETS_FILE="$generated" \
       bash node-9/bin/setup-secrets 2>&1 )"
secrets_status=$?
secrets_mode="$(stat -c '%a' "$generated" 2>/dev/null \
    || stat -f '%Lp' "$generated" 2>/dev/null)"
if [[ $secrets_status -eq 0 && "$secrets_mode" == '600' ]]; then
    ok 'secrets_created_with_mode_0600'
else
    bad 'secrets_created_with_mode_0600' "exit $secrets_status, mode $secrets_mode"
fi

service_pw="$(sed -n 's/^CHAT_SERVICE_PASSWORD=//p' "$generated" 2>/dev/null)"
admin_pw="$(sed -n 's/^CHAT_ADMIN_PASSWORD=//p' "$generated" 2>/dev/null)"
if [[ "$service_pw" =~ ^[0-9a-f]{32}$ && "$admin_pw" =~ ^[0-9a-f]{32}$ \
      && "$service_pw" != "$admin_pw" ]]; then
    ok 'secrets_values_are_random_and_distinct'
else
    bad 'secrets_values_are_random_and_distinct' 'the password values are not distinct hex values'
fi

before="$(cksum < "$generated" 2>/dev/null)"
( cd "$repo_root" && NODE9_SECRETS_FILE="$generated" \
    bash node-9/bin/setup-secrets ) >/dev/null 2>&1
after="$(cksum < "$generated" 2>/dev/null)"
if [[ -n "$before" && "$before" == "$after" ]]; then
    ok 'secrets_not_replaced'
else
    bad 'secrets_not_replaced' 'the second run changed the file'
fi

if [[ -n "$service_pw" && -n "$admin_pw" \
      && "$secrets_out" != *"$service_pw"* && "$secrets_out" != *"$admin_pw"* ]]; then
    ok 'secrets_never_printed'
else
    bad 'secrets_never_printed' 'the output holds a password'
fi

userinit="$node9_root/userinit.yml"
userinit_case='ok'
if [[ ! -f "$userinit" ]]; then
    userinit_case='userinit.yml is missing'
elif [[ "$(grep -c 'password:' "$userinit")" != '1' ]]; then
    userinit_case='userinit.yml holds more than one password key'
elif ! grep -qF 'password: ${CHAT_ADMIN_PASSWORD}' "$userinit"; then
    userinit_case='the Admin password is not the placeholder'
elif grep -qi 'changeme' "$userinit"; then
    userinit_case='userinit.yml holds the literal changeme'
fi
if [[ "$userinit_case" == 'ok' ]]; then
    ok 'userinit_overrides_only_admin_password'
else
    bad 'userinit_overrides_only_admin_password' "$userinit_case"
fi

# -- The schema step ----------------------------------------------------

mkdir -p "$work/bin"
printf '#!/usr/bin/env bash\nexit 0\n' > "$work/bin/sleep"
chmod +x "$work/bin/sleep"

cat > "$work/cqlsh" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CQLSH_CALLS"
case "$*" in
    *'DESCRIBE KEYSPACES'*)
        n="$(cat "$DESCRIBE_COUNT" 2>/dev/null || printf '0')"
        n=$((n + 1))
        printf '%s' "$n" > "$DESCRIBE_COUNT"
        if ((n <= ${DESCRIBE_FAILS:-0})); then
            printf 'Connection error: could not connect\n' >&2
            exit 1
        fi
        exit 0
        ;;
    *'-f /schema/keyspace-uuid.cql'*)
        if [[ -n "${APPLY_STATE:-}" ]]; then
            printf 'present' > "$APPLY_STATE"
        fi
        exit 0
        ;;
    *system_schema.keyspaces*)
        if [[ "${QUERY_FAILS:-0}" == '1' ]]; then
            printf 'Connection error: could not connect\n' >&2
            exit 1
        fi
        if [[ "${QUERY_UNEXPECTED:-0}" == '1' ]]; then
            printf 'unexpected output\n'
            exit 0
        fi
        if [[ -n "${APPLY_STATE:-}" && -f "$APPLY_STATE" \
              && "${VERIFY_STAYS_ABSENT:-0}" != '1' ]]; then
            printf '(1 rows)\n'
        else
            printf '(0 rows)\n'
        fi
        exit 0
        ;;
esac
exit 0
STUB
chmod +x "$work/cqlsh"

reset_schema() {
    rm -f "$work/calls" "$work/describe-count"
}

run_schema() {
    local state="$1"
    shift
    ( cd "$repo_root" \
      && env PATH="$work/bin:$PATH" \
             CQLSH_STUB="$work/cqlsh" \
             CQLSH_CALLS="$work/calls" \
             DESCRIBE_COUNT="$work/describe-count" \
             APPLY_STATE="$state" \
             "$@" \
             bash -c 'source node-9/bin/lib.sh
                 node9_cqlsh() { "$CQLSH_STUB" "$@"; }
                 node9_apply_schema' )
}

apply_calls() {
    grep -c -- '-f /schema/keyspace-uuid.cql' "$work/calls" 2>/dev/null || true
}

describe_calls() {
    grep -c 'DESCRIBE KEYSPACES' "$work/calls" 2>/dev/null || true
}

rm -f "$work/state-applied"
reset_schema
out="$(run_schema "$work/state-applied" 2>&1)"
status=$?
if [[ $status -eq 0 && "$(apply_calls)" == '1' && -f "$work/state-applied" ]]; then
    ok 'schema_applied_when_absent'
else
    bad 'schema_applied_when_absent' "exit $status, applies $(apply_calls)"
fi

printf 'present' > "$work/state-present"
reset_schema
out="$(run_schema "$work/state-present" 2>&1)"
status=$?
if [[ $status -eq 0 && "$(apply_calls)" == '0' ]]; then
    ok 'schema_not_applied_when_present'
else
    bad 'schema_not_applied_when_present' "exit $status, applies $(apply_calls)"
fi

reset_schema
out="$(run_schema "$work/state-error" QUERY_FAILS=1 2>&1)"
status=$?
if [[ $status -ne 0 && "$(apply_calls)" == '0' ]]; then
    ok 'schema_not_applied_on_connection_error'
else
    bad 'schema_not_applied_on_connection_error' "exit $status, applies $(apply_calls)"
fi

reset_schema
out="$(run_schema "$work/state-odd" QUERY_UNEXPECTED=1 2>&1)"
status=$?
if [[ $status -ne 0 && "$(apply_calls)" == '0' ]]; then
    ok 'schema_not_applied_on_unexpected_output'
else
    bad 'schema_not_applied_on_unexpected_output' "exit $status, applies $(apply_calls)"
fi

rm -f "$work/state-verify"
reset_schema
out="$(run_schema "$work/state-verify" VERIFY_STAYS_ABSENT=1 2>&1)"
status=$?
if [[ $status -ne 0 && "$(apply_calls)" == '1' ]]; then
    ok 'schema_fails_when_verify_fails'
else
    bad 'schema_fails_when_verify_fails' "exit $status, applies $(apply_calls)"
fi

rm -f "$work/state-waits"
reset_schema
out="$(run_schema "$work/state-waits" DESCRIBE_FAILS=3 2>&1)"
status=$?
if [[ $status -eq 0 && "$(describe_calls)" == '4' && "$(apply_calls)" == '1' ]]; then
    ok 'schema_waits_for_cassandra'
else
    bad 'schema_waits_for_cassandra' "exit $status, describes $(describe_calls), applies $(apply_calls)"
fi

reset_schema
out="$(run_schema "$work/state-gives-up" DESCRIBE_FAILS=999 2>&1)"
status=$?
if [[ $status -ne 0 && "$(describe_calls)" == '180' && "$(apply_calls)" == '0' ]]; then
    ok 'schema_gives_up'
else
    bad 'schema_gives_up' "exit $status, describes $(describe_calls), applies $(apply_calls)"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all node-9 cases passed'
