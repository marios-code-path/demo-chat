#!/usr/bin/env bash
# Checks the stage and progress lines of chat-deploy. See CHAT-mzireodl.
#
# The test runs a copy of chat-deploy in a temporary directory. Stubs on PATH
# replace conda, mvn, docker, curl, and sleep. Stub scripts replace the
# run-*-in-docker.sh scripts. So the test starts, stops, and builds nothing,
# and a live deployment is safe while it runs. The real chat-build resolves
# the plan, because the stage lines read the image and module from it.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

repo="$(cd "$(dirname "$0")/.." && pwd)"
tmp_root="${TMPDIR:-/tmp}"
work="$(mktemp -d "${tmp_root%/}/chat-deploy-progress.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

mkdir -p "$work/bin" "$work/shell-scripts"
cp "$repo/shell-scripts/chat-deploy" "$work/shell-scripts/chat-deploy"
for service in consul cassandra redis; do
    printf '#!/usr/bin/env bash\necho "%s is ready (stub)"\n' "$service" \
        > "$work/shell-scripts/run-$service-in-docker.sh"
done
# The plan comes from the real chat-build. The image build is a stub.
cat > "$work/shell-scripts/chat-build" <<EOF
#!/usr/bin/env bash
for arg in "\$@"; do
    if [[ "\$arg" == --dry-run ]]; then exec "$repo/shell-scripts/chat-build" "\$@"; fi
done
echo 'stub image build output'
if [[ "\${STUB_REQUIRE_NO_KEY:-}" == 1 && -n "\${CHAT_EMBEDDING_OPENAI_API_KEY:-}" ]]; then
    echo 'FAIL the image build received the embedding API key'
    exit 5
fi
if [[ "\${STUB_FAIL:-}" == build ]]; then echo 'STUB BUILD FAILED'; exit 3; fi
EOF
cat > "$work/bin/conda" <<'EOF'
#!/usr/bin/env bash
# conda run [--no-capture-output] -n ENV COMMAND...
shift
while [[ "$1" == -* ]]; do
    if [[ "$1" == -n ]]; then shift; fi
    shift
done
exec "$@"
EOF
cat > "$work/bin/mvn" <<'EOF'
#!/usr/bin/env bash
echo 'stub maven install output'
if [[ "${STUB_REQUIRE_NO_KEY:-}" == 1 && -n "${CHAT_EMBEDDING_OPENAI_API_KEY:-}" ]]; then
    echo 'FAIL the install received the embedding API key'
    exit 5
fi
if [[ "${STUB_FAIL:-}" == install ]]; then echo 'STUB MVN FAILED'; exit 4; fi
EOF
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
if [[ "${STUB_REQUIRE_KEY_AT_CONTAINER:-}" == 1 && "$1" == compose ]]; then
    [[ "${CHAT_EMBEDDING_OPENAI_API_KEY:-}" == test-key ]] || exit 6
fi
exit 0
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$work/bin/sleep"
# Health answers after STUB_HEALTH_AFTER calls. Consul answers at once.
cat > "$work/bin/curl" <<EOF
#!/usr/bin/env bash
for arg in "\$@"; do
    case "\$arg" in
        */actuator/health)
            count=\$((\$(cat "$work/health-calls" 2>/dev/null || echo 0) + 1))
            echo "\$count" > "$work/health-calls"
            ((count > \${STUB_HEALTH_AFTER:-0})) && exit 0
            exit 7 ;;
        */v1/health/service/*) echo '[{"Service":{}}]'; exit 0 ;;
    esac
done
exit 7
EOF
chmod +x "$work/bin/"* "$work/shell-scripts/"*

failures=0
vector_args=()

# run_deploy LABEL -- ENV... ; sets output and status
run_deploy() {
    rm -f "$work/health-calls"
    output="$(env PATH="$work/bin:$PATH" CHAT_SERVICE_PASSWORD=stub \
        CONSUL_HOST=10.0.0.5 TMPDIR="$work" "$@" \
        "$work/shell-scripts/chat-deploy" core --cassandra --index lucene \
        --pubsub redis-pubsub --notls --node-id 16 --key-type uuid \
        "${vector_args[@]}" 2>&1)"
    status=$?
}

# expect NAME PATTERN... : every pattern must be in output
expect() {
    local name="$1" pattern
    shift
    for pattern in "$@"; do
        if ! grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the output does not hold: $pattern"
            echo "$output" | tail -8 | sed 's/^/       /'
            failures=$((failures + 1))
            return
        fi
    done
    echo "ok   $name"
}

expect_status() {
    local name="$1" want="$2"
    if [[ "$status" != "$want" ]]; then
        echo "FAIL $name: exit $status, want $want"
        failures=$((failures + 1))
        return
    fi
    echo "ok   $name"
}

run_deploy env STUB_HEALTH_AFTER=25
# SHOW_OUTPUT=1 prints what an operator sees in a good deployment.
if [[ -n "${SHOW_OUTPUT:-}" ]]; then printf '%s\n' "$output"; fi
expect_status 'a good deployment exits 0' 0
expect 'each stage prints a start line' \
    '[chat-deploy] Build log: ' \
    '[chat-deploy] Installing chat-deploy-cassandra and its dependencies.' \
    '[chat-deploy] Building the core image docker.io/library/cassandra-core-service-rsocket:0.0.1.' \
    '[chat-deploy] Starting the core container.' \
    '[chat-deploy] Waiting for core health on port 6791 (up to 120 attempts).' \
    '[chat-deploy] Waiting for the Consul registration (up to 30 attempts).'
expect 'each long stage prints its time' \
    '[chat-deploy] Install done in ' '[chat-deploy] Image build done in ' \
    '[chat-deploy] Core is healthy after '
expect 'the health wait prints progress every 10 attempts' \
    '[chat-deploy] Core is still starting (attempt 10 of 120).' \
    '[chat-deploy] Core is still starting (attempt 20 of 120).'
expect 'the last line is unchanged' \
    'Core is ready on port 6791 and registered in Consul'
expect 'Maven output goes to the log, not the terminal' '[chat-deploy] Build log: '
if grep -qF 'stub maven install output' <<< "$output"; then
    echo 'FAIL the install output reached the terminal'
    failures=$((failures + 1))
fi

run_deploy env STUB_FAIL=install
expect_status 'a failed install exits with its status' 4
expect 'a failed install names its stage and shows the log end' \
    '[chat-deploy] Install failed after ' 'STUB MVN FAILED'

run_deploy env STUB_FAIL=build
expect_status 'a failed image build exits with its status' 3
expect 'a failed image build names its stage and shows the log end' \
    '[chat-deploy] Image build failed after ' 'STUB BUILD FAILED'

vector_args=(--vector embedded --embedding openai)
run_deploy env -u CHAT_EMBEDDING_OPENAI_BASE_URL \
    CHAT_EMBEDDING_OPENAI_MODEL=nomic-embed-text-v1.5.Q8_0.gguf \
    CHAT_EMBEDDING_IDENTITY=nomic-embed-text-v1-5-768 \
    CHAT_EMBEDDING_OPENAI_API_KEY=test-key
expect_status 'a missing base URL stops the vector launch before Docker' 2
expect 'a missing base URL names the required variable' \
    'set CHAT_EMBEDDING_OPENAI_BASE_URL before starting core'

run_deploy env STUB_REQUIRE_NO_KEY=1 STUB_REQUIRE_KEY_AT_CONTAINER=1 \
    CHAT_EMBEDDING_OPENAI_BASE_URL=http://node2.lan:6090/v1 \
    CHAT_EMBEDDING_OPENAI_MODEL=nomic-embed-text-v1.5.Q8_0.gguf \
    CHAT_EMBEDDING_IDENTITY=nomic-embed-text-v1-5-768 \
    CHAT_EMBEDDING_OPENAI_API_KEY=test-key
expect_status 'the key bypasses install and image build but reaches container start' 0

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all chat-deploy progress cases passed'
