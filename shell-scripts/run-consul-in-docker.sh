#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
consul_host="${CONSUL_HOST:-127.0.0.1}"
consul_port="${CONSUL_PORT:-8500}"

consul_is_running() {
    local leader
    leader="$(curl --connect-timeout 2 --max-time 3 --fail --silent \
        "http://$consul_host:$consul_port/v1/status/leader")" || return 1
    [[ -n "$leader" && "$leader" != '""' ]]
}

if consul_is_running; then
    echo "Consul is ready at $consul_host:$consul_port"
    exit 0
fi

if [[ "$consul_port" != 8500 || ( "$consul_host" != 127.0.0.1 && "$consul_host" != localhost ) ]]; then
    echo "Consul is unavailable at $consul_host:$consul_port. Check the configured address." >&2
    exit 1
fi

docker compose -f "$script_dir/../devops/docker/docker-compose.yaml" up -d consul

for ((attempt = 1; attempt <= 30; attempt++)); do
    if consul_is_running; then
        echo "Consul is ready at $consul_host:$consul_port"
        exit 0
    fi
    sleep 1
done

echo "Consul did not become ready at $consul_host:$consul_port" >&2
exit 1
