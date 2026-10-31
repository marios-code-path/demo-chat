#!/usr/bin/env bash
# Check temporary alias rules before general tools change their inputs.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
reader="$repo/shell-scripts/legacy-node-inputs.sh"
checks=0
failures=0
run_alias() {
    status=0
    env -i PATH="$PATH" "$@" bash -c \
        '. "$1"; chat_legacy_node_inputs; rc=$?; ((rc == 0)) || exit "$rc"; printenv "$2"' \
        _ "$reader" "$target" > "$work/out" 2> "$work/error" || status=$?
}
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
while read -r legacy target; do
    run_alias "$legacy=legacy"
    label="$legacy supplies an absent counterpart"
    check test "$(cat "$work/out")" = legacy
    run_alias "$legacy=equal" "$target=equal"
    label="$legacy accepts an equal counterpart"
    check test "$status" -eq 0
    run_alias "$legacy=old" "$target=new"
    label="$legacy rejects a different counterpart"
    check test "$status" -eq 2
    run_alias "$legacy=" "$target=new"
    label="$legacy distinguishes empty from absent"
    check test "$status" -eq 2
    run_alias "$legacy="
    label="$legacy preserves a legacy empty value"
    check test "$status" -eq 0
done <<'EOF'
NODE9_PROJECT NODE_PROJECT
NODE9_VECTOR_PERSIST NODE_VECTOR_PERSIST
NODE9_RSOCKET_PORT NODE_RSOCKET_PORT
NODE9_MGMT_PORT NODE_MGMT_PORT
NODE9_REGISTRY_PORT NODE_REGISTRY_PORT
NODE9_IMAGE_REPO NODE_IMAGE_REPO
NODE9_HOME CHAT_DEPLOYMENT_HOME
NODE_9_BIN CHAT_DEPLOYMENT_BIN
NODE9_SECRETS_FILE CHAT_DEPLOYMENT_SECRETS_FILE
NODE9_SECRETS_DIR CHAT_DEPLOYMENT_SECRETS_DIR
NODE9_ANSIBLE_DIR CHAT_DEPLOYMENT_ANSIBLE_DIR
NODE9_JAVA CHAT_BUILD_JAVA
NODE9_MVN CHAT_BUILD_MVN
NODE9_JAVA_LIMIT CHAT_JAVA_PROBE_LIMIT_S
NODE9_ALLOW_SOURCE_TREE CHAT_ALLOW_SOURCE_TREE
NODE9_CHAT_BUILD CHAT_BUILD_COMMAND
NODE9_INSTALL_FAIL_LIB_MOVE CHAT_TEST_INSTALL_FAIL_LIB_MOVE
NODE9_INSTALL_LEGACY_SHA256 CHAT_TEST_INSTALL_LEGACY_SHA256
NODE9_INSTALL_LEGACY_SIZE CHAT_TEST_INSTALL_LEGACY_SIZE
NODE_9_REGISTER_TOPIC_NAME CHAT_REGISTER_TOPIC_NAME
NODE_9_REGISTER_TOPIC_ID CHAT_REGISTER_TOPIC_ID
EOF

status=0
env -i PATH="$PATH" NODE9_DIR=/foreign bash -c \
    '. "$1"; chat_legacy_node_inputs /expected' _ "$reader" > "$work/out" 2> "$work/error" || status=$?
label='a caller cannot redirect the derived root'
check test "$status" -eq 2

cp "$repo/node-9/node-9.env" "$work/legacy.env"
printf 'CHAT_EMBEDDING_IDENTITY=$(touch %s/executed)\n' "$work" > "$work/only.env"
printf 'NODE9_PROJECT=legacy\nNODE9_RSOCKET_PORT=16000\n' >> "$work/only.env"
status=0
env -i PATH="$PATH" CHAT_NODE_PROFILE="$work/only.env" NODE9_PROJECT=override bash -c \
    '. "$1"; chat_legacy_node_inputs; chat_legacy_node_profile_load NODE_PROJECT; rc=$?; ((rc == 0)) || exit "$rc"; printf "%s\n%s\n" "$NODE_PROJECT" "$CHAT_NODE_PROFILE"' \
    _ "$reader" > "$work/out" 2> "$work/error" || status=$?
label='legacy profile translation succeeds'
check test "$status" -eq 0
label='legacy environment precedes profile defaults'
check grep -Fxq override "$work/out"
label='profile selection survives temporary translation'
check grep -Fxq "$work/only.env" "$work/out"
label='legacy translation evaluates no profile text'
check test ! -e "$work/executed"

printf 'NODE9_PROJECT=legacy\nNODE_PROJECT=other\n' > "$work/conflict.env"
status=0
env -i PATH="$PATH" CHAT_NODE_PROFILE="$work/conflict.env" bash -c \
    '. "$1"; chat_legacy_node_profile_load NODE_PROJECT' _ "$reader" > "$work/out" 2> "$work/error" || status=$?
label='conflicting legacy and neutral profile keys fail'
check test "$status" -eq 2

status=0
env -i PATH="$PATH" CHAT_NODE_PROFILE="$work/legacy.env" bash -c \
    '. "$1"; chat_legacy_node_inputs; chat_legacy_node_profile_load NODE_PROJECT; rc=$?; ((rc == 0)) || exit "$rc"; printf "%s\n" "$NODE_PROJECT"' \
    _ "$reader" > "$work/out" 2> "$work/error" || status=$?
label='the shipped legacy profile remains readable'
check test "$status" -eq 0
target=NODE_PROJECT
run_alias NODE_PROJECT=valid NODE9_PROJECT=$'valid\n'
label='aliases compare complete values including trailing newlines'
check test "$status" -eq 2

printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
