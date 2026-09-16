#!/bin/bash
#
# Proves the embedding feature outside a test classpath.
#
# A test classpath cannot prove this feature works. Every test gate in this
# repository puts test outputs on the classpath, which is what hid the original
# defect. See CHAT-etfnihnu.
#
# The gate builds a packaged deployment, asserts that no test output is on its
# classpath, launches it against a synthetic OpenAI endpoint, seeds messages
# through persistence, rebuilds the index, and runs one search.
#
# Production client code runs against a synthetic endpoint. The gate needs no
# secret key and no external network.
#
#   ./shell-scripts/vector/gate-embedding-launch.sh
#
# Exit status: 0 when every assertion holds, 1 when one fails.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT="$( cd "$DIR/../.." && pwd )"
cd "$ROOT" || exit 1

IDENTITY="gate-stub-v1"
STUB_PORT=9099
APP_PORT=8080
ACTUATOR_USER="actuator"
ACTUATOR_PASS="actuator"
WORK=$(mktemp -d)
STUB_PID=""
APP_PID=""

# Each child is signalled and then reaped. A kill with no wait leaves the
# shell to report the job later, and it then writes "Terminated: 15" after the
# last line of this script. Step 6 of the plan reads that last line.
cleanup() {
    if [ -n "$APP_PID" ]; then
        kill "$APP_PID" 2>/dev/null
        wait "$APP_PID" 2>/dev/null
    fi
    if [ -n "$STUB_PID" ]; then
        kill "$STUB_PID" 2>/dev/null
        wait "$STUB_PID" 2>/dev/null
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $1"
    exit 1
}

# CLAUDE.md requires miniforge for Python. The stub runs under it, and so does
# every JSON read below. The activation happens once, here, and it must
# succeed. A later step that silently used a system python3 would break the one
# rule this repository sets.
# shellcheck disable=SC1090
if [ -f "$HOME/miniforge3/etc/profile.d/conda.sh" ]; then
    source "$HOME/miniforge3/etc/profile.d/conda.sh"
    conda activate base || fail "conda activate base failed"
else
    fail "miniforge is not installed at ~/miniforge3. See CLAUDE.md."
fi
# conda activate base sets CONDA_PREFIX, and on this machine it does not put
# the miniforge bin directory first. A bare python3 then resolves to the
# homebrew interpreter. Measured on 2026-09-14. So every call below names the
# interpreter of the active environment.
PYTHON="$CONDA_PREFIX/bin/python3"
[ -x "$PYTHON" ] || fail "no python3 at $PYTHON after conda activate base"
case "$PYTHON" in
    "$HOME/miniforge3"/*) ;;
    *) fail "python3 resolves to $PYTHON, which is outside miniforge. See CLAUDE.md." ;;
esac

echo "0. Python runs under miniforge."
"$PYTHON" -c "import sys; print('   ' + sys.executable)"

# Two commands, and not one. The expose-webflux profile declares its
# dependencies on the parent, so every module inherits them. chat-webflux then
# reads itself as a dependency, and maven stops with "is referencing itself"
# before it builds anything. -pl does not avoid that, because maven reads every
# module of the reactor first. So the package below names one pom file and
# builds no reactor, and the install before it carries no profile.
#
# The package also needs the deploy profile. The root build sets the Boot
# repackage skip to true, and the deploy profile is what sets it to false. The
# gate launches the artifact with java -jar, so it needs the executable one.
echo "1. Install the reactor."
mvn -o -B -Dmaven.test.skip=true clean install > "$WORK/build.log" 2>&1 \
    || { tail -40 "$WORK/build.log"; fail "the install did not finish"; }

echo "2. Package chat-deploy-memory with expose-webflux."
mvn -o -B -Pexpose-webflux,deploy -Dmaven.test.skip=true \
    -f chat-deploy-memory/pom.xml \
    clean package >> "$WORK/build.log" 2>&1 \
    || { tail -40 "$WORK/build.log"; fail "the package did not finish"; }

JAR=$(find chat-deploy-memory/target -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*.original' | head -1)
[ -n "$JAR" ] || fail "no packaged jar in chat-deploy-memory/target"
echo "   jar: $JAR"

echo "3. Assert that no test output is on the launched classpath."
# The library count first. A plain jar carries no BOOT-INF/lib entry at all,
# and the test jar check below would then pass on an artifact that holds no
# library. That would be a false green.
LIBS=$(unzip -l "$JAR" | grep -c 'BOOT-INF/lib/')
[ "$LIBS" -gt 0 ] || fail "the artifact holds no BOOT-INF/lib entry, so it is not the executable jar"
echo "   $LIBS libraries inside the artifact"
TESTS=$(unzip -l "$JAR" | grep -c 'tests\.jar')
[ "$TESTS" -eq 0 ] || { unzip -l "$JAR" | grep 'tests\.jar'; fail "a test jar is inside the packaged artifact"; }
echo "   ok, no test jar inside the artifact"

echo "4. Start the synthetic embeddings endpoint."
"$PYTHON" "$DIR/openai-stub-server.py" "$STUB_PORT" > "$WORK/stub.log" 2>&1 &
STUB_PID=$!
sleep 1
kill -0 "$STUB_PID" 2>/dev/null || fail "the stub did not start"

echo "5. Launch the packaged deployment."
java --enable-native-access=ALL-UNNAMED -jar "$JAR" \
    --app.nodeid=1 \
    --app.key.type=long \
    --spring.application.name=gate-embedding \
    --app.server.proto=rest \
    --server.port="$APP_PORT" \
    --app.service.core.key=memory \
    --app.service.core.persistence=memory \
    --app.service.core.index=lucene \
    --app.service.core.pubsub=memory \
    --app.service.core.secrets=memory \
    --app.service.composite=true \
    --app.service.composite.auth=true \
    --app.service.security.userdetails=true \
    --app.service.core.vector=simple \
    --app.service.core.embedding=openai \
    --app.service.core.embedding.identity="$IDENTITY" \
    --app.service.core.embedding.openai.base-url="http://127.0.0.1:$STUB_PORT" \
    --app.service.core.embedding.openai.api-key=not-a-secret \
    --app.service.core.embedding.openai.model=stub-embedding \
    --app.controller.persistence=true \
    --app.controller.recall=true \
    --app.controller.message=true \
    --app.controller.topic=true \
    --app.controller.user=true \
    --app.controller.key=true \
    --app.controller.index=true \
    --app.actuator.username="$ACTUATOR_USER" \
    --app.actuator.password="$ACTUATOR_PASS" \
    --management.endpoint.vectorindex.enabled=true \
    --management.endpoints.web.exposure.include=vectorindex,health \
    > "$WORK/app.log" 2>&1 &
APP_PID=$!

echo "   waiting for health"
for _ in $(seq 1 60); do
    curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" > /dev/null 2>&1 && break
    kill -0 "$APP_PID" 2>/dev/null || { tail -40 "$WORK/app.log"; fail "the deployment exited"; }
    sleep 2
done
curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" > /dev/null \
    || { tail -40 "$WORK/app.log"; fail "the deployment did not answer health"; }
echo "   ok, the deployment is up"

echo "6. Assert that an actuator call without credentials answers 401."
CODE=$(curl -sS -o /dev/null -w '%{http_code}' "http://127.0.0.1:$APP_PORT/actuator/vectorindex")
[ "$CODE" = "401" ] || fail "an actuator call without credentials answered $CODE, expected 401"
echo "   ok, 401"

echo "7. Seed three messages through persistence, with no credentials."
# MessageSendRequest carries msg, from, and dest. RequestResponse declares
# @JsonTypeInfo as a property named type, and MessageSendRequest declares
# @JsonTypeName, so the body must carry that discriminator. The route declares
# @ResponseStatus(HttpStatus.CREATED), so it answers 201.
for text in "apple pie recipe" "banana bread recipe" "carrot soup recipe"; do
    CODE=$(curl -sS -o /dev/null -w '%{http_code}' \
        -X PUT "http://127.0.0.1:$APP_PORT/persist/message/add" \
        -H 'Content-Type: application/json' \
        -d "{\"type\":\"MessageSendRequest\",\"msg\":\"$text\",\"from\":10,\"dest\":20}")
    [ "$CODE" = "201" ] || fail "the seed answered $CODE for '$text', expected 201"
done
echo "   ok, three messages persisted"

echo "8. Trigger a rebuild, under Basic credentials."
# The timestamp comes first. The trigger never promises the job key, so the
# gate correlates by start instant. This needs the gate clock and the
# application clock to agree, and both run on this host.
TRIGGER_AT=$("$PYTHON" -c "import time; print(time.time())")
curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
    -X POST "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/trigger.json" \
    || fail "the trigger did not answer"

# A rejected trigger starts nothing and creates no job. Without this check the
# gate would wait for a job that no call created, and then report a missing
# durable record. See CHAT-cxduiwjj.
ACCEPTED=$("$PYTHON" -c "
import json
print(json.load(open('$WORK/trigger.json')).get('accepted'))
")
[ "$ACCEPTED" = "True" ] \
    || { cat "$WORK/trigger.json"; fail "the trigger was rejected, so no run started"; }
echo "   ok, the trigger was accepted"

echo "9. Wait for the run, then for the durable record."
# Two bounds. The outer one covers the rebuild, which grows with the corpus.
# The inner one covers the interval between running=false and the durable
# write, which is one event and one store write. running=false does not prove
# that the record is written. See CHAT-cxduiwjj.
OUTER=60
INNER=15
for _ in $(seq 1 "$OUTER"); do
    curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
        "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/status.json"
    RUNNING=$("$PYTHON" -c "
import json
print(json.load(open('$WORK/status.json'))['status']['running'])
" 2>/dev/null)
    [ "$RUNNING" = "False" ] && break
    sleep 2
done
[ "$RUNNING" = "False" ] \
    || { cat "$WORK/status.json"; fail "the outer bound expired and the run did not finish"; }
echo "   ok, the run finished"

for _ in $(seq 1 "$INNER"); do
    curl -sS -u "$ACTUATOR_USER:$ACTUATOR_PASS" \
        "http://127.0.0.1:$APP_PORT/actuator/vectorindex" > "$WORK/status.json"
    TERMINAL=$("$PYTHON" -c "
import json
jobs = [j for j in json.load(open('$WORK/status.json'))['jobs']
        if j['startedAt'] >= $TRIGGER_AT and j['outcome'] != 'RUNNING']
jobs.sort(key=lambda j: j['startedAt'], reverse=True)
print(jobs[0]['outcome'] if jobs else 'NONE')
" 2>/dev/null)
    [ "$TERMINAL" != "NONE" ] && break
    sleep 1
done
[ "$TERMINAL" != "NONE" ] \
    || { cat "$WORK/status.json"; fail "the inner bound expired and the run left no durable record"; }
echo "   ok, the durable record reports $TERMINAL"

echo "10. Assert that the newest job succeeded and carries the identity."
# RELEASED and FAILED both end the wait. Only SUCCEEDED proves the rebuild did
# its work, and this gate asserts hits below.
[ "$TERMINAL" = "SUCCEEDED" ] \
    || { cat "$WORK/status.json"; fail "the newest job reports $TERMINAL, expected SUCCEEDED"; }

# The filter is the point. An unfiltered jobs[0] was the RUNNING record of this
# run, so the gate read the right identity from the wrong record, and a
# SUCCEEDED record of an earlier run would also have satisfied it.
JOB_IDENTITY=$("$PYTHON" -c "
import json
jobs = [j for j in json.load(open('$WORK/status.json'))['jobs']
        if j['startedAt'] >= $TRIGGER_AT and j['outcome'] != 'RUNNING']
jobs.sort(key=lambda j: j['startedAt'], reverse=True)
print(jobs[0].get('embeddingIdentity'))
")
[ "$JOB_IDENTITY" = "$IDENTITY" ] \
    || fail "the newest job carries identity '$JOB_IDENTITY', expected '$IDENTITY'"
echo "   ok, the job carries $IDENTITY"

echo "11. Run one recall, with no credentials."
curl -sS -X POST "http://127.0.0.1:$APP_PORT/message/recall/topic" \
    -H 'Content-Type: application/json' \
    -d '{"type":"TopicRecallRequest","topicId":20,"query":"recipe","limit":10}' \
    > "$WORK/recall.json" || fail "the recall did not answer"

"$PYTHON" -c "
import json, sys
body = json.load(open('$WORK/recall.json'))
if body.get('indexComplete') is not True:
    print('indexComplete is', body.get('indexComplete'), 'expected True')
    sys.exit(1)
hits = body.get('hits', [])
if len(hits) < 3:
    print('hits', len(hits), 'expected at least 3')
    sys.exit(1)
print('   ok,', len(hits), 'hits and indexComplete true')
" || { cat "$WORK/recall.json"; echo; tail -40 "$WORK/app.log"; fail "the recall answer is wrong"; }

echo
echo "PASS. The packaged deployment embedded, rebuilt, and searched."
