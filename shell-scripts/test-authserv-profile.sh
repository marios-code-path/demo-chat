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

# The launch JSON reaches the authserv container. See CHAT-vidnpywh.
#
# The change adds one environment key with an empty default, so the whole
# rendered text must change. These cases compare effective behavior instead.
# Control 1 is the environment mapping. Control 2 is every other key of the
# authserv service. Each case reports its own result, so one run shows the
# sequence that the plan records.
compose_file="$repo/chat-authorization-server/docker-compose.yaml"
: > "$work/control-jwk"
config_env=(
    CHAT_SERVICE_PASSWORD=test SPRING_DATASOURCE_USERNAME=test
    SPRING_DATASOURCE_PASSWORD=test AUTHSERV_UID=501 AUTHSERV_GID=20
    AUTHSERV_JWK_PATH="$work/control-jwk"
)
agents_json='{"app":{"oauth2":{"agent-scope":"chat.mcp","agents":[{"client-id":"a","username":"Agent"}]}}}'

cat > "$work/check-authserv.py" <<'PYTHON'
import json
import sys

mode = sys.argv[1]
service = json.load(sys.stdin)["services"]["authserv"]
failures = []

# Control 1. The keys and values that the service carried before the change.
CONTROL_ENVIRONMENT = {
    "APP_OAUTH2_JWK_PATH": "file:/run/secrets/authserv.jwk",
    "CHAT_SERVICE_PASSWORD": "test",
    "SPRING_DATASOURCE_PASSWORD": "test",
    "SPRING_DATASOURCE_URL": "jdbc:postgresql://authserv-postgres:5432/authserver",
    "SPRING_DATASOURCE_USERNAME": "test",
}
# Control 2. Every other key of the service, with the launch inputs that this
# script supplies. The volume source holds a work path, so the case checks the
# target and the read-only flag alone.
CONTROL_KEYS = sorted([
    "command", "depends_on", "entrypoint", "extra_hosts", "image",
    "networks", "ports", "user", "volumes",
])


def check(condition, message):
    if not condition:
        failures.append(message)


if mode in ("set", "unset"):
    environment = service.get("environment", {})
    for key, value in CONTROL_ENVIRONMENT.items():
        check(key in environment, "control 1 lost the key %s" % key)
        check(environment.get(key) == value,
              "control 1 changed %s to %r" % (key, environment.get(key)))
    extra = sorted(set(environment) - set(CONTROL_ENVIRONMENT) - {"SPRING_APPLICATION_JSON"})
    check(not extra, "the environment gained an unexpected key: %s" % extra)
    check("SPRING_APPLICATION_JSON" in environment,
          "the environment holds no SPRING_APPLICATION_JSON key")
    if mode == "set":
        check(environment.get("SPRING_APPLICATION_JSON") == sys.argv[2],
              "SPRING_APPLICATION_JSON holds %r" % environment.get("SPRING_APPLICATION_JSON"))
    else:
        check(environment.get("SPRING_APPLICATION_JSON") == "",
              "an unset variable gave %r" % environment.get("SPRING_APPLICATION_JSON"))
elif mode == "rest":
    check(sorted(k for k in service if k != "environment") == CONTROL_KEYS,
          "the service keys outside environment changed to %s"
          % sorted(k for k in service if k != "environment"))
    check(service.get("image") == "chat-authserv:0.0.1",
          "the image changed to %r" % service.get("image"))
    check(service.get("user") == "501:20", "the user changed to %r" % service.get("user"))
    check(service.get("command") is None, "the command changed")
    check(service.get("entrypoint") is None, "the entrypoint changed")
    check(list(service.get("depends_on", {})) == ["postgres"], "depends_on changed")
    check(list(service.get("networks", {})) == ["chatnet"], "the networks changed")
    check(service.get("extra_hosts") == ["host.docker.internal=host-gateway"],
          "extra_hosts changed")
    published = [(p.get("host_ip"), p.get("published"), p.get("target"))
                 for p in service.get("ports", [])]
    check(published == [("127.0.0.1", "9000", 9000), ("127.0.0.1", "9001", 9001)],
          "the ports changed to %s" % published)
    volumes = service.get("volumes", [])
    check(len(volumes) == 1, "the volume count changed to %d" % len(volumes))
    if volumes:
        check(volumes[0].get("target") == "/run/secrets/authserv.jwk",
              "the volume target changed")
        check(volumes[0].get("read_only") is True, "the volume lost read_only")
else:
    failures.append("unknown mode %s" % mode)

for message in failures:
    print("    %s" % message)
sys.exit(1 if failures else 0)
PYTHON

render_authserv() {
    if [[ -n "${1:-}" ]]; then
        env "${config_env[@]}" AUTHSERV_AGENTS_JSON="$1" \
            docker compose -f "$compose_file" config --format json
    else
        env -u AUTHSERV_AGENTS_JSON "${config_env[@]}" \
            docker compose -f "$compose_file" config --format json
    fi
}

case_a() { render_authserv "$agents_json" | python3 -I "$work/check-authserv.py" set "$agents_json"; }
case_b() { render_authserv "" | python3 -I "$work/check-authserv.py" unset; }
case_c() { render_authserv "" | python3 -I "$work/check-authserv.py" rest; }

launch_failures=0
for entry in 'A:a set value arrives whole:case_a' \
             'B:an unset value is empty and control 1 holds:case_b' \
             'C:control 2 is unchanged:case_c'; do
    IFS=':' read -r label description fn <<< "$entry"
    if "$fn"; then
        echo "PASS case $label — $description"
    else
        echo "FAIL case $label — $description"
        launch_failures=$((launch_failures + 1))
    fi
done
if (( launch_failures > 0 )); then
    echo "FAIL $launch_failures launch JSON case(s) failed" >&2
    exit 1
fi

echo 'authserv profile check passed'
