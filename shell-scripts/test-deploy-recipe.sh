#!/usr/bin/env bash
# Checks the flags of `just deploy-core-cassandra-lucene-redis`. Each case
# runs the recipe with --dry-run, so no service starts. See CHAT-brlctoze.
#
# No case passes --stop. A recipe that ignores a later flag runs a real stop,
# and that stops the live Redis, Cassandra, and Consul containers. This
# happened once, on 2026-10-16. The test also clears CHAT_SERVICE_PASSWORD,
# because chat-deploy refuses to start a service without it.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?
export CHAT_SERVICE_PASSWORD=

cd "$(dirname "$0")/.."

failures=0
recipe=deploy-core-cassandra-lucene-redis

# expect_plan NAME PATTERN... -- ARGS...
# The dry-run plan must exit 0 and hold every pattern.
expect_plan() {
    local name="$1"
    shift
    local patterns=()
    while [[ "$1" != -- ]]; do
        patterns+=("$1")
        shift
    done
    shift
    local output
    if ! output="$(just "$recipe" "$@" 2>&1)"; then
        echo "FAIL $name: the recipe exited non-zero"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    local pattern
    for pattern in "${patterns[@]}"; do
        if ! grep -qF -- "$pattern" <<< "$output"; then
            echo "FAIL $name: the plan does not hold $pattern"
            failures=$((failures + 1))
            return
        fi
    done
    echo "ok   $name"
}

# expect_refusal NAME MESSAGE -- ARGS...
# The recipe must exit 2 and print the message.
expect_refusal() {
    local name="$1" message="$2"
    shift 3
    local output status
    output="$(just "$recipe" "$@" 2>&1)"
    status=$?
    if [[ $status -ne 2 ]] || ! grep -qF -- "$message" <<< "$output"; then
        echo "FAIL $name: expected exit 2 and '$message', got exit $status"
        echo "$output" | tail -5
        failures=$((failures + 1))
        return
    fi
    echo "ok   $name"
}

expect_refusal 'no key type is refused, and long is no default' 'needs a key type. Use --key-type TYPE' \
    -- 16 --dry-run
expect_plan '--key-type uuid selects the uuid key type' \
    -Dapp.key.type=uuid -Dapp.nodeid=16 -- 16 --key-type uuid --dry-run
expect_plan '--key-type=uuid form' \
    -Dapp.key.type=uuid -Dapp.nodeid=16 -- 16 --key-type=uuid --dry-run
expect_plan '--key-type long is explicit' \
    -Dapp.key.type=long -Dapp.nodeid=16 -- 16 --key-type long --dry-run
expect_plan '--key-type keeps the default node id' \
    -Dapp.key.type=uuid -Dapp.nodeid=7 -- --key-type uuid --dry-run
expect_plan 'node_id= form with --key-type' \
    -Dapp.key.type=uuid -Dapp.nodeid=16 -- node_id=16 --key-type uuid --dry-run
expect_plan 'the composition is unchanged with --key-type' \
    -Dapp.service.core.index=lucene -Dapp.service.core.pubsub=redis-pubsub \
    -Dapp.service.core.persistence=cassandra -- 16 --key-type uuid --dry-run
expect_plan 'embedded vectors and OpenAI embeddings are selected together' \
    -Dapp.service.core.vector=embedded -Dapp.service.core.embedding=openai \
    -Dmanagement.endpoint.vectorindex.enabled=true \
    -- 16 --key-type uuid --vector embedded --embedding openai --dry-run
expect_refusal 'a vector store needs an embedding provider' \
    '--vector and --embedding must be set together' \
    -- 16 --key-type uuid --vector embedded --dry-run
expect_refusal 'Redis stays limited to live pub/sub' \
    'supports --vector embedded --embedding openai with --cassandra' \
    -- 16 --key-type uuid --vector redis --embedding openai --dry-run
expect_refusal 'a blank vector store is refused' '--vector needs a provider' \
    -- 16 --key-type uuid --vector '' --embedding openai --dry-run
secret_plan="$(CHAT_EMBEDDING_OPENAI_API_KEY='test-secret-marker' just "$recipe" 16 \
    --key-type uuid --vector embedded --embedding openai --dry-run 2>&1)"
if [[ "$secret_plan" == *'test-secret-marker'* ]]; then
    echo 'FAIL the image plan contains the embedding API key'
    failures=$((failures + 1))
else
    echo 'ok   the image plan excludes the embedding API key'
fi
expect_refusal 'chat-build refuses an unknown key type' "invalid choice: 'bogus'" \
    -- 16 --key-type bogus --dry-run
expect_refusal '--key-type needs a value' '--key-type needs a value' \
    -- 16 --key-type
expect_refusal 'the old --uuid flag is refused' 'Unknown deployment flag: --uuid' \
    -- 16 --uuid --dry-run
expect_refusal 'an unknown flag is refused' 'Unknown deployment flag: --bogus' \
    -- 16 --bogus

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all deploy recipe cases passed'
