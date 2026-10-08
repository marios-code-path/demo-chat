#!/bin/bash
#
# test-flags.sh — assert chat-build emits the flags recorded under golden/.
#
# chat-build used to be verified by test-parity.sh, which diffed it against
# build-app.sh. Both are gone. This compares chat-build against committed
# expectations instead, so it stays verified without the scripts it replaced.
#
# The goldens were seeded while test-parity.sh still existed and passed every
# case, so they carry the legacy scripts' authority rather than merely freezing
# whatever chat-build happened to emit that day.
#
#   ./test-flags.sh                  # check every case
#   ./test-flags.sh core-memory-tls  # check one case
#   ./test-flags.sh --update         # rewrite goldens, then read the diff
#
# Updating is not a way to make a failure go away. A diff means either a
# deliberate change to the launch contract, in which case commit the new golden
# alongside the change that caused it, or a bug. Decide which before running
# --update.

set -uo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GOLDEN="$DIR/golden"
CHAT_BUILD="$DIR/chat-build"

# Pinned so goldens are reproducible on any machine. CONSUL_HOST especially:
# chat-build otherwise shells out to `docker inspect` to find it.
export CONSUL_HOST=10.0.0.5
export KEYSTORE_PASS="golden-test-pass"
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export CASSANDRA_CONTACT_POINTS=127.0.0.1
export CASSANDRA_PORT=9042

# chat-build requires --jwk to name an existing absolute path when it runs the
# authorization server. This test composes command lines and launches nothing,
# so a placeholder file is enough. A real ES256 key is not needed to build a
# flag list, and committing one would be wrong. See CHAT-cadftbow.
export GOLDEN_JWK=/tmp/chat-build-golden.jwk
: > "$GOLDEN_JWK"
export DEBUG_PORT=5005

# chat-build expands ~ in --index-root. A fixed HOME keeps that golden the same
# on every machine. See CHAT-eesnvnad.
GOLDEN_HOME=/home/golden

# Provenance matters. The four cases marked [parity] were asserted against
# build-app.sh before it was removed, so their goldens carry the legacy scripts'
# authority. The rest are snapshots of chat-build's own output: they detect
# change, but nothing independent ever vouched for their correctness.
#
# Cassandra was never parity-checked and could not have been: run-core.sh built
# the chat-deploy-cassandra module directly, while chat-build builds chat-deploy
# with the cassandra-backend profile, so the two differed by construction rather
# than by mistake.
#
# name | chat-build arguments
CASES=(
  # core backends
  "core-memory-init|core --memory --run --notls --long --init users,rootkeys --node-id 0"          # [parity]
  "core-memory-consul|core --memory --consul --run --notls --long --init users,rootkeys --node-id 0" # [parity]
  "core-memory-tls|core --memory --run --tls /etc/keys --long --init users,rootkeys --node-id 0"   # [parity]
  "core-cassandra|core --cassandra --run --notls --long --init users,rootkeys --node-id 0"
  "core-kafka|core --kafka --run --notls --long --node-id 0"
  "core-redis|core --redis --run --notls --long --node-id 0"
  "core-e2ee|core --memory --e2ee --run --notls --long --node-id 0"
  # core variants
  "core-uuid|core --memory --run --notls --uuid --node-id 0"
  "core-websocket|core --memory --websocket --run --notls --long --node-id 0"
  "core-debug|core --memory --debug --run --notls --long --node-id 0"
  "core-client-agent|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 5b2c9e1a-3f47-4d8e-9a61-0c7d2e8f4b13=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
  "core-agent-brackets|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 0f3a7c21-1b2d-4e5f-8a9b-1c2d3e4f5a6b=Bot_1 --agent 9e8d7c6b-5a4f-4e3d-8c2b-1a0f9e8d7c6b=Bot1"
  "core-build-image|core --memory --build --notls --long --node-id 0"
  # Lucene index files. See CHAT-eesnvnad.
  "core-redis-index-root|core --redis --run --notls --long --node-id 0 --index-root /var/lib/chat/lucene"
  "core-redis-index-root-home|core --redis --run --notls --long --node-id 0 --index-root ~/chat-lucene"
  # other services
  "rest-client|rest --run --notls --long --node-id 0"
  "rest-client-agent|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 5b2c9e1a-3f47-4d8e-9a61-0c7d2e8f4b13=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
  "gateway-client|gateway --run --notls --long --node-id 0"
  "authserv-client|authserv --run --notls --long --node-id 0 --jwk $GOLDEN_JWK"
  "authserv-client-agent|authserv --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent 5b2c9e1a-3f47-4d8e-9a61-0c7d2e8f4b13=Agent --agent 7c1e0b2a-5d0e-4c55-9d1e-2f6a8b3c4d5e=Claude"
  "shell-client|shell --run --notls --long --node-id 0"                                            # [parity]
  # root key roles under consul discovery. See RootKeySource in chat-deploy.
  "shell-consul|shell --consul --run --notls --long --node-id 0"
  "authserv-consul|authserv --consul --run --notls --long --node-id 0 --jwk $GOLDEN_JWK"
)

# Each case must exit with the code and print the text. See CHAT-frcrctdp.
# name | chat-build arguments | expected exit | expected text
REFUSALS=(
  "refuse-old-client-id|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-client-id x|2|--agent CLIENT_ID=HANDLE"
  "refuse-old-username|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-username Agent|2|--agent CLIENT_ID=HANDLE"
  "refuse-old-client-id-empty|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-client-id=|2|--agent CLIENT_ID=HANDLE"
  "refuse-old-username-empty|rest --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent-username=|2|--agent CLIENT_ID=HANDLE"
  "refuse-reserved|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=admin|1|reserved handle 'admin'"
  "refuse-duplicate-client|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=Agent --agent a=Claude|1|client id 'a' twice"
  "refuse-duplicate-handle|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=Claude --agent b=claude|1|handle 'claude' twice"
  "refuse-handle-chars|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent a=my-bot|1|letters, digits and underscores"
  "refuse-shape|core --memory --run --notls --long --node-id 0 --jwk $GOLDEN_JWK --agent Agent|1|CLIENT_ID=HANDLE"
  "refuse-no-jwk|rest --run --notls --long --node-id 0 --agent a=Agent|1|--agent requires --jwk PATH"
  "refuse-service|gateway --run --notls --long --node-id 0 --agent a=Agent|1|--agent requires the core, rest or authserv service"
  # --index-root. See CHAT-eesnvnad.
  "refuse-index-root-empty|core --redis --run --notls --long --node-id 0 --index-root=|1|--index-root must not be empty"
  "refuse-index-root-relative|core --redis --run --notls --long --node-id 0 --index-root lucene|1|--index-root must be an absolute path: lucene"
  "refuse-index-root-service|rest --run --notls --long --node-id 0 --index-root /var/lib/chat/lucene|1|--index-root requires the core service"
  "refuse-index-root-cassandra|core --cassandra --run --notls --long --node-id 0 --index-root /var/lib/chat/lucene|1|--index-root has no effect with --cassandra"
  "refuse-index-root-build|core --redis --build --notls --long --node-id 0 --index-root /var/lib/chat/lucene|1|--index-root cannot be baked into an image"
)

UPDATE=0
ONLY=""
for arg in "$@"; do
    case "$arg" in
        -u|--update) UPDATE=1 ;;
        --help|-h) awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0 ;;
        -*) echo "unknown option: $arg" >&2; exit 2 ;;
        *) ONLY="$arg" ;;
    esac
done

# One flag per line, quoting stripped, comma-lists de-duplicated, sorted. Same
# normalisation test-parity.sh applies, so goldens seeded from a green parity
# run compare like for like.
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

extract_flags() {
  sed -n '/^# JAVA_TOOL_OPTIONS:/,/^$/p' | sed '1d;/^$/d' | sed 's/^  //' \
    | tr ' ' '\n' | sed '/^$/d'
}

extract_profiles() {
  grep -oE '^mvn .*' | grep -oE '\-P[^ ]+' | sed 's/^-P//' \
    | tr ',' '\n' | sed '/^$/d' | LC_ALL=C sort | paste -sd, -
}

mkdir -p "$GOLDEN"
pass=0
fail=0
updated=0

for entry in "${CASES[@]}"; do
    name="${entry%%|*}"
    args="${entry#*|}"

    [ -n "$ONLY" ] && [ "$ONLY" != "$name" ] && continue

    # shellcheck disable=SC2086
    out="$(HOME="$GOLDEN_HOME" "$CHAT_BUILD" $args --dry-run 2>&1)"
    if [ $? -ne 0 ]; then
        echo "FAIL  $name — chat-build exited non-zero"
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
        continue
    fi

    actual="$({ echo "# profiles: $(echo "$out" | extract_profiles)"; \
                echo "$out" | extract_flags | normalise; })"
    file="$GOLDEN/$name.flags"

    if [ "$UPDATE" -eq 1 ]; then
        if [ -f "$file" ] && [ "$actual" = "$(cat "$file")" ]; then
            echo "same  $name"
        else
            echo "$actual" > "$file"
            echo "wrote $name"
            updated=$((updated + 1))
        fi
        continue
    fi

    if [ ! -f "$file" ]; then
        echo "FAIL  $name — no golden at ${file#"$DIR"/}; run --update to create it"
        fail=$((fail + 1))
        continue
    fi

    if [ "$actual" = "$(cat "$file")" ]; then
        echo "ok    $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name — flags differ from golden"
        diff <(cat "$file") <(echo "$actual") | sed 's/^/        /'
        fail=$((fail + 1))
    fi
done

for entry in "${REFUSALS[@]}"; do
    IFS='|' read -r name args want_exit want_text <<< "$entry"
    [ -n "$ONLY" ] && [ "$ONLY" != "$name" ] && continue
    [ "$UPDATE" -eq 1 ] && continue
    # shellcheck disable=SC2086
    out="$(HOME="$GOLDEN_HOME" "$CHAT_BUILD" $args --dry-run 2>&1)"; code=$?
    if [ "$code" -eq "$want_exit" ] && grep -qF -- "$want_text" <<< "$out"; then
        echo "ok    $name"
        pass=$((pass + 1))
    else
        echo "FAIL  $name — exit $code, want $want_exit and \"$want_text\""
        echo "$out" | sed 's/^/        /'
        fail=$((fail + 1))
    fi
done

echo
if [ "$UPDATE" -eq 1 ]; then
    echo "goldens: $updated rewritten"
    echo "review the diff before committing — an unexplained change is a bug, not a new baseline"
    exit 0
fi

if [ "$fail" -gt 0 ]; then
    echo "flags: $fail case(s) failed, $pass passed"
    exit 1
fi
echo "flags: all $pass case(s) match"
