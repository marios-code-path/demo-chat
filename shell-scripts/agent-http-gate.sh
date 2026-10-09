#!/usr/bin/env bash
# agent-http-gate.sh — run the agent HTTP and relay tests that the reactor
# builds cannot run. See CHAT-frcrctdp.
#
# The CI jobs and build-health.sh run without the expose-webflux and
# rest-core-e2e profiles. Under those builds, RestAgentSelectionTests and
# RestToCoreBearerDeploymentTests are skipped. McpMessagingDeploymentTests also
# requires expose-webflux. This runner selects each class under its profile.
# It fails unless every selected class runs with zero skipped tests.
#
#   ./shell-scripts/agent-http-gate.sh            # resolve online, like CI
#   ./shell-scripts/agent-http-gate.sh --offline  # use the local repository alone
#
# Exit 0 when all three classes pass with zero skipped tests and the relay core jar
# holds no chat-webflux. Exit 1 otherwise. Exit 2 on a usage error.

set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
OFFLINE=""

for arg in "$@"; do
    case "$arg" in
        --offline) OFFLINE="-o" ;;
        --help|-h) awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

status=0
logs="$(mktemp -d "${TMPDIR:-/tmp}/agent-http-gate.XXXXXX")"

# The value of one numeric attribute of the testsuite element.
attr() {
    echo "$1" | grep -o " $2=\"[0-9]*\"" | grep -o '[0-9][0-9]*' || echo 0
}

# require_class MODULE CLASS MIN_TESTS MAVEN_EXIT
require_class() {
    local report="$ROOT/$1/target/surefire-reports/TEST-$2.xml"
    if [ "$4" -ne 0 ]; then
        echo "FAIL  $2 — the Maven build exited $4"
        status=1
    fi
    if [ ! -f "$report" ]; then
        echo "FAIL  $2 — no surefire report at ${report#"$ROOT"/}"
        status=1
        return
    fi
    local head tests failures errors skipped
    head="$(grep -o '<testsuite [^>]*>' "$report" | head -1)"
    tests="$(attr "$head" tests)"
    failures="$(attr "$head" failures)"
    errors="$(attr "$head" errors)"
    skipped="$(attr "$head" skipped)"
    echo "      $2: $tests tests, $failures failures, $errors errors, $skipped skipped"
    if [ "$tests" -lt "$3" ] || [ "$failures" -ne 0 ] || [ "$errors" -ne 0 ] || [ "$skipped" -ne 0 ]; then
        echo "FAIL  $2 — want at least $3 tests, 0 failures, 0 errors and 0 skipped"
        status=1
    else
        echo "ok    $2"
    fi
}

echo "running: RestAgentSelectionTests and McpMessagingDeploymentTests under expose-webflux"
(cd "$ROOT" && mvn $OFFLINE -B -pl chat-deploy-memory -am -Pexpose-webflux clean test \
    -Dtest=RestAgentSelectionTests,McpMessagingDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false) > "$logs/rest.log" 2>&1
rest_exit=$?
require_class chat-deploy-memory com.demo.chat.test.deploy.memory.RestAgentSelectionTests 2 "$rest_exit"
require_class chat-deploy-memory com.demo.chat.test.deploy.memory.McpMessagingDeploymentTests 6 "$rest_exit"
if grep -q 'Surefire is going to kill self fork JVM' "$logs/rest.log"; then
    echo "WARN  REST test process exceeded the Surefire shutdown deadline"
    echo "      REST log: $logs/rest.log"
    shutdown_warning=1
else
    shutdown_warning=0
fi

# clean removes every output of the first build. The relay core is a core and
# not a REST launch, so its jar must not hold chat-webflux.
echo "running: RestToCoreBearerDeploymentTests under rest-core-e2e"
(cd "$ROOT" && mvn $OFFLINE -B -pl chat-deploy-memory-integration-test -am -Prest-core-e2e clean verify \
    -Dtest=RestToCoreBearerDeploymentTests -Dsurefire.failIfNoSpecifiedTests=false) > "$logs/relay.log" 2>&1
require_class chat-deploy-memory-integration-test com.demo.chat.deploy.test.security.RestToCoreBearerDeploymentTests 7 $?

jar="$ROOT/chat-deploy-memory/target/chat-deploy-memory-0.0.1-exec.jar"
if [ ! -f "$jar" ]; then
    echo "FAIL  relay core jar — no file at ${jar#"$ROOT"/}"
    status=1
elif unzip -l "$jar" | grep -q "chat-webflux"; then
    echo "FAIL  relay core jar — it holds chat-webflux"
    status=1
else
    echo "ok    relay core jar holds no chat-webflux"
fi

if [ "$status" -eq 0 ]; then
    echo "agent http gate: ok"
    if [ "$shutdown_warning" -eq 0 ]; then
        rm -rf "$logs"
    fi
else
    echo "agent http gate: FAILED — logs in $logs"
fi
exit "$status"
