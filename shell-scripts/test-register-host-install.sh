#!/usr/bin/env bash
# Check deployment installation and shared register validation in a fixture home.
set -uo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/vault"
printf 'fixture-vault-pass\n' > "$work/vault/vault-pass"
printf 'fixture-shared-vault\n' > "$work/vault/shared.yml"
printf 'fixture-owner-vault\n' > "$work/vault/owner.yml"
checks=0
failures=0
check() {
    checks=$((checks + 1))
    if "$@"; then printf 'ok %s\n' "$label"; else
        printf 'FAIL %s\n' "$label"
        failures=$((failures + 1))
    fi
}
status=0
XDG_CONFIG_HOME="$work/config" bash "$repo/node-9/install" --skip-cli \
    --prefix "$work/home" --vault-source "$work/vault" > "$work/out" 2> "$work/error" || status=$?
label='the deployment installer creates its fixture home'
check test "$status" -eq 0
label='installation includes the shared register library'
check test -f "$work/home/bin/register-lib.sh"
status=0
env NODE9_DIR=/foreign bash -c '. "$1"; test "$NODE9_DIR" = "$2"' \
    _ "$work/home/bin/lib.sh" "$work/home" > "$work/out" 2> "$work/error" || status=$?
label='deployment code retains its derived root'
check test "$status" -eq 0
status=0
bash -c '. "$1"; node9_load_env; test "$NODE9_PROJECT" = chat-node-9 && test "$NODE9_VECTOR_PERSIST" = on' \
    _ "$work/home/bin/lib.sh" > "$work/out" 2> "$work/error" || status=$?
label='the installed deployment supplies its fixed environment'
check test "$status" -eq 0
printf '\nchat_register_key_error() { printf "shared validator\\n"; return 1; }\n' \
    >> "$work/home/bin/register-lib.sh"
status=0
bash -c '. "$1"; node9_key_error valid' _ "$work/home/bin/lib.sh" \
    > "$work/out" 2> "$work/error" || status=$?
label='the deployment delegates key validation to its general library'
check test "$status" -eq 1
label='the shared validator supplies the observed result'
check grep -Fxq 'shared validator' "$work/out"
printf '%s checks, %s failures\n' "$checks" "$failures"
test "$failures" -eq 0
