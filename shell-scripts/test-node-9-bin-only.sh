#!/usr/bin/env bash
set -euo pipefail

. "$(dirname "${BASH_SOURCE[0]}")/test-support/clear-legacy-inputs.sh" || exit $?

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
home="$fixture/node-9"
mkdir -p "$home/bin" "$home/lib" "$home/schema" "$home/ansible" "$home/secrets"
printf 'installed-at: fixture\nsource-commit: old\n' > "$home/.installed"
printf 'old launcher\n' > "$home/bin/old-launcher"
printf 'secret sentinel\n' > "$home/secrets.env"
printf 'jar sentinel\n' > "$home/lib/keep.jar"
printf 'schema sentinel\n' > "$home/schema/keep.cql"
printf 'vault sentinel\n' > "$home/ansible/keep.yml"

output="$(NODE9_JAVA=missing-java NODE9_MVN=missing-mvn \
    "$repo_root/node-9/install" --prefix "$home" --bin-only)"
[[ "$output" == *'bin installed'* ]]
[[ -x "$home/bin/setup-portal" && -x "$home/bin/chat-shell-portal-tunnel" ]]
[[ -x "$home/bin/chat-mcp-stdio" && -x "$home/bin/generate-agent-instructions" ]]
[[ ! -e "$home/bin/old-launcher" ]]
[[ "$(cat "$home/.installed")" == $'installed-at: fixture\nsource-commit: old' ]]
[[ "$(cat "$home/secrets.env")" == 'secret sentinel' ]]
[[ "$(cat "$home/lib/keep.jar")" == 'jar sentinel' ]]
[[ "$(cat "$home/schema/keep.cql")" == 'schema sentinel' ]]
[[ "$(cat "$home/ansible/keep.yml")" == 'vault sentinel' ]]

dry_run="$(NODE9_JAVA=missing-java "$repo_root/node-9/install" \
    --prefix "$home" --bin-only --dry-run)"
[[ "$dry_run" == *'mode: bin-only'* && "$dry_run" == *"target: $home/bin"* ]]

for extra in --keep-secrets --skip-cli; do
    if "$repo_root/node-9/install" --prefix "$home" --bin-only "$extra" \
        >/dev/null 2>&1; then
        printf 'bin-only accepted %s.\n' "$extra" >&2
        exit 1
    fi
done

if "$repo_root/node-9/install" --prefix "$fixture/new-home" --bin-only \
    >/dev/null 2>&1; then
    printf 'bin-only accepted an uninstalled home.\n' >&2
    exit 1
fi
[[ ! -e "$fixture/new-home" ]]

linked_home="$fixture/linked-home"
mkdir -p "$linked_home"
printf 'installed-at: fixture\n' > "$linked_home/.installed"
ln -s "$home/bin" "$linked_home/bin"
if "$repo_root/node-9/install" --prefix "$linked_home" --bin-only \
    >/dev/null 2>&1; then
    printf 'bin-only accepted a symbolic link at bin.\n' >&2
    exit 1
fi
[[ -L "$linked_home/bin" ]]
printf 'node-9 bin-only checks passed\n'
