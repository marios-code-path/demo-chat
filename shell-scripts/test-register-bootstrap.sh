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

file_mode_of() {
    case "$(uname -s)" in
        Darwin) stat -f '%Lp' "$1" ;;
        *) stat -c '%a' "$1" ;;
    esac
}

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

# One cqlsh stub. It answers the three proof queries from the stub state. The
# room id comes from the topic name, so it stays stable for one state.
cqlsh_stub="$work/bin/cqlsh-stub"
cat > "$cqlsh_stub" <<'STUB'
#!/usr/bin/env bash
set -uo pipefail
state="${STUB_STATE:?Set STUB_STATE}"
query=''
while (( $# )); do
    case "$1" in
        -e)
            query="${2:-}"
            shift 2 || true
            ;;
        *)
            shift
            ;;
    esac
done
# room_id_of NAME  Print a stable fake UUID for one topic name.
room_id_of() {
    local hex
    hex="$(printf '%s' "$1" | shasum -a 256 | cut -c 1-32)"
    printf '%s-%s-%s-%s-%s\n' "${hex:0:8}" "${hex:8:4}" "${hex:12:4}" \
        "${hex:16:4}" "${hex:20:12}"
}
owner="$(cat "$state/owner" 2> /dev/null || printf 'Mario\n')"
case "$query" in
    *chat_room_name*)
        name="$(printf '%s' "$query" | sed -n "s/.*name = '\\([^']*\\)'.*/\\1/p")"
        if [[ -f "$state/topics" ]] && grep -qx "$name" "$state/topics"; then
            printf '\n room_id\n'
            printf -- '--------------------------------------\n'
            printf ' %s\n\n(1 rows)\n' "$(room_id_of "$name")"
        else
            printf '\n room_id\n'
            printf -- '--------------------------------------\n'
            printf '\n(0 rows)\n'
        fi
        exit 0
        ;;
    *auth_metadata_target*)
        printf '\n principal                            | permission\n'
        printf -- '--------------------------------------+------------\n'
        printf ' %s |          *\n\n(1 rows)\n' "$(room_id_of "owner-$owner")"
        exit 0
        ;;
    *chat_user*)
        printf '\n handle\n'
        printf -- '--------\n'
        printf ' %s\n\n(1 rows)\n' "$owner"
        exit 0
        ;;
esac
printf 'cqlsh-stub: the query is unknown\n' >&2
exit 1
STUB
chmod +x "$cqlsh_stub"

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
        CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" "$@" \
        "$install_home/bin/register-import" apply --bundle "$bundle" \
        --as Mario 2>&1)"
    status=$?
}

# -- The preflight -------------------------------------------------------

reset_stub
out="$(env -u NODE9_ALLOW_SOURCE_TREE PATH="$stub_bin:$PATH" \
    STUB_STATE="$stub_state" CHAT_SHELL_CLI="$stub_cli" \
    REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
    "$importer" apply --bundle "$bundle" --as Mario 2>&1)"
status=$?
if [[ $status -eq 2 && "$out" == *'source tree'* ]]; then
    ok 'apply_refuses_source_tree'
else
    bad 'apply_refuses_source_tree' "exit $status, output: $out"
fi

reset_stub
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
    CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
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
    CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
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

# -- The index and its ownership proof -----------------------------------


bundle_hash="$(jq -r '.content_sha256' "$bundle/bundle-manifest.json")"
receipts="$install_home/data/register-import/$bundle_hash"

# run_index [extra env] -- Call ensure_index through the library mode.
run_index() {
    reset_stub
    rm -rf "$receipts"
    mkdir -p "$receipts"
    chmod 700 "$receipts"
    if [[ -n "${1:-}" ]]; then
        printf '%s\n' "$1" > "$stub_state/owner"
    fi
    if [[ -n "${2:-}" ]]; then
        printf '%s\n' "$2" >> "$stub_state/topics"
    fi
    out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
        STUB_INDEX_PRE="${3:-0}" STUB_DENY_INDEX="${4:-0}" \
        CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
        NODE9_ALLOW_SOURCE_TREE=1 bash -c '
            REGISTER_IMPORT_LIB=1
            export REGISTER_IMPORT_LIB
            source "$1" || exit 2
            export CHAT_SHELL_USER=Mario
            ensure_index "$2"' \
        _ "$install_home/bin/register-import" "$receipts" 2>&1)"
    status=$?
}

run_index Mario
if [[ $status -eq 0 && -f "$receipts/index.json" \
      && "$(jq -r '.owner' "$receipts/index.json" 2> /dev/null)" == 'Mario' \
      && -n "$(jq -r '.id' "$receipts/index.json" 2> /dev/null)" \
      && "$(file_mode_of "$receipts/index.json")" == '600' \
      && "$(grep -c 'add-topic' "$stub_state/log")" -eq 1 ]]; then
    ok 'index_fresh_creates_and_saves_proof'
else
    bad 'index_fresh_creates_and_saves_proof' "exit $status, output: $out"
fi

run_index Mario register-index 0
if [[ $status -eq 0 && -f "$receipts/index.json" \
      && "$(grep -c 'add-topic' "$stub_state/log")" -eq 0 ]]; then
    ok 'index_present_without_proof_and_empty_rebuilds_proof'
else
    bad 'index_present_without_proof_and_empty_rebuilds_proof' \
        "exit $status, output: $out"
fi

run_index Mario '' 2
if [[ $status -eq 1 && ! -f "$receipts/index.json" \
      && "$(grep -c 'post' "$stub_state/log")" -eq 0 ]]; then
    ok 'index_present_with_messages_and_no_proof_stops'
else
    bad 'index_present_with_messages_and_no_proof_stops' \
        "exit $status, output: $out"
fi

run_index Sigma register-index 0
if [[ $status -eq 1 && ! -f "$receipts/index.json" ]]; then
    ok 'index_other_owner_stops'
else
    bad 'index_other_owner_stops' "exit $status, output: $out"
fi

run_index Mario '' 0 1
if [[ $status -eq 1 && ! -f "$receipts/index.json" ]]; then
    ok 'index_denied_stops'
else
    bad 'index_denied_stops' "exit $status, output: $out"
fi

# One target topic of the bundle exists already. The import stops before its
# first write.
reset_stub
rm -rf "$receipts"
printf 'forward-register-first\n' >> "$stub_state/topics"
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
    CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
    NODE9_ALLOW_SOURCE_TREE=1 bash -c '
        REGISTER_IMPORT_LIB=1
        export REGISTER_IMPORT_LIB
        source "$1" || exit 2
        export CHAT_SHELL_USER=Mario
        check_target_topics "$2"' \
    _ "$install_home/bin/register-import" "$bundle" 2>&1)"
status=$?
if [[ $status -eq 1 && "$out" == *'forward-register-first'* ]]; then
    ok 'existing_target_topic_stops_before_first_write'
else
    bad 'existing_target_topic_stops_before_first_write' \
        "exit $status, output: $out"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all register bootstrap cases passed'
