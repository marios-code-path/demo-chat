#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose_file="$script_dir/../devops/docker/docker-compose.yaml"
host="${1:?Cassandra host is required}"
port="${2:?Cassandra port is required}"
key_type="${3:?Key type is required}"
network="${4:?Docker network is required}"

if [[ "$key_type" != long && "$key_type" != uuid ]]; then
    echo "Cassandra key type $key_type is unsupported" >&2
    exit 2
fi

keyspace="chat_$key_type"
if [[ "$host" == cassandra && "$port" == 9042 ]]; then
    docker compose -f "$compose_file" up -d cassandra
    ready=false
    for ((attempt = 1; attempt <= 180; attempt++)); do
        if docker compose -f "$compose_file" exec -T cassandra cqlsh \
            -e 'DESCRIBE KEYSPACES' >/dev/null 2>&1; then
            ready=true
            break
        fi
        sleep 1
    done
    if [[ "$ready" == false ]]; then
        echo 'Cassandra did not become ready' >&2
        exit 1
    fi
    if ! docker compose -f "$compose_file" exec -T cassandra cqlsh \
        -e "DESCRIBE KEYSPACE $keyspace" >/dev/null 2>&1; then
        # The repository schema drops its keyspace. Apply it only when absent.
        docker compose -f "$compose_file" exec -T cassandra cqlsh \
            -f "/schema/keyspace-$key_type.cql"
    fi
    docker compose -f "$compose_file" exec -T cassandra cqlsh \
        -e "DESCRIBE KEYSPACE $keyspace" >/dev/null
else
    first_host="${host%%,*}"
    docker run --rm --network "$network" --add-host host.docker.internal:host-gateway \
        --entrypoint cqlsh cassandra:4.1.3 "$first_host" "$port" \
        -e "DESCRIBE KEYSPACE $keyspace" >/dev/null
fi

echo "Cassandra is ready with $keyspace"
