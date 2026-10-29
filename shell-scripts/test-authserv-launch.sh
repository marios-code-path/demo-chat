#!/usr/bin/env bash
# Check the authorization server profiles and the Consul container lookup.
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/authserv-launch.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

if [[ "${1:-}" != consul ]]; then
    plan="$(cd "$repo" && just --dry-run deploy-authserv "$work/jwk" --key-type long 2>&1)"
    profile_calls="$(printf '%s\n' "$plan" | rg -c -- '--profile jdbc --profile client-init' || true)"
    if [[ "$profile_calls" != 2 ]]; then
        echo 'FAIL deploy-authserv must pass jdbc and client-init to both chat-build calls' >&2
        exit 1
    fi
    resolved="$(cd "$repo" && CONSUL_HOST=10.0.0.5 ./shell-scripts/chat-build authserv \
        --build --notls --node-id 8 --consul --key-type long \
        --profile jdbc --profile client-init --dry-run)"
    [[ "$resolved" == *'-Dspring.profiles.active=jdbc,client-init'* ]] || {
        echo 'FAIL chat-build did not activate the JDBC and client init profiles' >&2
        exit 1
    }
fi

if [[ "${1:-}" == profile ]]; then
    echo 'authserv profile check passed'
    exit 0
fi

mkdir -p "$work/bin"
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
case "$1 $2 $3" in
    'ps -aqf name=consul') printf 'stopped-consul\nrunning-consul\n' ;;
    'ps -qf name=consul') printf 'running-consul\nsecond-running-consul\n' ;;
    'inspect -f {{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
        [[ "$4" == running-consul ]] || exit 2
        printf '10.0.0.5\n' ;;
    *) exit 2 ;;
esac
EOF
chmod +x "$work/bin/docker"

output="$(cd "$repo" && env -u CONSUL_HOST PATH="$work/bin:$PATH" \
    ./shell-scripts/chat-build core --memory --consul --run --notls \
    --key-type long --node-id 0 --dry-run 2>&1)" || {
    printf '%s\n' "$output" >&2
    echo 'FAIL Consul lookup did not select one running container' >&2
    exit 1
}
[[ "$output" == *'-Dspring.cloud.consul.host=10.0.0.5'* ]] || {
    echo 'FAIL Consul host is absent from the plan' >&2
    exit 1
}

echo 'authserv launch checks passed'
