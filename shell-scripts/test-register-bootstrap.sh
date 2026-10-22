#!/usr/bin/env bash
# Offline checks for the register import on a prepared host. No container
# start, and no real core. See CHAT-jmnjvupr.
#
# The suite installs a node-9 home in a temporary directory. It gives that
# home a stub shell CLI and a stub curl. Every check runs offline.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
importer="$repo_root/node-9/bin/register-import"
stub_cli="$repo_root/shell-scripts/fixtures/register-stub-cli"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
ok() { printf 'ok   %s\n' "$1"; }
bad() { printf 'FAIL %s: %s\n' "$1" "$2"; failures=$((failures + 1)); }

sha_of() { shasum -a 256 "$1" | cut -d ' ' -f 1; }

# -- One installed home, one stub CLI, one stub curl ---------------------

install_home="$work/opt/node-9"
mkdir -p "$work/opt"
if ! install_out="$(bash node-9/install --prefix "$install_home" 2>&1)"; then
    printf 'FAIL the suite cannot install a home: %s\n' "$install_out"
    exit 1
fi

for name in Sigma Theta Phi; do
    printf 'fixture-%s-password\n' "$name" > "$install_home/secrets/$name.password"
    chmod 600 "$install_home/secrets/$name.password"
done
cat > "$install_home/secrets.env" <<'EOF'
CHAT_SERVICE_PASSWORD=fixture-service-password
CHAT_ADMIN_PASSWORD=fixture-admin-password
CHAT_EMBEDDING_OPENAI_API_KEY=fixture-embedding-key
CHAT_USER_SIGMA_PASSWORD=fixture-sigma-password
CHAT_USER_THETA_PASSWORD=fixture-theta-password
CHAT_USER_PHI_PASSWORD=fixture-phi-password
CHAT_USER_MARIO_PASSWORD=fixture-mario-password
EOF
chmod 600 "$install_home/secrets.env"

stub_bin="$work/bin"
mkdir -p "$stub_bin"
printf '#!/usr/bin/env bash\nexit 0\n' > "$stub_bin/curl"
chmod +x "$stub_bin/curl"

# -- One packed bundle ---------------------------------------------------

printf 'Preamble\ntext\n## First\nbody one\n## Second\nbody two\n' \
    > "$work/source.md"
cat > "$work/mapping.json" <<EOF
{
  "version": 1,
  "source_sha256": "$(sha_of "$work/source.md")",
  "source_date": "2026-10-22",
  "source_basis": "Owner-reviewed snapshot for the suite",
  "session": {"first_line": 1, "after_line": 3},
  "entries": [
    {"key": "CHAT-aaaaaaaa", "topic": "forward-register-first",
     "title": "First", "first_line": 3, "after_line": 5},
    {"key": "CHAT-bbbbbbbb", "topic": "forward-register-second",
     "title": "Second", "first_line": 5, "after_line": 7}
  ]
}
EOF
bundle="$work/bundle"
if ! pack_out="$(NODE9_ALLOW_SOURCE_TREE=1 "$importer" pack \
    --source "$work/source.md" --mapping "$work/mapping.json" \
    --bundle "$bundle" 2>&1)"; then
    printf 'FAIL the suite cannot pack a bundle: %s\n' "$pack_out"
    exit 1
fi

# -- Helpers -------------------------------------------------------------

stub_state="$work/stub-state"

reset_stub() {
    rm -rf "$stub_state"
    mkdir -p "$stub_state"
}

# calls  Print the number of stub calls.
calls() {
    if [[ -f "$stub_state/log" ]]; then
        grep -c . "$stub_state/log"
    else
        printf '0\n'
    fi
}

# run_apply [extra env assignments] -- ARGS...
# Run the installed apply with the stub CLI, the stub curl, and a clean
# stub state. The output holds standard output and standard error.
run_apply() {
    reset_stub
    out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
        CHAT_SHELL_CLI="$stub_cli" "$@" \
        "$install_home/bin/register-import" apply --bundle "$bundle" \
        --as Mario 2>&1)"
    status=$?
}

# -- The preflight -------------------------------------------------------

reset_stub
out="$(env -u NODE9_ALLOW_SOURCE_TREE PATH="$stub_bin:$PATH" \
    STUB_STATE="$stub_state" CHAT_SHELL_CLI="$stub_cli" \
    "$importer" apply --bundle "$bundle" --as Mario 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'source tree'* ]]; then
    ok 'apply_refuses_source_tree'
else
    bad 'apply_refuses_source_tree' "exit $status, output: $out"
fi

reset_stub
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
    CHAT_SHELL_CLI="$stub_cli" \
    "$install_home/bin/register-import" apply --bundle "$bundle" \
    --as Sigma 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'Mario'* && "$(calls)" -eq 0 ]]; then
    ok 'apply_refuses_other_user'
else
    bad 'apply_refuses_other_user' "exit $status, calls=$(calls), output: $out"
fi

rm -rf "$work/bad-bundle"
cp -R "$bundle" "$work/bad-bundle"
bad_part="$(find "$work/bad-bundle/parts" -type f | sort | head -1)"
printf 'x' >> "$bad_part"
reset_stub
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
    CHAT_SHELL_CLI="$stub_cli" \
    "$install_home/bin/register-import" apply --bundle "$work/bad-bundle" \
    --as Mario 2>&1)"
status=$?
if [[ $status -eq 2 && "$(calls)" -eq 0 \
      && "$out" == *"$(basename "$bad_part")"* ]]; then
    ok 'apply_refuses_bad_bundle_before_any_call'
else
    bad 'apply_refuses_bad_bundle_before_any_call' \
        "exit $status, calls=$(calls), output: $out"
fi

mv "$install_home/secrets/Phi.password" "$work/Phi.password.kept"
run_apply
if [[ $status -eq 77 && "$(calls)" -eq 0 && "$out" == *'Phi'* ]]; then
    ok 'apply_status_for_missing_credential'
else
    bad 'apply_status_for_missing_credential' \
        "exit $status, calls=$(calls), output: $out"
fi
mv "$work/Phi.password.kept" "$install_home/secrets/Phi.password"

run_apply STUB_NO_CORE=1
if [[ $status -eq 69 ]]; then
    ok 'apply_status_for_absent_core'
else
    bad 'apply_status_for_absent_core' "exit $status, output: $out"
fi

run_apply CORE_PORT=1
if [[ $status -eq 2 && "$out" == *'CORE_PORT'* && "$(calls)" -eq 0 ]]; then
    ok 'apply_refuses_port_override'
else
    bad 'apply_refuses_port_override' \
        "exit $status, calls=$(calls), output: $out"
fi

run_apply
secret_case='ok'
for secret in fixture-service-password fixture-admin-password \
    fixture-embedding-key fixture-sigma-password fixture-theta-password \
    fixture-phi-password fixture-mario-password; do
    [[ "$out" != *"$secret"* ]] || secret_case="the output holds $secret"
done
if [[ "$secret_case" == 'ok' ]]; then
    ok 'apply_prints_no_secret'
else
    bad 'apply_prints_no_secret' "$secret_case"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all register bootstrap cases passed'
