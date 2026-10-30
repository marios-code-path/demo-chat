#!/usr/bin/env bash
# Check the authorization server deployment profiles.
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/authserv-launch.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

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

printf '{}\n' > "$work/jwk"
mkdir -p "$work/bin" "$work/docker-config"
cat > "$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
case "$*" in
    *'6891/actuator/health'*) exit 0 ;;
    *'/v1/health/service/core-service-rsocket'*)
        printf '%s\n' "$*" > "$HOST_CAPTURE"
        printf '[{}]\n'
        exit 0 ;;
    *'9001/actuator/health'*)
        if [[ -f "$AUTH_STARTED" ]]; then printf 200; else printf 000; fi
        exit 0 ;;
esac
exit 1
EOF
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
case "$*" in
    *'up -d postgres'*) exit 0 ;;
    *'exec -T'*) exit 0 ;;
    *'up -d authserv'*) : > "$AUTH_STARTED"; exit 0 ;;
esac
exit 1
EOF
cat > "$work/bin/conda" <<'EOF'
#!/usr/bin/env bash
case "$*" in
    *'./shell-scripts/chat-build authserv --build'*)
        printf '%s:%s\n' "$CONSUL_HOST" "$CONSUL_PORT" > "$IMAGE_CAPTURE" ;;
esac
exit 0
EOF
chmod +x "$work/bin/curl" "$work/bin/docker" "$work/bin/conda"

recipe_env=(
    PATH="$work/bin:$PATH" HOST_CAPTURE="$work/host-url"
    AUTH_STARTED="$work/auth-started" IMAGE_CAPTURE="$work/image-address"
    CHAT_SERVICE_PASSWORD=test SPRING_DATASOURCE_USERNAME=test
    SPRING_DATASOURCE_PASSWORD=test CORE_MGMT_PORT=6891
    CONSUL_HOST=127.0.0.1 CONSUL_PORT=6892
    CHAT_BUILD_DOCKER_CONFIG="$work/docker-config"
)
env -u AUTHSERV_CONTAINER_CONSUL_HOST -u AUTHSERV_CONTAINER_CONSUL_PORT \
    "${recipe_env[@]}" \
    just --working-directory "$repo" deploy-authserv "$work/jwk" --key-type uuid > "$work/recipe-output"
[[ "$(cat "$work/image-address")" == 'consul:8500' ]] || {
    echo 'FAIL authserv image must use the Consul Docker service address' >&2
    exit 1
}
[[ "$(cat "$work/host-url")" == *'127.0.0.1:6892/v1/health/service/core-service-rsocket'* ]] || {
    echo 'FAIL host preflight must keep the published Consul address' >&2
    exit 1
}

rm -f "$work/auth-started" "$work/image-address" "$work/host-url"
env "${recipe_env[@]}" AUTHSERV_CONTAINER_CONSUL_HOST=registry.internal \
    AUTHSERV_CONTAINER_CONSUL_PORT=8501 \
    just --working-directory "$repo" deploy-authserv "$work/jwk" --key-type uuid > "$work/recipe-output"
[[ "$(cat "$work/image-address")" == 'registry.internal:8501' ]] || {
    echo 'FAIL authserv image must accept the container Consul address override' >&2
    exit 1
}
[[ "$(cat "$work/host-url")" == *'127.0.0.1:6892/v1/health/service/core-service-rsocket'* ]] || {
    echo 'FAIL the container override must not change the host preflight address' >&2
    exit 1
}

container_resolved="$(cd "$repo" && CONSUL_HOST=consul CONSUL_PORT=8500 \
    ./shell-scripts/chat-build authserv --build --notls --node-id 8 --consul \
    --key-type uuid --profile jdbc --profile client-init --dry-run)"
[[ "$container_resolved" == *'-Dspring.cloud.consul.host=consul'* ]] || {
    echo 'FAIL chat-build did not use the container Consul host' >&2
    exit 1
}
[[ "$container_resolved" == *'-Dspring.cloud.consul.port=8500'* ]] || {
    echo 'FAIL chat-build did not use the container Consul port' >&2
    exit 1
}

echo 'authserv profile check passed'
