#!/usr/bin/env bash
# Offline checks for node-9. No container starts. See CHAT-hcfgkbuz.
#
# Each case reads the Compose file with `docker compose config`. The tests
# never run `docker compose up`, and they never touch node 16.
set -uo pipefail

# The sandbox copies are source trees. They run on purpose.
export NODE9_ALLOW_SOURCE_TREE=1

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
CHAT_USER_SIGMA_PASSWORD=fixture-sigma-password
CHAT_USER_THETA_PASSWORD=fixture-theta-password
CHAT_USER_PHI_PASSWORD=fixture-phi-password
CHAT_USER_MARIO_PASSWORD=fixture-mario-password
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

user_case='ok'
for pair in SIGMA:fixture-sigma-password THETA:fixture-theta-password \
            PHI:fixture-phi-password MARIO:fixture-mario-password; do
    name="${pair%%:*}"
    value="${pair#*:}"
    if ! grep -q "CHAT_USER_${name}_PASSWORD: ${value}" <<< "$off_text"; then
        user_case="the core environment lacks CHAT_USER_${name}_PASSWORD"
    fi
done
if [[ "$user_case" == 'ok' ]]; then
    ok 'compose_passes_user_passwords'
else
    bad 'compose_passes_user_passwords' "$user_case"
fi

no_mario="$work/secrets-no-mario.env"
grep -v '^CHAT_USER_MARIO_PASSWORD=' "$fixture" > "$no_mario"
mario_text="$(node9_config off "$no_mario" 2>&1)"
mario_status=$?
if [[ "$mario_status" -ne 0 && "$mario_text" == *'CHAT_USER_MARIO_PASSWORD'* ]]; then
    ok 'compose_requires_mario_password'
else
    bad 'compose_requires_mario_password' "exit $mario_status"
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
    if [[ -n "$(find "$node9_root" \
        \( -path "$node9_root/data" -o -path "$node9_root/images" \
           -o -path "$node9_root/secrets" -o -path "$node9_root/run" \) -prune \
        -o -type f -exec grep -lE '/Users|/home' {} + 2>/dev/null)" ]]; then
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
generated_dir="$work/generated-secrets"
generated_config="$work/generated-config"
mkdir -p "$generated_config"
printf 'CHAT_USER_MARIO_PASSWORD=fixture-mario-password\n' > "$generated"
secrets_out="$( cd "$repo_root" \
    && XDG_CONFIG_HOME="$generated_config" \
       NODE9_SECRETS_FILE="$generated" NODE9_SECRETS_DIR="$generated_dir" \
       bash node-9/bin/setup-secrets --local 2>&1 )"
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
( cd "$repo_root" && XDG_CONFIG_HOME="$generated_config" \
    NODE9_SECRETS_FILE="$generated" NODE9_SECRETS_DIR="$generated_dir" \
    bash node-9/bin/setup-secrets --local ) >/dev/null 2>&1
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
elif [[ "$(grep -c 'password:' "$userinit")" != '5' ]]; then
    userinit_case='userinit.yml does not hold five password keys'
elif ! grep -qF 'password: ${CHAT_ADMIN_PASSWORD}' "$userinit"; then
    userinit_case='the Admin password is not the placeholder'
elif grep -qi 'changeme' "$userinit"; then
    userinit_case='userinit.yml holds the literal changeme'
fi
for user in SIGMA THETA PHI MARIO; do
    if ! grep -qF "password: \${CHAT_USER_${user}_PASSWORD}" "$userinit"; then
        userinit_case="the ${user} password is not the placeholder"
    fi
done
if [[ "$userinit_case" == 'ok' ]]; then
    ok 'userinit_has_four_users'
else
    bad 'userinit_has_four_users' "$userinit_case"
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

# -- Start and stop -----------------------------------------------------

sandbox="$work/node-9-sandbox"
mkdir -p "$sandbox/bin"
cp node-9/node-9.env node-9/compose.yaml node-9/compose.vector-disk.yaml \
    node-9/userinit.yml "$sandbox/" 2>/dev/null
cp -R node-9/schema "$sandbox/" 2>/dev/null
cp node-9/bin/lib.sh node-9/bin/setup-secrets node-9/bin/start \
    node-9/bin/stop node-9/bin/save-images node-9/bin/load-images \
    "$sandbox/bin/" 2>/dev/null

cat > "$work/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALLS"
if [[ "$1" == 'save' && "$2" == '--output' ]]; then
    : > "$3"
    exit 0
fi
case "$*" in
    'image inspect '*)
        if [[ "${IMAGE_ABSENT:-0}" == '1' ]]; then exit 1; fi
        exit 0
        ;;
    *'exec -T cassandra cqlsh -e DESCRIBE KEYSPACES'*)
        exit 0
        ;;
    *'exec -T cassandra cqlsh -f /schema/keyspace-uuid.cql'*)
        if [[ -n "${SCHEMA_STATE:-}" ]]; then printf 'present' > "$SCHEMA_STATE"; fi
        exit 0
        ;;
    *'exec -T cassandra cqlsh -e SELECT'*)
        if [[ "${KEYS_PRESENT:-0}" == '1' \
              || ( -n "${SCHEMA_STATE:-}" && -f "$SCHEMA_STATE" ) ]]; then
            printf '(1 rows)\n'
        else
            printf '(0 rows)\n'
        fi
        exit 0
        ;;
esac
exit 0
STUB

cat > "$work/bin/mvn" <<'STUB'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MVN_CALLS"
case "$*" in
    *'help:evaluate'*)
        printf '%s\n' "${MVN_IMAGE:-node-9/cassandra-core-service-rsocket:0.0.1}"
        ;;
esac
exit 0
STUB

printf '#!/usr/bin/env bash\nexit 0\n' > "$work/bin/curl"
chmod +x "$work/bin/docker" "$work/bin/mvn" "$work/bin/curl"

stub_env=(PATH="$work/bin:$PATH" DOCKER_CALLS="$work/start-calls"
    MVN_CALLS="$work/start-mvn" SCHEMA_STATE="$work/start-state")

reset_start() {
    rm -f "$work/start-calls" "$work/start-mvn" "$work/start-state"
}

reset_start
out="$(env "${stub_env[@]}" bash "$sandbox/bin/start" --dry-run 2>&1)"
status=$?
names_case='ok'
for pattern in '-p chat-node-9' '--node-id 9' 'IMAGE_REPO_PREFIX=node-9'; do
    if [[ "$out" != *"$pattern"* ]]; then
        names_case="missing: $pattern"
    fi
done
if [[ "$out" == *'chat-deploy-core'* ]]; then
    names_case='the output names chat-deploy-core'
fi
if [[ $status -eq 0 && "$names_case" == 'ok' ]]; then
    ok 'start_dry_run_uses_node9_names'
else
    bad 'start_dry_run_uses_node9_names' "exit $status, ${names_case}"
fi

build_case='ok'
for pattern in '--notls' 'CONSUL_HOST=consul' 'CONSUL_PORT=8500' \
    'CASSANDRA_CONTACT_POINTS=cassandra'; do
    if [[ "$out" != *"$pattern"* ]]; then
        build_case="missing: $pattern"
    fi
done
if [[ "$build_case" == 'ok' ]]; then
    ok 'build_command_is_explicit'
else
    bad 'build_command_is_explicit' "$build_case"
fi

printf 'CHAT_SERVICE_PASSWORD=start-service\nCHAT_ADMIN_PASSWORD=start-admin\nCHAT_EMBEDDING_OPENAI_API_KEY=start-embed\nCHAT_USER_SIGMA_PASSWORD=start-sigma\nCHAT_USER_THETA_PASSWORD=start-theta\nCHAT_USER_PHI_PASSWORD=start-phi\nCHAT_USER_MARIO_PASSWORD=start-mario\n' \
    > "$sandbox/secrets.env"

# A start with no secrets file and no Vault password stops. It does not
# generate values on its own.
mkdir -p "$work/empty-config"
mv "$sandbox/secrets.env" "$sandbox/secrets.env.saved"
reset_start
env "${stub_env[@]}" XDG_CONFIG_HOME="$work/empty-config" \
    bash "$sandbox/bin/start" >/dev/null 2>&1
status=$?
if [[ $status -eq 2 && ! -e "$sandbox/secrets.env" ]]; then
    ok 'start_without_vault_stops'
else
    bad 'start_without_vault_stops' "exit $status, secrets file $([ -e "$sandbox/secrets.env" ] && echo present || echo absent)"
fi
mv "$sandbox/secrets.env.saved" "$sandbox/secrets.env"

reset_start
rm -rf "$sandbox/data"
env "${stub_env[@]}" KEYS_PRESENT=0 bash "$sandbox/bin/start" >/dev/null 2>&1
cassandra_line="$(grep -n 'up -d cassandra redis consul' "$work/start-calls" 2>/dev/null | head -n 1 | cut -d: -f1)"
schema_line="$(grep -n -- '-f /schema/keyspace-uuid.cql' "$work/start-calls" 2>/dev/null | head -n 1 | cut -d: -f1)"
core_line="$(grep -n 'up -d core-service-rsocket' "$work/start-calls" 2>/dev/null | head -n 1 | cut -d: -f1)"
if [[ -n "$cassandra_line" && -n "$schema_line" && -n "$core_line" \
      && "$cassandra_line" -lt "$schema_line" && "$schema_line" -lt "$core_line" ]]; then
    ok 'start_order'
else
    bad 'start_order' "cassandra=$cassandra_line schema=$schema_line core=$core_line"
fi

reset_start
env "${stub_env[@]}" KEYS_PRESENT=1 bash "$sandbox/bin/start" >/dev/null 2>&1
reset_start
env "${stub_env[@]}" KEYS_PRESENT=1 bash "$sandbox/bin/start" >/dev/null 2>&1
if [[ "$(grep -c -- '-f /schema/keyspace-uuid.cql' "$work/start-calls" 2>/dev/null)" == '0' ]]; then
    ok 'second_start_keeps_data'
else
    bad 'second_start_keeps_data' 'the second start applied the schema'
fi

printf 'CHAT_SERVICE_PASSWORD=dry-service-secret\nCHAT_ADMIN_PASSWORD=dry-admin-secret\nCHAT_EMBEDDING_OPENAI_API_KEY=dry-embed-secret\nCHAT_USER_SIGMA_PASSWORD=dry-sigma\nCHAT_USER_THETA_PASSWORD=dry-theta\nCHAT_USER_PHI_PASSWORD=dry-phi\nCHAT_USER_MARIO_PASSWORD=dry-mario\n' \
    > "$sandbox/secrets.env"
reset_start
out="$(env "${stub_env[@]}" bash "$sandbox/bin/start" --dry-run 2>&1)"
status=$?
if [[ $status -eq 0 && "$out" != *'dry-service-secret'* \
      && "$out" != *'dry-admin-secret'* && "$out" != *'dry-embed-secret'* ]]; then
    ok 'dry_run_prints_no_secret'
else
    bad 'dry_run_prints_no_secret' "exit $status, the output holds a secret value"
fi

before_ps="$(docker ps -a --format '{{.ID}} {{.Names}} {{.Image}}' 2>/dev/null | sort)"
before_images="$(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' 2>/dev/null | sort)"
reset_start
env "${stub_env[@]}" bash "$sandbox/bin/start" --dry-run >/dev/null 2>&1
after_ps="$(docker ps -a --format '{{.ID}} {{.Names}} {{.Image}}' 2>/dev/null | sort)"
after_images="$(docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' 2>/dev/null | sort)"
if [[ "$before_ps" == "$after_ps" && "$before_images" == "$after_images" ]]; then
    ok 'start_dry_run_changes_nothing'
else
    bad 'start_dry_run_changes_nothing' 'the Docker listings changed'
fi

rm -rf "$sandbox/data"
reset_start
env "${stub_env[@]}" NODE9_VECTOR_PERSIST=off \
    bash "$sandbox/bin/start" --dry-run >/dev/null 2>&1
if [[ ! -d "$sandbox/data/vector" && -d "$sandbox/data/cassandra" ]]; then
    ok 'start_dry_run_off_has_no_vector_dir'
else
    bad 'start_dry_run_off_has_no_vector_dir' 'the vector directory state is wrong'
fi

# GNU netcat exits after one connection, so do not probe the listener.
nc -l -p 6890 >/dev/null 2>&1 &
listener=$!
/bin/sleep 0.5
reset_start
env "${stub_env[@]}" bash "$sandbox/bin/start" --dry-run >/dev/null 2>&1
status=$?
kill "$listener" >/dev/null 2>&1
wait "$listener" 2>/dev/null
if [[ $status -eq 3 && ! -e "$work/start-calls" ]]; then
    ok 'start_refuses_busy_port'
else
    bad 'start_refuses_busy_port' "exit $status, docker calls $([ -e "$work/start-calls" ] && echo yes || echo no)"
fi

printf 'CHAT_SERVICE_PASSWORD=x\nCHAT_ADMIN_PASSWORD=y\nCHAT_EMBEDDING_OPENAI_API_KEY=z\nCHAT_USER_SIGMA_PASSWORD=a\nCHAT_USER_THETA_PASSWORD=b\nCHAT_USER_PHI_PASSWORD=c\nCHAT_USER_MARIO_PASSWORD=d\n' \
    > "$sandbox/secrets.env"
reset_start
env "${stub_env[@]}" bash "$sandbox/bin/stop" >/dev/null 2>&1
status=$?
if [[ $status -eq 0 && -s "$work/start-calls" \
      && "$(grep -vc -- '-p chat-node-9' "$work/start-calls")" == '0' \
      && "$(grep -c -- '--timeout 60' "$work/start-calls")" == '1' ]]; then
    ok 'stop_names_only_project_node9'
else
    bad 'stop_names_only_project_node9' "exit $status"
fi

reset_start
out="$(env "${stub_env[@]}" IMAGE_ABSENT=1 \
    MVN_IMAGE='node-9/cassandra-core-service-rsocket:0.0.1' \
    bash "$sandbox/bin/start" --dry-run 2>&1)"
good_status=$?
good_output="$out"
reset_start
out="$(env "${stub_env[@]}" IMAGE_ABSENT=1 \
    MVN_IMAGE='docker.io/library/cassandra-core-service-rsocket:0.0.1' \
    bash "$sandbox/bin/start" --dry-run 2>&1)"
bad_status=$?
if [[ $good_status -eq 0 \
      && "$good_output" == *'image: node-9/cassandra-core-service-rsocket:0.0.1'* \
      && $bad_status -eq 4 && "$out" != *'shell-scripts/chat-build'* ]]; then
    ok 'image_reference_differs_from_node16'
else
    bad 'image_reference_differs_from_node16' "good=$good_status bad=$bad_status"
fi

# A loaded image makes Maven unnecessary. The destination needs Docker alone.
reset_start
out="$(env "${stub_env[@]}" bash "$sandbox/bin/start" --dry-run 2>&1)"
status=$?
if [[ $status -eq 0 && ! -e "$work/start-mvn" \
      && "$out" == *'image: node-9/cassandra-core-service-rsocket:0.0.1'* ]]; then
    ok 'start_with_a_loaded_image_calls_no_maven'
else
    bad 'start_with_a_loaded_image_calls_no_maven' \
        "exit $status, maven calls $([ -e "$work/start-mvn" ] && echo yes || echo no)"
fi

# -- Save and load images ----------------------------------------------

save_calls="$work/save-calls"
rm -rf "$sandbox/images"
rm -f "$save_calls"
env PATH="$work/bin:$PATH" DOCKER_CALLS="$save_calls" \
    bash "$sandbox/bin/save-images" >/dev/null 2>&1
save_status=$?
save_count="$(grep -c '^save --output ' "$save_calls" 2>/dev/null)"
tar_count="$(ls "$sandbox/images"/*.tar 2>/dev/null | wc -l | tr -d ' ')"
if [[ $save_status -eq 0 && "$save_count" == '5' && "$tar_count" == '5' ]]; then
    ok 'save_images_names_five_images'
else
    bad 'save_images_names_five_images' \
        "exit $save_status, saves $save_count, tars $tar_count"
fi

image_list="$( cd "$repo_root" \
    && bash -c 'source node-9/bin/lib.sh; node9_load_env; node9_images' 2>&1 )"
list_status=$?
if [[ $list_status -eq 0 && "$(grep -c ':latest' <<< "$image_list")" == '0' \
      && "$(sort -u <<< "$image_list" | wc -l | tr -d ' ')" == '5' ]]; then
    ok 'image_list_has_no_latest_tag'
else
    bad 'image_list_has_no_latest_tag' "exit $list_status, list: $image_list"
fi

rm -f "$sandbox/images/redis-7.tar"
rm -f "$save_calls"
out="$(env PATH="$work/bin:$PATH" DOCKER_CALLS="$save_calls" \
    bash "$sandbox/bin/load-images" 2>&1)"
load_status=$?
if [[ $load_status -ne 0 && "$out" == *'redis-7.tar'* ]]; then
    ok 'load_images_fails_on_missing_tar'
else
    bad 'load_images_fails_on_missing_tar' "exit $load_status, output: $out"
fi

# -- Install outside the source tree -------------------------------------

install_home="$work/opt/node-9"
mkdir -p "$work/opt"
out="$(bash node-9/install --prefix "$install_home" 2>&1)"
status=$?
if [[ $status -eq 0 && -f "$install_home/.installed" \
      && -x "$install_home/bin/start" && -d "$install_home/data" \
      && "$(stat -f %Lp "$install_home/secrets" 2>/dev/null || stat -c %a "$install_home/secrets")" == '700' \
      && ! -e "$install_home/install" ]]; then
    ok 'install_copies_code_and_creates_state_dirs'
else
    bad 'install_copies_code_and_creates_state_dirs' "exit $status, output: $out"
fi

printf 'keep\n' > "$install_home/secrets.env"
printf 'keep\n' > "$install_home/data/marker"
printf 'stale\n' > "$install_home/bin/old-script"
out="$(bash node-9/install --prefix "$install_home" 2>&1)"
status=$?
if [[ $status -eq 0 && "$(cat "$install_home/secrets.env")" == 'keep' \
      && "$(cat "$install_home/data/marker")" == 'keep' \
      && ! -e "$install_home/bin/old-script" ]]; then
    ok 'install_rerun_keeps_state_and_replaces_code'
else
    bad 'install_rerun_keeps_state_and_replaces_code' "exit $status, output: $out"
fi

out="$(bash node-9/install --prefix "$repo_root/node-9-home-test" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'git work tree'* \
      && ! -e "$repo_root/node-9-home-test" ]]; then
    ok 'install_refuses_a_prefix_inside_a_work_tree'
else
    bad 'install_refuses_a_prefix_inside_a_work_tree' "exit $status, output: $out"
fi

mkdir -p "$work/foreign"
printf 'x\n' > "$work/foreign/file"
out="$(bash node-9/install --prefix "$work/foreign" 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'no .installed marker'* ]]; then
    ok 'install_refuses_a_foreign_directory'
else
    bad 'install_refuses_a_foreign_directory' "exit $status, output: $out"
fi

# Import: a complete set copies. An incomplete set is skipped. No overwrite.
good_state="$work/good-state"
mkdir -p "$good_state/secrets"
cp "$fixture" "$good_state/secrets.env"
for name in Sigma Theta Phi; do printf 'pw-%s\n' "$name" > "$good_state/secrets/$name.password"; done
bad_state="$work/bad-state"
mkdir -p "$bad_state/secrets"
grep -v THETA "$fixture" > "$bad_state/secrets.env"

home_a="$work/opt/import-a"
out="$(bash node-9/install --prefix "$home_a" --import-state "$good_state" 2>&1)"
status=$?
if [[ $status -eq 0 && -f "$home_a/secrets/Theta.password" \
      && "$(stat -f %Lp "$home_a/secrets.env" 2>/dev/null || stat -c %a "$home_a/secrets.env")" == '600' ]]; then
    ok 'install_imports_a_complete_secret_set'
else
    bad 'install_imports_a_complete_secret_set' "exit $status, output: $out"
fi

printf 'changed\n' > "$home_a/secrets.env"
bash node-9/install --prefix "$home_a" --import-state "$good_state" >/dev/null 2>&1
if [[ "$(cat "$home_a/secrets.env")" == 'changed' ]]; then
    ok 'install_import_never_overwrites'
else
    bad 'install_import_never_overwrites' 'secrets.env changed'
fi

home_b="$work/opt/import-b"
out="$(bash node-9/install --prefix "$home_b" --import-state "$bad_state" 2>&1)"
status=$?
if [[ $status -eq 0 && ! -e "$home_b/secrets.env" && "$out" == *'import skipped'* ]]; then
    ok 'install_skips_an_incomplete_secret_set'
else
    bad 'install_skips_an_incomplete_secret_set' "exit $status, output: $out"
fi

# An installed start with no image stops before Maven. A register with no
# CLI stops with one line.
reset_start
rm -f "$work/start-mvn"
out="$(env "${stub_env[@]}" IMAGE_ABSENT=1 bash "$install_home/bin/start" --dry-run 2>&1)"
status=$?
if [[ $status -eq 4 && "$out" == *'load-images'* && ! -e "$work/start-mvn" ]]; then
    ok 'installed_start_without_image_calls_no_maven'
else
    bad 'installed_start_without_image_calls_no_maven' "exit $status, output: $out"
fi

out="$(bash "$install_home/bin/register" read --as Theta 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'Set CHAT_SHELL_CLI'* ]]; then
    ok 'installed_register_needs_a_cli'
else
    bad 'installed_register_needs_a_cli' "exit $status, output: $out"
fi

# -- The source-tree guard ----------------------------------------------

cp node-9/bin/register "$sandbox/bin/"
guard_ok=true
for script in start stop setup-secrets register load-images save-images; do
    out="$(env -u NODE9_ALLOW_SOURCE_TREE bash "$sandbox/bin/$script" 2>&1)"
    status=$?
    if [[ $status -ne 2 || "$out" != *'source tree'* \
          || "$out" != *"/opt/demo-chat/node-9/bin/$script"* ]]; then
        guard_ok=false
        bad "guard_refuses_$script" "exit $status, output: $out"
    fi
done
if [[ "$guard_ok" == 'true' ]]; then
    ok 'guard_refuses_every_script_in_a_source_tree'
fi

out="$(env -u NODE9_ALLOW_SOURCE_TREE bash "$install_home/bin/start" --dry-run 2>&1)"
if [[ "$out" != *'source tree'* ]]; then
    ok 'guard_allows_an_installed_copy'
else
    bad 'guard_allows_an_installed_copy' "output: $out"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all node-9 cases passed'
