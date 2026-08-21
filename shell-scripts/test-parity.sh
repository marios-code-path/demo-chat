#!/bin/bash
# test-parity.sh — assert chat-build produces the same JVM flags as build-app.sh.
#
# For each case, this runs build-app.sh -x with the environment its matching
# run-*.sh script exports, runs the equivalent chat-build --dry-run, and diffs
# the two flag sets. Known-intentional differences are declared per case in
# EXPECTED_ONLY_OLD / EXPECTED_ONLY_NEW; anything else fails the run.
#
# Usage: ./test-parity.sh [-v]

set -uo pipefail
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

VERBOSE=0
[[ "${1:-}" == "-v" ]] && VERBOSE=1

PASS=0
FAIL=0

# Pull JAVA_TOOL_OPTIONS out of build-app.sh -x output and split it one flag
# per line. The env file build-app.sh writes is a set of `echo K = V` lines,
# and the options span several physical lines, so take everything between the
# JAVA_TOOL_OPTIONS marker and the next marker.
extract_old() {
  awk '
    /echo JAVA_TOOL_OPTIONS =/ { sub(/.*echo JAVA_TOOL_OPTIONS =/, ""); capture=1 }
    /echo MAVEN ARGS=/ { capture=0 }
    capture { print }
  ' | tr ' ' '\n' | sed '/^$/d'
}

extract_new() {
  sed -n '/^# JAVA_TOOL_OPTIONS:/,/^$/p' | sed '1d;/^$/d' | sed 's/^  //' \
    | tr ' ' '\n' | sed '/^$/d'
}

extract_old_profiles() { grep -o 'MAVEN ARGS=.*' | sed 's/MAVEN ARGS=//'; }
extract_new_profiles() { grep -oE '^mvn .*' | grep -oE '\-P[^ ]+'; }

# normalise: strip shell quoting that never needed to reach the JVM, collapse
# repeated entries inside comma-lists (build-app.sh emits client-<proto>-<disc>
# .yml twice for client services), and sort.
normalise() {
  sed "s/'//g" | sed 's/"//g' | python3 -c '
import sys
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    if "=" in line and "," in line.split("=", 1)[1]:
        key, value = line.split("=", 1)
        value = ",".join(dict.fromkeys(value.split(",")))
        line = f"{key}={value}"
    print(line)
' | LC_ALL=C sort -u
}

# Maven treats -P as a set, so compare profiles order-insensitively.
sort_profiles() { tr ',' '\n' | sed '/^$/d' | LC_ALL=C sort | paste -sd, -; }

run_case() {
  local name="$1"; shift
  local old_out new_out
  old_out=$(eval "$OLD_CMD" 2>&1)
  new_out=$("$DIR/chat-build" "$@" 2>&1)

  local old_flags new_flags
  old_flags=$(echo "$old_out" | extract_old | normalise)
  new_flags=$(echo "$new_out" | extract_new | normalise)

  local only_old only_new
  only_old=$(comm -23 <(echo "$old_flags") <(echo "$new_flags"))
  only_new=$(comm -13 <(echo "$old_flags") <(echo "$new_flags"))

  # Drop the differences this case declares as intentional.
  [[ -n "${EXPECTED_ONLY_OLD:-}" ]] && only_old=$(echo "$only_old" | grep -vE "$EXPECTED_ONLY_OLD")
  [[ -n "${EXPECTED_ONLY_NEW:-}" ]] && only_new=$(echo "$only_new" | grep -vE "$EXPECTED_ONLY_NEW")
  only_old=$(echo "$only_old" | sed '/^$/d')
  only_new=$(echo "$only_new" | sed '/^$/d')

  local old_p new_p
  old_p=$(echo "$old_out" | extract_old_profiles | sed 's/^-P//' | sort_profiles)
  new_p=$(echo "$new_out" | extract_new_profiles | sed 's/^-P//' | sort_profiles)

  local failed=0
  if [[ "$old_p" != "$new_p" ]]; then
    echo "FAIL  $name — maven profiles differ"
    echo "        build-app.sh: $old_p"
    echo "        chat-build:   $new_p"
    failed=1
  fi
  if [[ -n "$only_old" || -n "$only_new" ]]; then
    [[ $failed -eq 0 ]] && echo "FAIL  $name — flag sets differ"
    [[ -n "$only_old" ]] && echo "$only_old" | sed 's/^/        only build-app.sh: /'
    [[ -n "$only_new" ]] && echo "$only_new" | sed 's/^/        only chat-build:   /'
    failed=1
  fi

  if [[ $failed -eq 0 ]]; then
    echo "ok    $name"
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
  fi

  if [[ $VERBOSE -eq 1 ]]; then
    echo "      --- build-app.sh flags ---"; echo "$old_flags" | sed 's/^/      /'
    echo "      --- chat-build flags ---";   echo "$new_flags" | sed 's/^/      /'
  fi

  unset EXPECTED_ONLY_OLD EXPECTED_ONLY_NEW
}

# --------------------------------------------------------------------------
# Case 1 — core, memory backend, local discovery, no TLS
#   old: run-core.sh runlocal memory  (env below is what run-core.sh exports)
# --------------------------------------------------------------------------
(
export APP_PRIMARY="core-service"
export APP_IMAGE_NAME="memory-core-service-rsocket"
export ADDITIONAL_CONFIGS="classpath:/config/server-rsocket-consul.yml,"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"
export OPT_FLAGS=" -Dlogging.level.io.rsocket.FrameLogger=OFF"
export PORTS_FLAGS="-Dserver.port=6791 -Dmanagement.server.port=6791 -Dspring.rsocket.server.port=6790"
export SERVICE_FLAGS="-Dspring.main.web-application-type=reactive -Dapp.server.proto=rsocket \
-Dapp.service.core.key -Dapp.service.core.pubsub=memory -Dapp.service.core.index=lucene \
-Dapp.service.core.persistence=memory -Dapp.service.core.secrets=memory -Dapp.service.composite \
-Dapp.service.composite.auth \
-Dapp.core.controllers='persistence,index,key,pubsub,secrets,user,topic,message' \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user \
-Dapp.controller.topic -Dapp.controller.message"

OLD_CMD="$DIR/build-app.sh -m chat-deploy -s memory -e rsocket -p prod \
-n core-service-rsocket -k long -b runlocal -i users,rootkeys -c notls -x"

PASS=0; FAIL=0
run_case "core --memory --notls --init users,rootkeys" \
  core --memory --run --notls --long --init users,rootkeys --dry-run
exit $FAIL
) ; R1=$?

# --------------------------------------------------------------------------
# Case 2 — core, memory backend, consul discovery
# --------------------------------------------------------------------------
(
export APP_PRIMARY="core-service"
export APP_IMAGE_NAME="memory-core-service-rsocket"
export ADDITIONAL_CONFIGS="classpath:/config/server-rsocket-consul.yml,"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"
export OPT_FLAGS=" -Dlogging.level.io.rsocket.FrameLogger=OFF"
export PORTS_FLAGS="-Dserver.port=6791 -Dmanagement.server.port=6791 -Dspring.rsocket.server.port=6790"
export SERVICE_FLAGS="-Dspring.main.web-application-type=reactive -Dapp.server.proto=rsocket \
-Dapp.service.core.key -Dapp.service.core.pubsub=memory -Dapp.service.core.index=lucene \
-Dapp.service.core.persistence=memory -Dapp.service.core.secrets=memory -Dapp.service.composite \
-Dapp.service.composite.auth \
-Dapp.core.controllers='persistence,index,key,pubsub,secrets,user,topic,message' \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user \
-Dapp.controller.topic -Dapp.controller.message"
export CONSUL_HOST=10.0.0.5
export CONSUL_PORT=8500

OLD_CMD="$DIR/build-app.sh -m chat-deploy -s memory -e rsocket -p prod \
-n core-service-rsocket -k long -b runlocal -i users,rootkeys -d consul -c notls -x"

PASS=0; FAIL=0
run_case "core --memory --consul --notls" \
  core --memory --consul --run --notls --long --init users,rootkeys --dry-run
exit $FAIL
) ; R2=$?

# --------------------------------------------------------------------------
# Case 3 — core, memory, TLS on
# --------------------------------------------------------------------------
(
export APP_PRIMARY="core-service"
export APP_IMAGE_NAME="memory-core-service-rsocket"
export ADDITIONAL_CONFIGS="classpath:/config/server-rsocket-consul.yml,"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"
export OPT_FLAGS=" -Dlogging.level.io.rsocket.FrameLogger=OFF"
export PORTS_FLAGS="-Dserver.port=6791 -Dmanagement.server.port=6791 -Dspring.rsocket.server.port=6790"
export SERVICE_FLAGS="-Dspring.main.web-application-type=reactive -Dapp.server.proto=rsocket \
-Dapp.service.core.key -Dapp.service.core.pubsub=memory -Dapp.service.core.index=lucene \
-Dapp.service.core.persistence=memory -Dapp.service.core.secrets=memory -Dapp.service.composite \
-Dapp.service.composite.auth \
-Dapp.core.controllers='persistence,index,key,pubsub,secrets,user,topic,message' \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user \
-Dapp.controller.topic -Dapp.controller.message"
export KEYSTORE_PASS="parity-test-pass"

OLD_CMD="$DIR/build-app.sh -m chat-deploy -s memory -e rsocket -p prod \
-n core-service-rsocket -k long -b runlocal -i users,rootkeys -c /etc/keys -x"

PASS=0; FAIL=0
run_case "core --memory --tls /etc/keys" \
  core --memory --run --tls /etc/keys --long --init users,rootkeys --dry-run
exit $FAIL
) ; R3=$?

# --------------------------------------------------------------------------
# Case 4 — shell, client backend, local discovery
#   run-shell.sh sets MAIN_FLAGS, but build-app.sh overwrites MAIN_FLAGS
#   wholesale, so the shell interactive flags never reach the JVM through the
#   old path. chat-build keeps them in opt_flags, which is the fix, so they
#   are declared as an expected new-only difference.
# --------------------------------------------------------------------------
(
export CLIENT_PROTO="rsocket"
export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export PORTS_FLAGS="-Dserver.port=9101 -Dmanagement.server.port=9101"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
-Dapp.client.rsocket.core.key -Dapp.client.rsocket.core.persistence \
-Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user \
-Dapp.client.rsocket.composite.message -Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export OPT_FLAGS=" -Dlogging.level.io.rsocket.FrameLogger=OFF -Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"

OLD_CMD="$DIR/build-app.sh -m chat-deploy -k long -p shell -e shell -s client \
-n chat-shell -b runlocal -d local -c notls -x"

EXPECTED_ONLY_NEW='spring.shell.interactive.enabled|spring.main.web-application-type'
PASS=0; FAIL=0
run_case "shell --client --notls" \
  shell --run --notls --long --dry-run
exit $FAIL
) ; R4=$?

TOTAL_FAIL=$((R1 + R2 + R3 + R4))
echo
if [[ $TOTAL_FAIL -eq 0 ]]; then
  echo "parity: all cases passed"
else
  echo "parity: $TOTAL_FAIL case(s) failed"
fi
exit $TOTAL_FAIL
