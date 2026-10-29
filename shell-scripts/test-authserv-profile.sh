#!/usr/bin/env bash
# Check the authorization server deployment profiles.
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/authserv-launch.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

plan="$(cd "$repo" && just --dry-run deploy-authserv "$work/jwk" --key-type long 2>&1)"
profile_calls="$(printf '%s\n' "$plan" | rg -c -- '--profile jdbc --profile client-init' || true)"
if [[ "$profile_calls" != 2 ]]; then
    echo 'FAIL deploy-authserv must pass jdbc and client-init to both chat-build calls' >&2
    exit 1
fi

resolved="$(cd "$repo" && CONSUL_HOST=10.0.0.5 ./shell-scripts/chat-build authserv \
    --build --notls --node-id 8 --consul --key-type long \
    --profile jdbc --profile client-init --dry-run)"
[[ "$resolved" == *'-Dspring.profiles.active=jdbc,client-init'* ]] || {
    echo 'FAIL chat-build did not activate the JDBC and client init profiles' >&2
    exit 1
}

echo 'authserv profile check passed'
