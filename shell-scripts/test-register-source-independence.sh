#!/usr/bin/env bash
# Exercise general register tools without a deployment subtree.
set -uo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/source/shell-scripts"
checks=0
failures=0
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
for file in register register-create register-import register-range register-split register-uuid5 \
    register-lib.sh node-inputs.sh node-profile.sh; do
    [[ ! -f "$repo/shell-scripts/$file" ]] || cp "$repo/shell-scripts/$file" "$work/source/shell-scripts/$file"
done
tools="$work/source/shell-scripts"
printf 'Preamble\r\n## One\r\nbody\r\n' > "$work/source.md"
status=0
"$tools/register-split" "$work/source.md" "$work/parts" > "$work/out" 2> "$work/error" || status=$?
label='the splitter works without a deployment checkout'
check test "$status" -eq 0
if [[ -f "$work/parts/sections.jsonl" ]]; then
    : > "$work/recovered"
    while IFS= read -r part; do cat "$work/parts/$part" >> "$work/recovered"; done \
        < <(jq -r .path "$work/parts/sections.jsonl")
fi
label='the splitter preserves every source byte'
check cmp -s "$work/source.md" "$work/recovered"
status=0
"$tools/register-range" "$work/source.md" 2 3 "$work/range" > "$work/out" 2> "$work/error" || status=$?
printf '## One\r\n' > "$work/want"
label='the range tool preserves CRLF bytes'
check cmp -s "$work/want" "$work/range"
status=0
env -i PATH="$PATH" HOME="$work/home" XDG_CONFIG_HOME="$work/config" CHAT_ALLOW_SOURCE_TREE=1 \
    "$tools/register" config --agent Agent --project Example --register example-register \
    > "$work/config-path" 2> "$work/error" || status=$?
label='general configuration works without deployment files'
check test "$status" -eq 0
label='the command writes its selected application config'
check test -s "$(cat "$work/config-path")"
status=0
bash -c '. "$1"; chat_register_key_error "bad|key"' _ "$tools/register-lib.sh" \
    > "$work/out" 2> "$work/error" || status=$?
label='general key validation preserves its refusal status'
check test "$status" -eq 1
label='no deployment subtree is present'
check test ! -e "$work/source/node-9"
mkdir -p "$work/secrets"
printf 'fixture-password\n' > "$work/secrets/Theta.password"
printf 'CHAT_USER_THETA_PASSWORD=fixture-password\n' > "$work/secrets.env"
printf '#!/usr/bin/env bash\nexit 1\n' > "$work/cli"
chmod +x "$work/cli"
status=0
env -i PATH="$PATH" HOME="$work/home" CHAT_ALLOW_SOURCE_TREE=1 \
    CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
    CHAT_REGISTER_TOPIC_NAME=example-register CHAT_SHELL_CLI="$work/cli" CORE_PORT=invalid \
    CHAT_DEPLOYMENT_SECRETS_DIR="$work/secrets" CHAT_DEPLOYMENT_SECRETS_FILE="$work/secrets.env" \
    "$tools/register" read --as Theta > "$work/out" 2> "$work/error" || status=$?
label='an existing CLI refusal is not converted to status 2'
check test "$status" -eq 1
printf 'CHAT_USER_MARIO_PASSWORD=fixture-owner\n' >> "$work/secrets.env"
printf '#!/usr/bin/env bash\n: > "$CHAT_TEST_METADATA_CALLS"\nprintf " name\\n------\\n"\n' > "$work/metadata"
printf '#!/usr/bin/env bash\n: > "$CHAT_TEST_CALLS"\nexit 1\n' > "$work/counted-cli"
chmod +x "$work/metadata" "$work/counted-cli"
status=0
env -i PATH="$PATH" HOME="$work/home" CHAT_ALLOW_SOURCE_TREE=1 \
    CHAT_NODE_PROFILE="$repo/shell-scripts/fixtures/profiles/alpha.env" \
    CHAT_SHELL_CLI="$work/counted-cli" REGISTER_IMPORT_CQLSH="$work/metadata" CORE_PORT=65535 \
    CHAT_TEST_CALLS="$work/unexpected-call" CHAT_TEST_METADATA_CALLS="$work/unexpected-metadata" \
    CHAT_DEPLOYMENT_SECRETS_DIR="$work/secrets" \
    CHAT_DEPLOYMENT_SECRETS_FILE="$work/secrets.env" \
    "$tools/register-create" example-register --as Mario > "$work/out" 2> "$work/error" || status=$?
label='an invalid profile offset stops before a create command'
check test "$status" -eq 2
label='failed connection preparation creates no application call'
check test ! -e "$work/unexpected-call"
label='failed connection preparation starts no metadata transport'
check test ! -e "$work/unexpected-metadata"
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
