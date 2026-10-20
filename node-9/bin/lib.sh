# Shared helpers for node-9. Source this file. See CHAT-hcfgkbuz.
#
# NODE9_DIR comes from this file, not from the working directory.
# A caller cannot override it. The directory fixes the Compose files and
# the mounts, so an override could redirect node 9 at another project.
NODE9_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# node9_check_toggle VALUE
# Status 0 for on or off. Otherwise print the value and return 2.
node9_check_toggle() {
    case "${1:-}" in
        on|off)
            return 0
            ;;
        *)
            printf "node-9: NODE9_VECTOR_PERSIST must be on or off, got '%s'\n" "${1:-}" >&2
            return 2
            ;;
    esac
}

# node9_check_isolation
# Reject a caller override of a fixed isolation value. Node 9 must never
# reach the node-16 Compose project, image, or host ports.
node9_check_isolation() {
    if [[ "${NODE9_PROJECT:-}" != 'chat-node-9' ]]; then
        printf "node-9: NODE9_PROJECT must be chat-node-9, got '%s'\n" "${NODE9_PROJECT:-}" >&2
        return 2
    fi
    if [[ "${NODE9_RSOCKET_PORT:-}" != '6890' ]]; then
        printf "node-9: NODE9_RSOCKET_PORT must be 6890, got '%s'\n" "${NODE9_RSOCKET_PORT:-}" >&2
        return 2
    fi
    if [[ "${NODE9_MGMT_PORT:-}" != '6891' ]]; then
        printf "node-9: NODE9_MGMT_PORT must be 6891, got '%s'\n" "${NODE9_MGMT_PORT:-}" >&2
        return 2
    fi
    case "${CORE_IMAGE:-}" in
        node-9/cassandra-core-service-rsocket:*)
            ;;
        *)
            printf "node-9: CORE_IMAGE must be node-9/cassandra-core-service-rsocket:<version>, got '%s'\n" "${CORE_IMAGE:-}" >&2
            return 2
            ;;
    esac
}

# node9_load_env
# Set the variables of node-9.env that the caller does not set. Then check
# the toggle and the fixed isolation values.
node9_load_env() {
    local file="$NODE9_DIR/node-9.env"
    if [[ ! -f "$file" ]]; then
        printf 'node-9: missing %s\n' "$file" >&2
        return 2
    fi
    local line key value
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "$line" || "$line" == \#* ]] && continue
        key="${line%%=*}"
        value="${line#*=}"
        if ! printenv "$key" >/dev/null 2>&1; then
            export "$key=$value"
        fi
    done < "$file"
    node9_check_toggle "${NODE9_VECTOR_PERSIST:-}" || return 2
    node9_check_isolation
}

# node9_compose_args
# Print one argument per line. The base file comes first. The overlay comes
# next only when the toggle is on.
node9_compose_args() {
    node9_check_toggle "${NODE9_VECTOR_PERSIST:-}" || return 2
    printf '%s\n' -f compose.yaml
    if [[ "${NODE9_VECTOR_PERSIST}" == on ]]; then
        printf '%s\n' -f compose.vector-disk.yaml
    fi
}

# node9_compose ARGS...
# Run docker compose in the chat-node-9 project. Compose reads node-9.env
# and the secret file. No secret appears in a command line.
node9_compose() {
    local secrets_file="${NODE9_SECRETS_FILE:-$NODE9_DIR/secrets.env}"
    if [[ ! -f "$secrets_file" ]]; then
        printf 'node-9: %s is missing. Run node-9/bin/setup-secrets.\n' "$secrets_file" >&2
        return 2
    fi
    node9_check_toggle "${NODE9_VECTOR_PERSIST:-}" || return 2
    node9_check_isolation || return 2
    local -a args=()
    local line
    while IFS= read -r line; do
        args+=("$line")
    done < <(node9_compose_args)
    (
        cd "$NODE9_DIR" || exit 1
        docker compose -p "${NODE9_PROJECT:-chat-node-9}" \
            --env-file "$NODE9_DIR/node-9.env" \
            --env-file "$secrets_file" \
            "${args[@]}" "$@"
    )
}

# node9_images
# Print the five node-9 images, one per line. Run node9_load_env first.
node9_images() {
    printf '%s\n' \
        "${CORE_IMAGE:?Set CORE_IMAGE}" \
        "cassandra:${CASSANDRA_VERSION:?Set CASSANDRA_VERSION}" \
        "redis:${REDIS_VERSION:?Set REDIS_VERSION}" \
        "hashicorp/consul:${CONSUL_VERSION:?Set CONSUL_VERSION}" \
        "busybox:1.36"
}

# node9_image_file IMAGE
# Print the tar file name for IMAGE.
node9_image_file() {
    printf '%s.tar\n' "$(printf '%s' "$1" | tr '/:' '--')"
}

# node9_cqlsh ARGS...
# Run cqlsh in the node-9 Cassandra container.
node9_cqlsh() {
    node9_compose exec -T cassandra cqlsh "$@"
}

# node9_row_line TEXT
# Print the row count when TEXT holds exactly one row-count line.
# Return 1 for any other text.
node9_row_line() {
    local matches
    matches="$(printf '%s\n' "$1" | grep -cE '^\((0|1) rows\)$')"
    if [[ "$matches" != '1' ]]; then
        return 1
    fi
    if printf '%s\n' "$1" | grep -qxF '(1 rows)'; then
        printf '1\n'
    else
        printf '0\n'
    fi
}

# node9_apply_schema
# Apply the uuid schema only when the keyspace is absent. A failed or
# unclear query applies nothing. See CHAT-hcfgkbuz.
node9_apply_schema() {
    local attempt
    local ready=false
    for ((attempt = 1; attempt <= 180; attempt++)); do
        if node9_cqlsh -e 'DESCRIBE KEYSPACES' >/dev/null 2>&1; then
            ready=true
            break
        fi
        sleep 1
    done
    if [[ "$ready" != 'true' ]]; then
        printf 'node-9: Cassandra did not answer after 180 attempts\n' >&2
        return 1
    fi

    local query rows out
    query="SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'chat_uuid'"
    if ! out="$(node9_cqlsh -e "$query" 2>&1)"; then
        printf 'node-9: the keyspace query failed\n' >&2
        return 1
    fi
    if ! rows="$(node9_row_line "$out")"; then
        printf 'node-9: the keyspace query gave no single row count\n' >&2
        return 1
    fi
    if [[ "$rows" == '1' ]]; then
        return 0
    fi

    if ! node9_cqlsh -f /schema/keyspace-uuid.cql; then
        printf 'node-9: the schema apply failed\n' >&2
        return 1
    fi
    if ! out="$(node9_cqlsh -e "$query" 2>&1)"; then
        printf 'node-9: the keyspace query failed after the apply\n' >&2
        return 1
    fi
    if ! rows="$(node9_row_line "$out")" || [[ "$rows" != '1' ]]; then
        printf 'node-9: the keyspace is absent after the apply\n' >&2
        return 1
    fi
}
