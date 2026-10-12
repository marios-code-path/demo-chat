#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose_file="$script_dir/../devops/docker/docker-compose.yaml"
host="${1:?Redis host is required}"
port="${2:?Redis port is required}"
network="${3:?Docker network is required}"

if [[ "$host" == redis && "$port" == 6379 ]]; then
    docker compose -f "$compose_file" up -d redis
    ping=(docker compose -f "$compose_file" exec -T redis redis-cli ping)
else
    ping=(docker run --rm --network "$network" --add-host host.docker.internal:host-gateway
        --entrypoint redis-cli redis:7 -h "$host" -p "$port" ping)
fi

for ((attempt = 1; attempt <= 30; attempt++)); do
    if [[ "$("${ping[@]}" 2>/dev/null)" == PONG ]]; then
        echo "Redis is ready at $host:$port"
        exit 0
    fi
    sleep 1
done

echo "Redis did not become ready at $host:$port" >&2
exit 1
