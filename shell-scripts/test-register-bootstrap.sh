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
if [[ "${STUB_REQUIRE_NODE9_ENV:-0}" == '1' \
      && ( "${NODE9_PROJECT:-}" != 'chat-node-9' \
           || "${NODE9_VECTOR_PERSIST:-}" != 'on' ) ]]; then
    printf 'cqlsh-stub: node-9 environment is absent\n' >&2
    exit 2
fi
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
    *'FROM chat_uuid.chat_room WHERE name = '*'ALLOW FILTERING;'*)
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
    *'FROM chat_uuid.auth_metadata WHERE target = '*'ALLOW FILTERING;'*)
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

run_index Mario register-index 0 1
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

# -- Topics, pointers, and receipts --------------------------------------

snapshot_topic_name="forward-register-snapshot-$(sha_of "$work/source.md" | cut -c 1-12)"

# full_apply [extra env assignments]
# Run one whole apply against a clean stub state and a clean receipt
# directory. The state stays after the run, so a case can read the log.
full_apply() {
    out="$(env -u NODE9_PROJECT -u NODE9_VECTOR_PERSIST \
        PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
        CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" "$@" \
        "$install_home/bin/register-import" apply --bundle "$bundle" \
        --as Mario 2>&1)"
    status=$?
}

# line_of PATTERN  Print the first log line number that holds PATTERN.
line_of() {
    grep -n -- "$1" "$stub_state/log" 2> /dev/null | head -1 | cut -d : -f 1
}

# last_line_of PATTERN  Print the last log line number that holds PATTERN.
last_line_of() {
    grep -n -- "$1" "$stub_state/log" 2> /dev/null | tail -1 | cut -d : -f 1
}

reset_stub
rm -rf "$receipts"
printf 'forward-register-first\n' >> "$stub_state/topics"
full_apply
if [[ $status -eq 1 && "$out" == *'forward-register-first'* \
      && "$(grep -c 'add-topic' "$stub_state/log" || true)" -eq 0 \
      && ! -f "$receipts/index.json" ]]; then
    ok 'target_conflict_stops_before_index_write'
else
    bad 'target_conflict_stops_before_index_write' \
        "exit $status, output: $out"
fi

reset_stub
rm -rf "$receipts"
full_apply STUB_REQUIRE_NODE9_ENV=1
if [[ $status -eq 0 ]]; then
    ok 'apply_loads_node9_environment_for_cql'
else
    bad 'apply_loads_node9_environment_for_cql' \
        "exit $status, output: $out"
fi

reset_stub
rm -rf "$receipts"
full_apply
first_status=$status
first_out="$out"
if [[ $first_status -ne 0 ]]; then
    bad 'apply_completes_one_import' "exit $first_status, output: $first_out"
else
    ok 'apply_completes_one_import'
fi

order_case='ok'
add_snapshot="$(line_of "add-topic --name $snapshot_topic_name")"
first_part="$(line_of "post --topic $snapshot_topic_name")"
read_snapshot="$(line_of "messages $snapshot_topic_name")"
pointer_first="$(line_of 'post --topic register-index')"
last_post="$(last_line_of 'post --topic')"
snapshot_pointer_line="$last_post"
if [[ -z "$add_snapshot" || -z "$first_part" || -z "$read_snapshot" \
      || -z "$pointer_first" ]]; then
    order_case='the log misses one step'
elif (( add_snapshot > first_part )); then
    order_case='the first post of the snapshot topic precedes its add-topic'
elif (( read_snapshot > pointer_first )); then
    order_case='one pointer precedes the readback of its topic'
elif [[ "$(sed -n "${snapshot_pointer_line}p" "$stub_state/log")" \
        != *'--topic register-index'* ]]; then
    order_case='the last post does not reach the index'
fi
if [[ "$order_case" == 'ok' ]]; then
    ok 'apply_posts_in_spec_order'
else
    bad 'apply_posts_in_spec_order' "$order_case"
fi

session_id="$(jq -r 'select(.kind == "session") | .messageId' \
    "$receipts/receipts.jsonl" 2> /dev/null | head -1)"
newest_id="$(jq -r '.id' \
    "$stub_state/messages/$snapshot_topic_name.jsonl" 2> /dev/null | tail -1)"
if [[ -n "$session_id" && "$session_id" == "$newest_id" ]]; then
    ok 'apply_session_range_is_newest'
else
    bad 'apply_session_range_is_newest' "session=$session_id newest=$newest_id"
fi

join_case='ok'
for topic in "$snapshot_topic_name" forward-register-first \
    forward-register-second; do
    for user in Sigma Theta Phi Mario; do
        grep -qx "$user $topic" "$stub_state/joins/all" 2> /dev/null \
            || join_case="$user did not join $topic"
    done
done
if [[ "$join_case" == 'ok' ]]; then
    ok 'apply_joins_all_four_users'
else
    bad 'apply_joins_all_four_users' "$join_case"
fi

pointer_case='ok'
pointer_text="$(jq -r 'select(.text | contains("CHAT-aaaaaaaa")) | .text' \
    "$stub_state/messages/register-index.jsonl" 2> /dev/null | head -1)"
if [[ -z "$pointer_text" ]]; then
    pointer_case='the index holds no pointer for the first entry'
else
    fields="$(printf '%s' "$pointer_text" | awk -F'|' '{print NF}')"
    [[ "$fields" == '4' ]] || pointer_case="the pointer holds $fields fields"
    [[ "$pointer_text" == *'CHAT-aaaaaaaa'* ]] \
        || pointer_case='the pointer names no key'
    [[ "$pointer_text" == *"$bundle_hash"* ]] \
        || pointer_case='the pointer names no bundle hash'
    [[ "$pointer_text" == *'First'* ]] \
        || pointer_case='the pointer names no title'
fi
if [[ "$pointer_case" == 'ok' ]]; then
    ok 'pointer_text_has_four_fields'
else
    bad 'pointer_text_has_four_fields' "$pointer_case"
fi

# A crash after a post and before its receipt. The retry sends the same
# request ID, and no topic takes a second copy of one message.
reset_stub
rm -rf "$receipts"
full_apply STUB_FAIL_AFTER_POST=3
crash_status=$status
full_apply
retry_status=$status
duplicate_case='ok'
if [[ $crash_status -ne 75 ]]; then
    duplicate_case="the first run gave exit $crash_status, not 75"
elif [[ $retry_status -ne 0 ]]; then
    duplicate_case="the second run gave exit $retry_status, not 0"
else
    while IFS= read -r file; do
        if [[ "$(jq -r '.id' "$file" | sort | uniq -d | grep -c .)" != '0' ]]; then
            duplicate_case="$(basename "$file") holds one message twice"
        fi
    done < <(find "$stub_state/messages" -type f)
fi
if [[ "$duplicate_case" == 'ok' ]]; then
    ok 'retry_after_crash_makes_no_duplicate'
else
    bad 'retry_after_crash_makes_no_duplicate' "$duplicate_case"
fi

# A crash between one post and its receipt leaves a stored message of no
# receipt. The next apply must adopt that message and complete. See the Sigma
# review of 2026-10-22.
reset_stub
rm -rf "$receipts"
full_apply
lost_kind='snapshot-part'
lost_id="$(jq -r --arg kind "$lost_kind" \
    'select(.kind == $kind and .index == 1) | .messageId' \
    "$receipts/receipts.jsonl" | head -1)"
jq -c --arg id "$lost_id" 'select(.messageId != $id)' \
    "$receipts/receipts.jsonl" > "$receipts/receipts.new"
mv "$receipts/receipts.new" "$receipts/receipts.jsonl"
before="$(grep -c . "$stub_state/messages/$snapshot_topic_name.jsonl")"
full_apply
lost_case='ok'
if [[ $status -ne 0 ]]; then
    lost_case="the retry gave exit $status, not 0: $out"
elif [[ "$(grep -c . "$stub_state/messages/$snapshot_topic_name.jsonl")" \
        != "$before" ]]; then
    lost_case='the retry added one message'
elif [[ -z "$(jq -r --arg id "$lost_id" \
        'select(.messageId == $id) | .messageId' \
        "$receipts/receipts.jsonl" | head -1)" ]]; then
    lost_case='the receipt of the stored message is still absent'
fi
if [[ "$lost_case" == 'ok' ]]; then
    ok 'retry_after_lost_receipt_completes'
else
    bad 'retry_after_lost_receipt_completes' "$lost_case"
fi

# A crash after the first post of the snapshot topic leaves a stored message
# and no receipt at all. The next apply must adopt that message and complete.
# See the second Sigma review of 2026-10-22.
reset_stub
rm -rf "$receipts"
full_apply
first_before="$(grep -c . "$stub_state/messages/$snapshot_topic_name.jsonl")"
rm -f "$receipts/receipts.jsonl"
full_apply
first_lost_case='ok'
if [[ $status -ne 0 ]]; then
    first_lost_case="the retry gave exit $status, not 0: $out"
elif [[ "$(grep -c . "$stub_state/messages/$snapshot_topic_name.jsonl")" \
        != "$first_before" ]]; then
    first_lost_case='the retry added one message to the snapshot topic'
elif [[ ! -s "$receipts/receipts.jsonl" ]]; then
    first_lost_case='the retry wrote no receipt'
fi
if [[ "$first_lost_case" == 'ok' ]]; then
    ok 'retry_after_lost_first_receipt_completes'
else
    bad 'retry_after_lost_first_receipt_completes' "$first_lost_case"
fi

# One changed receipt stops the retry.
reset_stub
rm -rf "$receipts"
full_apply
jq -c 'if .kind == "snapshot-header" then .messageId = "msg-999999" else . end' \
    "$receipts/receipts.jsonl" > "$receipts/receipts.new"
mv "$receipts/receipts.new" "$receipts/receipts.jsonl"
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
    CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
    "$install_home/bin/register-import" apply --bundle "$bundle" \
    --as Mario 2>&1)"
status=$?
if [[ $status -eq 1 ]]; then
    ok 'retry_with_changed_receipt_stops'
else
    bad 'retry_with_changed_receipt_stops' "exit $status, output: $out"
fi

# One malformed receipt stops the import before any call.
reset_stub
rm -rf "$receipts"
mkdir -p "$receipts"
printf 'not json\n' > "$receipts/receipts.jsonl"
full_apply
posts_made="$(grep -c 'post --topic' "$stub_state/log" 2> /dev/null)"
if [[ $status -eq 1 && "$posts_made" -eq 0 ]]; then
    ok 'malformed_receipt_stops'
else
    bad 'malformed_receipt_stops' \
        "exit $status, posts=$posts_made, output: $out"
fi

# A target topic that holds a message of no receipt stops the import.
reset_stub
rm -rf "$receipts"
full_apply
printf 'other\n' > "$work/other-text"
env STUB_STATE="$stub_state" CHAT_SHELL_USER=Mario "$stub_cli" post \
    --topic forward-register-first < "$work/other-text" > /dev/null 2>&1
full_apply
if [[ $status -eq 1 ]]; then
    ok 'unrelated_existing_topic_stops'
else
    bad 'unrelated_existing_topic_stops' "exit $status, output: $out"
fi

# Two hosts derive the same request IDs from one bundle.
other_state="$work/stub-state-2"
rm -rf "$other_state" "$receipts"
mkdir -p "$other_state"
reset_stub
full_apply
first_ids="$(jq -r '.requestId' "$receipts/receipts.jsonl" | sort)"
rm -rf "$receipts"
out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$other_state" \
    CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" \
    "$install_home/bin/register-import" apply --bundle "$bundle" \
    --as Mario 2>&1)"
status=$?
second_ids="$(jq -r '.requestId' "$receipts/receipts.jsonl" 2> /dev/null | sort)"
if [[ $status -eq 0 && -n "$first_ids" && "$first_ids" == "$second_ids" ]]; then
    ok 'two_hosts_get_equal_request_ids'
else
    bad 'two_hosts_get_equal_request_ids' "exit $status, output: $out"
fi

# -- verify --------------------------------------------------------------

# full_verify [extra env assignments]  Run verify against the current state.
full_verify() {
    out="$(env PATH="$stub_bin:$PATH" STUB_STATE="$stub_state" \
        CHAT_SHELL_CLI="$stub_cli" REGISTER_IMPORT_CQLSH="$cqlsh_stub" "$@" \
        "$install_home/bin/register-import" verify --bundle "$bundle" \
        --as Mario 2>&1)"
    status=$?
}

# One clean import stands behind every verify case.
reset_stub
rm -rf "$receipts"
full_apply
if [[ $status -ne 0 ]]; then
    printf 'FAIL the suite cannot import before verify: %s\n' "$out"
    exit 1
fi
cp -R "$stub_state" "$work/stub-state-good"

full_verify
verify_case='ok'
if [[ $status -ne 0 ]]; then
    verify_case="exit $status, output: $out"
else
    for pattern in 'source bytes' 'entries'; do
        [[ "$out" == *"$pattern"* ]] || verify_case="missing: $pattern"
    done
fi
if [[ "$verify_case" == 'ok' ]]; then
    ok 'verify_rebuilds_source_bytes'
else
    bad 'verify_rebuilds_source_bytes' "$verify_case"
fi

# One changed entry range breaks the entry check alone.
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
entry_id="$(jq -r 'select(.kind == "entry" and .topic == "forward-register-second") | .messageId' \
    "$receipts/receipts.jsonl" | head -1)"
jq -c --arg id "$entry_id" \
    'if .id == $id then .text = "other bytes\n" else . end' \
    "$stub_state/messages/forward-register-second.jsonl" \
    > "$stub_state/messages/forward-register-second.new"
mv "$stub_state/messages/forward-register-second.new" \
    "$stub_state/messages/forward-register-second.jsonl"
full_verify
if [[ $status -eq 1 && "$out" == *'forward-register-second'* ]]; then
    ok 'verify_checks_each_entry_range'
else
    bad 'verify_checks_each_entry_range' "exit $status, output: $out"
fi

# One changed snapshot part breaks the source rebuild.
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
part_id="$(jq -r 'select(.kind == "snapshot-part" and .index == 1) | .messageId' \
    "$receipts/receipts.jsonl" | head -1)"
jq -c --arg id "$part_id" \
    'if .id == $id then .text = "## Changed\n" else . end' \
    "$stub_state/messages/$snapshot_topic_name.jsonl" \
    > "$stub_state/messages/$snapshot_topic_name.new"
mv "$stub_state/messages/$snapshot_topic_name.new" \
    "$stub_state/messages/$snapshot_topic_name.jsonl"
full_verify
if [[ $status -eq 1 ]]; then
    ok 'verify_fails_on_changed_message'
else
    bad 'verify_fails_on_changed_message' "exit $status, output: $out"
fi

# A denied warning with status 0 is a failure.
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
full_verify STUB_DENY_TOPIC=1
if [[ $status -eq 1 && "$out" == *'denied'* ]]; then
    ok 'verify_fails_on_denied_warning_with_status_zero'
else
    bad 'verify_fails_on_denied_warning_with_status_zero' \
        "exit $status, output: $out"
fi

# The median of five session reads must meet its target.
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
full_verify STUB_READ_SLEEP=0.2 REGISTER_IMPORT_READ_TARGET_MS=50
slow_status=$status
slow_out="$out"
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
full_verify REGISTER_IMPORT_READ_TARGET_MS=10000
fast_status=$status
if [[ $slow_status -eq 1 && $fast_status -eq 0 ]]; then
    ok 'verify_median_gate'
else
    bad 'verify_median_gate' \
        "slow exit $slow_status, fast exit $fast_status, slow output: $slow_out"
fi

# A stalled read passes the wrapper budget and gives status 124.
rm -rf "$stub_state"
cp -R "$work/stub-state-good" "$stub_state"
full_verify STUB_STALL_READ=1 CHAT_REGISTER_DEADLINE_MS=1000
if [[ $status -eq 124 ]]; then
    ok 'verify_stall_returns_124'
else
    bad 'verify_stall_returns_124' "exit $status, output: $out"
fi

if ((failures > 0)); then
    echo "$failures case(s) failed"
    exit 1
fi
echo 'all register bootstrap cases passed'
