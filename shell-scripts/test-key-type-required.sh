#!/usr/bin/env bash
# Checks that every just recipe that builds or launches a deployment refuses
# to run without a key type. A compile, such as build-shell, needs none. See
# CHAT-ehpgyapa.
#
# A recipe that wrongly proceeds must not build or start anything. Stubs on
# PATH replace mvn and docker. They record each call and exit 99. The test
# clears the credentials, so chat-deploy and deploy-authserv stop early.
set -uo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"
tmp_root="${TMPDIR:-/tmp}"
work="$(mktemp -d "${tmp_root%/}/key-type-required.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/bin"
for tool in mvn docker; do
    printf '#!/usr/bin/env bash\necho "%s $*" >> "%s/calls"\necho "STUB %s"\nexit 99\n' \
        "$tool" "$work" "$tool" > "$work/bin/$tool"
done
chmod +x "$work/bin/"*
: > "$work/jwk"

run() {
    env PATH="$work/bin:$PATH" CHAT_SERVICE_PASSWORD= CHAT_IMPORT_ADMIN_PASSWORD= \
        SPRING_DATASOURCE_USERNAME= SPRING_DATASOURCE_PASSWORD= \
        just "$@" 2>&1
}

failures=0
message='needs a key type. Use --key-type TYPE with long or uuid.'

# expect_refusal RECIPE ARGS... : non-zero exit, the message, no tool call
expect_refusal() {
    local name="$*" output status
    : > "$work/calls"
    output="$(run "$@")"
    status=$?
    if ((status == 0)); then
        echo "FAIL just $name: exit 0, want a refusal"
        failures=$((failures + 1))
    elif ! grep -qF -- "$message" <<< "$output"; then
        echo "FAIL just $name: the output does not name the key type rule"
        echo "$output" | tail -4 | sed 's/^/       /'
        failures=$((failures + 1))
    elif [[ -s "$work/calls" ]]; then
        echo "FAIL just $name: it called $(head -1 "$work/calls")"
        failures=$((failures + 1))
    elif grep -qF '[build-shell] Installing' <<< "$output"; then
        echo "FAIL just $name: it installed before the refusal"
        failures=$((failures + 1))
    else
        echo "ok   just $name refuses"
    fi
}

# expect_plan RECIPE ARGS... -- PATTERN : exit 0 and the pattern
expect_plan() {
    local args=()
    while [[ "$1" != -- ]]; do args+=("$1"); shift; done
    local pattern="$2" output
    if ! output="$(run "${args[@]}")" || ! grep -qF -- "$pattern" <<< "$output"; then
        echo "FAIL just ${args[*]}: want exit 0 and $pattern"
        echo "$output" | tail -4 | sed 's/^/       /'
        failures=$((failures + 1))
        return
    fi
    echo "ok   just ${args[*]}"
}

expect_refusal dry-run-memory 0
expect_refusal launch-memory 0
expect_refusal build-cassandra 16
expect_refusal dry-run-shell 1
expect_refusal launch-shell 1
expect_refusal launch-shell 1 --build
expect_refusal deploy-core-cassandra-lucene-redis 16 --dry-run
expect_refusal deploy-core-cassandra-lucene-redis 16
expect_refusal deploy-authserv "$work/jwk"

expect_plan dry-run-memory 0 --key-type long -- -Dapp.key.type=long
expect_plan dry-run-memory 0 --key-type uuid -- -Dapp.key.type=uuid
expect_plan dry-run-shell 1 --key-type long -- -Dapp.key.type=long
expect_plan deploy-core-cassandra-lucene-redis 16 --key-type long --dry-run -- -Dapp.key.type=long
expect_plan build-shell --dry-run -- 'mvn -B -ntp -pl chat-shell -am'

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all key type rule cases passed'
