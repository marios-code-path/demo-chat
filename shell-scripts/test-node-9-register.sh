#!/usr/bin/env bash
# Offline contract checks for node-9/bin/register. No JVM, no container.
# See CHAT-jmnjvupr.
#
# chat-shell-cli is stubbed. Each stub call records its environment, its
# arguments, and its standard input. The tests never reach a core.
set -uo pipefail

cd "$(dirname "$0")/.."
repo_root="$PWD"
wrapper="$repo_root/node-9/bin/register"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

stub_dir="$work/bin"
mkdir -p "$stub_dir"
stub="$stub_dir/chat-shell-cli"

cat > "$stub" <<'STUB'
#!/usr/bin/env bash
# One stub call records itself and prints a canned reply.
set -uo pipefail
dir="${STUB_DIR:?Set STUB_DIR}"
count=0
[[ -f "$dir/count" ]] && count="$(cat "$dir/count")"
count=$((count + 1))
printf '%s\n' "$count" > "$dir/count"
call="$dir/call.$count"
env | sort > "$call.env"
printf '%s\n' "$@" > "$call.args"
cat > "$call.stdin"
if [[ -n "${CHAT_SHELL_PASSWORD_FILE:-}" && -r "$CHAT_SHELL_PASSWORD_FILE" ]]; then
    cat "$CHAT_SHELL_PASSWORD_FILE" > "$dir/pw.$count"
    perl -e 'printf "%o\n", (stat($ARGV[0]))[2] & 07777' "$CHAT_SHELL_PASSWORD_FILE" \
        > "$dir/pwmode.$count" 2> /dev/null || true
fi
if [[ -f "$dir/reply.$count" ]]; then cat "$dir/reply.$count"
elif [[ -f "$dir/reply" ]]; then cat "$dir/reply"
fi
if [[ -f "$dir/sleep.$count" ]]; then sleep "$(cat "$dir/sleep.$count")"
elif [[ -f "$dir/sleep" ]]; then sleep "$(cat "$dir/sleep")"
fi
status=0
if [[ -f "$dir/status.$count" ]]; then status="$(cat "$dir/status.$count")"
elif [[ -f "$dir/status" ]]; then status="$(cat "$dir/status")"
fi
exit "$status"
STUB
chmod +x "$stub"

secrets_dir="$work/secrets"
mkdir -p "$secrets_dir"
for name in Sigma Theta Phi; do
    printf 'fixture-%s-password\n' "$name" > "$secrets_dir/$name.password"
    chmod 0600 "$secrets_dir/$name.password"
done
secrets_file="$work/secrets.env"
printf 'CHAT_USER_MARIO_PASSWORD=fixture-mario-password\n' > "$secrets_file"
chmod 0600 "$secrets_file"

failures=0
checks=0
ok() { checks=$((checks + 1)); printf 'ok   %s\n' "$1"; }
bad() { checks=$((checks + 1)); failures=$((failures + 1)); printf 'FAIL %s: %s\n' "$1" "$2"; }

# reset_stub  Clear the record of every stub call, and its behaviour.
reset_stub() {
    rm -f "$stub_dir"/count "$stub_dir"/call.* "$stub_dir"/reply* "$stub_dir"/status* \
        "$stub_dir"/sleep* "$stub_dir"/pw.* "$stub_dir"/pwmode.*
}

# stub_reply TEXT  The text that the stub prints for every call.
stub_reply() { printf '%s\n' "$1" > "$stub_dir/reply"; }

# stub_reply_for N TEXT  The text that the stub prints for call N.
stub_reply_for() { printf '%s\n' "$2" > "$stub_dir/reply.$1"; }

# stub_status_for N STATUS  The status of call N.
stub_status_for() { printf '%s\n' "$2" > "$stub_dir/status.$1"; }

# stub_args N  The arguments of call N on one line.
stub_args() { tr '\n' ' ' 2>/dev/null < "$stub_dir/call.$1.args"; }

# calls  Print the number of stub calls.
calls() { [[ -f "$stub_dir/count" ]] && cat "$stub_dir/count" || echo 0; }

# run_wrapper NAME ARGS...  Run the wrapper with the stub.
# The input comes from $work/input. Extra environment words come from
# EXTRA_ENV, which is a space-separated list. Status lands in $status_of.
run_wrapper() {
    local name="$1"; shift
    [[ -f "$work/input" ]] || : > "$work/input"
    # shellcheck disable=SC2086
    ( env ${EXTRA_ENV:-} CHAT_SHELL_CLI="$stub" STUB_DIR="$stub_dir" \
      NODE9_SECRETS_DIR="$secrets_dir" \
      NODE9_SECRETS_FILE="${SECRETS_FILE:-$work/secrets.env}" \
      CHAT_REGISTER_DEADLINE_MS="${DEADLINE_MS:-30000}" \
      CHAT_REGISTER_CONFIRM_MS="${CONFIRM_MS:-10000}" \
      "$wrapper" "$@" ) > "$work/$name.out" 2> "$work/$name.err" < "$work/input"
    status_of=$?
}

# message_record ID TOPIC TIMESTAMP TEXT  One record line.
message_record() {
    jq -cn --arg id "$1" --arg topic "$2" --arg ts "$3" --arg text "$4" \
        '{id:$id, topic:$topic, sender:"Mario", timestamp:$ts, text:$text}'
}

# index_record ID TIMESTAMP TEXT  One register-index record line.
index_record() {
    message_record "$1" register-index "$2" "$3"
}

# denied_index  One denied-index warning line, as register-read prints it.
denied_index() {
    printf '{"type":"warning","kind":"denied-index","topic":"register-index","reason":"Access Denied"}'
}

# password_file_of CALL  The password file that call CALL used.
password_file_of() { sed -n 's/^CHAT_SHELL_PASSWORD_FILE=//p' "$stub_dir/call.$1.env" | head -n 1; }

# has_env CALL KEY VALUE  True when the call recorded KEY=VALUE.
has_env() {
    local call="$1" key="$2" value="$3"
    grep -qx "$key=$value" "$stub_dir/call.$call.env"
}

# every_line_typed FILE  True when every line of FILE is JSON with a type.
every_line_typed() {
    local file="$1" line
    [[ -s "$file" ]] || return 1
    while IFS= read -r line; do
        [[ -n "$line" ]] || return 1
        printf '%s' "$line" | jq -e 'has("type")' > /dev/null 2>&1 || return 1
    done < "$file"
}

# -- Argument checks ----------------------------------------------------

reset_stub
run_wrapper t_missing_as read
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'read_needs_as'
else
    bad 'read_needs_as' "status=$status_of calls=$(calls)"
fi

reset_stub
run_wrapper t_unknown_user read --as Hacker
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'unknown_user_is_status_2'
else
    bad 'unknown_user_is_status_2' "status=$status_of calls=$(calls)"
fi

reset_stub
run_wrapper t_extra read lobby extra --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'extra_argument_is_status_2'
else
    bad 'extra_argument_is_status_2' "status=$status_of calls=$(calls)"
fi

reset_stub
run_wrapper t_unsafe open CHAT-1 '..' '../evil' 'A title' --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'unsafe_short_name_is_status_2'
else
    bad 'unsafe_short_name_is_status_2' "status=$status_of calls=$(calls)"
fi

reset_stub
run_wrapper t_dash_open open - x 'A title' --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'open_dash_key_is_status_2'
else
    bad 'open_dash_key_is_status_2' "status=$status_of calls=$(calls)"
fi

reset_stub
run_wrapper t_dash_post post - --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'post_dash_key_is_status_2'
else
    bad 'post_dash_key_is_status_2' "status=$status_of calls=$(calls)"
fi

# -- Typed JSON on both read forms -------------------------------------

reset_stub
stub_reply '{"id":"m1","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:00Z","text":"one"}
{"id":"m2","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:01Z","text":"two"}'
run_wrapper t_read read --as Theta
if [[ "$status_of" -eq 0 ]] && every_line_typed "$work/t_read.out" \
   && [[ "$(wc -l < "$work/t_read.out" | tr -d ' ')" -eq 2 ]] \
   && [[ ! -s "$work/t_read.err" ]]; then
    ok 'read_session_is_typed'
else
    bad 'read_session_is_typed' "status=$status_of lines=$(wc -l < "$work/t_read.out" | tr -d ' ')"
fi

reset_stub
stub_reply '{"id":"m3","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:02Z","text":"three"}'
run_wrapper t_topic read forward-register-x --as Theta
if [[ "$status_of" -eq 0 ]] && every_line_typed "$work/t_topic.out" \
   && grep -q 'messages' "$stub_dir/call.1.args" \
   && grep -q 'records' "$stub_dir/call.1.args" \
   && grep -q 'forward-register-x' "$stub_dir/call.1.args"; then
    ok 'read_topic_is_typed'
else
    bad 'read_topic_is_typed' "status=$status_of args=$(tr '\n' ' ' 2>/dev/null < "$stub_dir/call.1.args")"
fi

reset_stub
stub_reply '{"type":"warning","kind":"duplicate-pointer","key":"CHAT-1","kept":{"topic":"a","id":"m1"},"ignored":{"topic":"b","id":"m2"}}'
run_wrapper t_warning read --as Theta
if [[ "$status_of" -eq 0 ]] && every_line_typed "$work/t_warning.out" \
   && jq -e 'select(.type=="warning")' "$work/t_warning.out" > /dev/null 2>&1; then
    ok 'warning_is_typed'
else
    bad 'warning_is_typed' "status=$status_of out=$(head -1 "$work/t_warning.out" 2>/dev/null)"
fi

# -- The owner login ----------------------------------------------------

# The owner login reads the rendered password in secrets.env. No client
# copy of the Mario password exists. See decision 37.
reset_stub
stub_reply '{"id":"m7","topic":"forward-register-x","sender":"Mario","timestamp":"2026-10-20T00:00:06Z","text":"seven"}'
run_wrapper t_mario read --as Mario
mario_file="$(password_file_of 1)"
if [[ "$status_of" -eq 0 ]] \
   && [[ "$(cat "$stub_dir/pw.1" 2> /dev/null)" == 'fixture-mario-password' ]] \
   && [[ "$(cat "$stub_dir/pwmode.1" 2> /dev/null)" == '600' ]]; then
    ok 'mario_reads_the_rendered_password'
else
    bad 'mario_reads_the_rendered_password' \
        "status=$status_of file=$mario_file pw=$(cat "$stub_dir/pw.1" 2> /dev/null) mode=$(cat "$stub_dir/pwmode.1" 2> /dev/null)"
fi

# A secrets file without the owner value is a login failure.
reset_stub
printf 'CHAT_USER_SIGMA_PASSWORD=only\n' > "$work/secrets-nomario.env"
SECRETS_FILE="$work/secrets-nomario.env" run_wrapper t_mario_missing read --as Mario
SECRETS_FILE=
if [[ "$status_of" -eq 77 ]] && [[ "$(calls)" -eq 0 ]] && [[ ! -s "$work/t_mario_missing.out" ]]; then
    ok 'mario_without_a_rendered_value_is_77'
else
    bad 'mario_without_a_rendered_value_is_77' \
        "status=$status_of calls=$(calls) err=$(head -1 "$work/t_mario_missing.err")"
fi

# A missing secrets file is a login failure.
reset_stub
SECRETS_FILE="$work/no-such.env" run_wrapper t_mario_nofile read --as Mario
SECRETS_FILE=
if [[ "$status_of" -eq 77 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'mario_without_a_secrets_file_is_77'
else
    bad 'mario_without_a_secrets_file_is_77' \
        "status=$status_of calls=$(calls) err=$(head -1 "$work/t_mario_nofile.err")"
fi

# -- Post ---------------------------------------------------------------

# prepare_post  Stub the pointer read, the join, the receipt, and one poll
# hit for message m9.
prepare_post() {
    stub_reply_for 1 "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | forward-register-x | task | A title')"
    stub_reply_for 2 'Joined forward-register-x'
    stub_reply_for 3 '{"messageId":"m9","commandId":"c9","requestId":"r9","outcome":"ok"}'
    stub_reply_for 4 "$(message_record m9 forward-register-x 2026-10-20T00:01:00Z 'entry')"
}

reset_stub
printf ' \n\t\n\n' > "$work/input"
run_wrapper t_blank post CHAT-1 --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'whitespace_only_post_is_status_2'
else
    bad 'whitespace_only_post_is_status_2' "status=$status_of calls=$(calls)"
fi

reset_stub
prepare_post
printf 'Hand-off: CHAT-1\nState: no final newline' > "$work/input"
run_wrapper t_nonl post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 4 ]] \
   && cmp -s "$work/input" "$stub_dir/call.3.stdin" \
   && jq -e '.type=="post" and .message_id=="m9" and .command_id=="c9" and .request_id=="r9"' "$work/t_nonl.out" > /dev/null 2>&1; then
    ok 'post_keeps_no_final_newline'
else
    bad 'post_keeps_no_final_newline' "status=$status_of calls=$(calls) bytes=$(wc -c 2>/dev/null < "$stub_dir/call.3.stdin")"
fi

reset_stub
prepare_post
printf 'Hand-off: CHAT-1\r\nState: crlf\r\nNext: done\r\n' > "$work/input"
run_wrapper t_crlf post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 4 ]] \
   && cmp -s "$work/input" "$stub_dir/call.3.stdin"; then
    ok 'post_keeps_crlf_multiline'
else
    bad 'post_keeps_crlf_multiline' "status=$status_of calls=$(calls)"
fi

reset_stub
prepare_post
printf 'line one\nline two\nline three\n' > "$work/input"
run_wrapper t_multi post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 4 ]] \
   && cmp -s "$work/input" "$stub_dir/call.3.stdin"; then
    ok 'post_keeps_multiple_lines'
else
    bad 'post_keeps_multiple_lines' "status=$status_of calls=$(calls)"
fi

reset_stub
prepare_post
printf 'Hand-off: CHAT-1\nState: café — ünïcode\n' > "$work/input"
run_wrapper t_utf8 post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && cmp -s "$work/input" "$stub_dir/call.3.stdin"; then
    ok 'post_keeps_utf8'
else
    bad 'post_keeps_utf8' "status=$status_of bytes=$(wc -c 2>/dev/null < "$stub_dir/call.3.stdin")"
fi

reset_stub
prepare_post
printf 'State: joined\n' > "$work/input"
run_wrapper t_post_join post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] \
   && grep -q 'join forward-register-x' <<< "$(stub_args 2)" \
   && grep -q 'post --topic forward-register-x' <<< "$(stub_args 3)"; then
    ok 'post_joins_the_pointer_topic'
else
    bad 'post_joins_the_pointer_topic' "status=$status_of join=$(stub_args 2) post=$(stub_args 3)"
fi

reset_stub
stub_reply_for 1 ''
printf 'State: orphan\n' > "$work/input"
run_wrapper t_post_orphan post CHAT-9 --as Theta
if [[ "$status_of" -eq 1 ]] && [[ "$(calls)" -eq 1 ]] \
   && [[ ! -s "$work/t_post_orphan.out" ]] \
   && grep -q 'CHAT-9' "$work/t_post_orphan.err"; then
    ok 'post_without_a_pointer_fails'
else
    bad 'post_without_a_pointer_fails' "status=$status_of calls=$(calls) err=$(head -1 "$work/t_post_orphan.err")"
fi

# A denied index joins register-index once, then reads again.
reset_stub
stub_reply_for 1 "$(denied_index)"
stub_reply_for 2 'Joined register-index'
stub_reply_for 3 "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | forward-register-x | task | A title')"
stub_reply_for 4 'Joined forward-register-x'
stub_reply_for 5 '{"messageId":"m9","commandId":"c9","requestId":"r9","outcome":"ok"}'
stub_reply_for 6 "$(message_record m9 forward-register-x 2026-10-20T00:01:00Z 'entry')"
printf 'State: after a join\n' > "$work/input"
run_wrapper t_post_denied_join post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 6 ]] \
   && grep -q 'join register-index' <<< "$(stub_args 2)" \
   && jq -e '.type=="post" and .message_id=="m9"' "$work/t_post_denied_join.out" > /dev/null 2>&1; then
    ok 'post_joins_after_a_denied_index'
else
    bad 'post_joins_after_a_denied_index' "status=$status_of calls=$(calls) err=$(head -1 "$work/t_post_denied_join.err")"
fi

# A second denial stops the post with status 1, before any entry.
reset_stub
stub_reply_for 1 "$(denied_index)"
stub_reply_for 2 'Joined register-index'
stub_reply_for 3 "$(denied_index)"
printf 'State: denied\n' > "$work/input"
run_wrapper t_post_denied_twice post CHAT-1 --as Theta
if [[ "$status_of" -eq 1 ]] && [[ "$(calls)" -eq 3 ]] \
   && [[ ! -s "$work/t_post_denied_twice.out" ]] \
   && grep -q 'denied' "$work/t_post_denied_twice.err" \
   && ! grep -q 'Run register open first' "$work/t_post_denied_twice.err"; then
    ok 'post_stops_when_the_index_stays_denied'
else
    bad 'post_stops_when_the_index_stays_denied' "status=$status_of calls=$(calls) err=$(head -1 "$work/t_post_denied_twice.err")"
fi

# A missed first poll and a failed read both allow a later hit.
reset_stub
prepare_post
stub_reply_for 4 ''
stub_reply_for 5 "$(message_record m9 forward-register-x 2026-10-20T00:01:01Z entry)"
printf 'State: late\n' > "$work/input"
run_wrapper t_late post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 5 ]]; then
    ok 'post_confirms_after_a_late_read'
else
    bad 'post_confirms_after_a_late_read' "status=$status_of calls=$(calls)"
fi

reset_stub
prepare_post
stub_reply_for 4 ''
stub_status_for 4 1
stub_reply_for 5 "$(message_record m9 forward-register-x 2026-10-20T00:01:02Z entry)"
printf 'State: retry read\n' > "$work/input"
run_wrapper t_failed_poll post CHAT-1 --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 5 ]]; then
    ok 'post_tolerates_a_failed_read'
else
    bad 'post_tolerates_a_failed_read' "status=$status_of calls=$(calls)"
fi

# No poll hit before the confirm window returns 75 and names the receipt.
reset_stub
prepare_post
stub_reply_for 4 ''
printf 'State: unconfirmed\n' > "$work/input"
CONFIRM_MS=400 run_wrapper t_unconfirmed post CHAT-1 --as Theta
CONFIRM_MS=
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_unconfirmed.out" ]] \
   && grep -q 'm9' "$work/t_unconfirmed.err" \
   && grep -q 'r9' "$work/t_unconfirmed.err"; then
    ok 'post_without_a_read_is_75'
else
    bad 'post_without_a_read_is_75' "status=$status_of out=$(head -1 "$work/t_unconfirmed.out") err=$(head -1 "$work/t_unconfirmed.err")"
fi

reset_stub
stub_reply_for 1 "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | forward-register-x | task | A title')"
stub_reply_for 2 'Joined forward-register-x'
stub_reply_for 3 '{"outcome":"ok"}'
printf 'State: bad receipt\n' > "$work/input"
run_wrapper t_bad_receipt post CHAT-1 --requestId r1 --as Theta
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_bad_receipt.out" ]] \
   && grep -q 'r1' "$work/t_bad_receipt.err"; then
    ok 'post_without_a_message_id_is_75'
else
    bad 'post_without_a_message_id_is_75' "status=$status_of err=$(head -1 "$work/t_bad_receipt.err")"
fi

# A retry with the receipt request id reuses it and adds no second entry.
reset_stub
prepare_post
stub_reply_for 4 ''
printf 'State: retry\n' > "$work/input"
CONFIRM_MS=400 run_wrapper t_retry_one post CHAT-1 --requestId r1 --as Theta
retry_status_one=$status_of
retry_args_one="$(stub_args 3)"
reset_stub
prepare_post
CONFIRM_MS=400 run_wrapper t_retry_two post CHAT-1 --requestId r1 --as Theta
CONFIRM_MS=
if [[ "$retry_status_one" -eq 75 ]] && [[ "$status_of" -eq 0 ]] \
   && grep -q -- '--requestId r1' <<< "$retry_args_one" \
   && grep -q -- '--requestId r1' <<< "$(stub_args 3)"; then
    ok 'post_retry_reuses_the_request_id'
else
    bad 'post_retry_reuses_the_request_id' "first=$retry_status_one args=$retry_args_one second=$status_of"
fi

for bad in 'a b' ''; do
    reset_stub
    printf 'State: x\n' > "$work/input"
    run_wrapper t_bad_request post CHAT-1 --requestId "$bad" --as Theta
    if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
        ok "post_rejects_request_id_${bad//[^a-zA-Z0-9]/_}"
    else
        bad "post_rejects_request_id_${bad//[^a-zA-Z0-9]/_}" "status=$status_of calls=$(calls)"
    fi
done

reset_stub
printf 'State: x\n' > "$work/input"
run_wrapper t_post_extra post CHAT-1 x --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'post_extra_argument_is_status_2'
else
    bad 'post_extra_argument_is_status_2' "status=$status_of calls=$(calls)"
fi

# The wrapper generates an id. It never sends the shell's "_".
reset_stub
prepare_post
printf 'State: x\n' > "$work/input"
run_wrapper t_auto_id post CHAT-1 --as Theta
auto_args="$(stub_args 3)"
auto_id="$(sed -n 's/.*--requestId \([^ ]*\).*/\1/p' <<< "$auto_args")"
if [[ "$status_of" -eq 0 ]] && [[ -n "$auto_id" ]] && [[ "$auto_id" != '_' ]] \
   && grep -q -x -- "$auto_id" "$stub_dir/call.3.args"; then
    ok 'post_generates_a_request_id'
else
    bad 'post_generates_a_request_id' "status=$status_of args=$auto_args"
fi

reset_stub
printf 'State: x\n' > "$work/input"
run_wrapper t_underscore post CHAT-1 --requestId _ --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'post_rejects_the_shell_new_id'
else
    bad 'post_rejects_the_shell_new_id' "status=$status_of calls=$(calls)"
fi

# A shell failure before the receipt names the id for recovery.
reset_stub
prepare_post
stub_status_for 3 1
stub_reply_for 3 'register: the post failed'
printf 'State: fail\n' > "$work/input"
run_wrapper t_post_fail post CHAT-1 --as Theta
fail_id="$(sed -n 's/.*--requestId \([^ ]*\).*/\1/p' "$work/t_post_fail.err" | head -1 | tr -d '.')"
if [[ "$status_of" -eq 1 ]] && [[ ! -s "$work/t_post_fail.out" ]] \
   && [[ -n "$fail_id" ]] && grep -q -x -- "--requestId" "$stub_dir/call.3.args" \
   && grep -q -x -- "$fail_id" "$stub_dir/call.3.args"; then
    ok 'post_failure_names_the_request_id'
else
    bad 'post_failure_names_the_request_id' "status=$status_of err=$(head -1 "$work/t_post_fail.err")"
fi

# A poll hit after the cap does not confirm the post.
reset_stub
prepare_post
printf '0.4\n' > "$stub_dir/sleep"
printf 'State: slow\n' > "$work/input"
CONFIRM_MS=300 run_wrapper t_slow_confirm post CHAT-1 --as Theta
CONFIRM_MS=
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_slow_confirm.out" ]]; then
    ok 'post_hit_after_the_cap_is_75'
else
    bad 'post_hit_after_the_cap_is_75' "status=$status_of out=$(head -1 "$work/t_slow_confirm.out")"
fi

# The generated id in the 75 line recovers the post on a retry.
reset_stub
prepare_post
stub_reply_for 4 ''
printf 'State: auto retry\n' > "$work/input"
CONFIRM_MS=400 run_wrapper t_auto_retry_one post CHAT-1 --as Theta
CONFIRM_MS=
auto_retry_status=$status_of
auto_retry_id="$(sed -n "s/.*--requestId \([^ ]*\).*/\1/p" "$work/t_auto_retry_one.err" | head -1 | tr -d ".")"
reset_stub
prepare_post
CONFIRM_MS=400 run_wrapper t_auto_retry_two post CHAT-1 --requestId "$auto_retry_id" --as Theta
CONFIRM_MS=
if [[ "$auto_retry_status" -eq 75 ]] && [[ -n "$auto_retry_id" ]] \
   && [[ "$status_of" -eq 0 ]] \
   && grep -q -x -- "$auto_retry_id" "$stub_dir/call.3.args"; then
    ok 'post_retry_uses_the_generated_id'
else
    bad 'post_retry_uses_the_generated_id' "first=$auto_retry_status id=$auto_retry_id second=$status_of"
fi

# A confirmation read gets only the time left in the window. A slow read
# is stopped at the window, so the post does not wait for the wrapper limit.
reset_stub
prepare_post
stub_reply_for 4 ''
printf '3\n' > "$stub_dir/sleep.4"
printf 'State: window\n' > "$work/input"
started=$SECONDS
CONFIRM_MS=300 run_wrapper t_window post CHAT-1 --as Theta
elapsed=$((SECONDS - started))
CONFIRM_MS=
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_window.out" ]] \
   && [[ "$elapsed" -lt 2 ]] && [[ "$(calls)" -eq 4 ]]; then
    ok 'post_read_stops_at_the_window'
else
    bad 'post_read_stops_at_the_window' \
        "status=$status_of elapsed=${elapsed}s calls=$(calls) out=$(head -1 "$work/t_window.out")"
fi

# The pause between reads also stops at the window. The time left is
# measured after the read, not before it.
stamp_ms() { perl -MTime::HiRes=time -e 'printf "%d", time * 1000'; }

reset_stub
prepare_post
stub_reply_for 4 ''
printf '0.9\n' > "$stub_dir/sleep.4"
printf 'State: pause\n' > "$work/input"
started_ms="$(stamp_ms)"
CONFIRM_MS=1000 run_wrapper t_pause post CHAT-1 --as Theta
elapsed_ms=$(( $(stamp_ms) - started_ms ))
CONFIRM_MS=
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_pause.out" ]] \
   && (( elapsed_ms < 1400 )) && [[ "$(calls)" -eq 4 ]]; then
    ok 'post_pause_stops_at_the_window'
else
    bad 'post_pause_stops_at_the_window' \
        "status=$status_of elapsed=${elapsed_ms}ms calls=$(calls) out=$(head -1 "$work/t_pause.out")"
fi

# -- The environment of every shell call --------------------------------

reset_stub
stub_reply '{"id":"m4","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:03Z","text":"four"}'
run_wrapper t_env read --as Theta
if [[ "$status_of" -eq 0 ]] \
   && has_env 1 CORE_PORT 6890 \
   && has_env 1 CORE_MGMT_PORT 6891 \
   && has_env 1 CHAT_SHELL_KEY_TYPE uuid \
   && has_env 1 CHAT_SHELL_NODE_ID 9; then
    ok 'shell_call_uses_node_9'
else
    bad 'shell_call_uses_node_9' "status=$status_of env=$(tr '\n' ' ' 2>/dev/null < "$stub_dir/call.1.env" | cut -c1-200)"
fi

# A caller cannot move the wrapper to another core.
reset_stub
stub_reply '{"id":"m5","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:04Z","text":"five"}'
EXTRA_ENV='CORE_PORT=6990 CORE_MGMT_PORT=6991 CHAT_SHELL_NODE_ID=16 CHAT_SHELL_KEY_TYPE=long'
run_wrapper t_redirect read --as Theta
EXTRA_ENV=
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'caller_cannot_redirect_to_node_16'
else
    bad 'caller_cannot_redirect_to_node_16' "status=$status_of calls=$(calls)"
fi

# -- The deadline covers the whole wrapper ------------------------------

reset_stub
stub_reply '{"id":"m6","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:05Z","text":"six"}'
printf '5\n' > "$stub_dir/sleep"
started=$SECONDS
DEADLINE_MS=1000 run_wrapper t_deadline read --as Theta
elapsed=$((SECONDS - started))
if [[ "$status_of" -eq 124 ]] && [[ ! -s "$work/t_deadline.out" ]] && [[ "$elapsed" -lt 5 ]]; then
    ok 'deadline_covers_the_wrapper'
else
    bad 'deadline_covers_the_wrapper' "status=$status_of elapsed=${elapsed}s bytes=$(wc -c < "$work/t_deadline.out")"
fi

# The contract gives one line on standard error for a timeout. A timer that
# wakes after the work directory is gone must not add a second line.
if [[ "$(grep -c . "$work/t_deadline.err")" -eq 1 ]] \
   && grep -q 'passed its 1000 ms limit' "$work/t_deadline.err"; then
    ok 'deadline_writes_one_line_on_stderr'
else
    bad 'deadline_writes_one_line_on_stderr' "err=$(tr '\n' '|' < "$work/t_deadline.err")"
fi

# -- Open ---------------------------------------------------------------

for short in 'A|B' 'a b' '-x' 'x/../y' '.hidden'; do
    reset_stub
    run_wrapper t_unsafe open CHAT-1 "$short" 'A title' --as Theta
    if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
        ok "open_rejects_short_name_${short//[^a-zA-Z0-9]/_}"
    else
        bad "open_rejects_short_name_${short//[^a-zA-Z0-9]/_}" "status=$status_of calls=$(calls)"
    fi
done

reset_stub
stub_reply "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | topic-a | task | A')
$(index_record 2 2026-10-20T00:00:01Z 'CHAT-1 | topic-b | task | B')"
run_wrapper t_open_exists open CHAT-1 new-topic 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 1 ]] \
   && jq -e '.type=="open" and .topic=="topic-a" and .state=="exists"' "$work/t_open_exists.out" > /dev/null 2>&1; then
    ok 'open_existing_fp_id_selects_earliest'
else
    bad 'open_existing_fp_id_selects_earliest' "status=$status_of calls=$(calls) out=$(head -1 "$work/t_open_exists.out")"
fi

reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room forward-register-x (id 7)'
stub_reply_for 3 'Joined forward-register-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 "$(index_record m1 2026-10-20T00:00:02Z 'CHAT-1 | forward-register-x | task | A title')"
run_wrapper t_open_create open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 5 ]] \
   && grep -q 'add-topic' <<< "$(stub_args 2)" \
   && grep -q 'post' <<< "$(stub_args 4)" \
   && grep -qx 'CHAT-1 | forward-register-x | | A title' "$stub_dir/call.4.stdin" \
   && jq -e '.type=="open" and .state=="created" and .topic=="forward-register-x" and .pointer_id=="m1"' "$work/t_open_create.out" > /dev/null 2>&1; then
    ok 'open_creates_topic_and_confirms_pointer'
else
    bad 'open_creates_topic_and_confirms_pointer' "status=$status_of calls=$(calls) out=$(head -1 "$work/t_open_create.out") stdin=$(head -1 "$stub_dir/call.4.stdin" 2>/dev/null)"
fi

# A readable index joins nothing. The first read is the only call.
reset_stub
stub_reply "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | topic-a | task | A')"
run_wrapper t_open_no_join open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 1 ]] \
   && ! grep -q 'join' <<< "$(stub_args 1)"; then
    ok 'open_does_not_join_a_readable_index'
else
    bad 'open_does_not_join_a_readable_index' "status=$status_of calls=$(calls) args=$(stub_args 1)"
fi

# A denied index joins register-index once, then reads again.
reset_stub
stub_reply_for 1 "$(denied_index)"
stub_reply_for 2 'Joined register-index'
stub_reply_for 3 "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | topic-a | task | A')"
run_wrapper t_open_denied_join open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 3 ]] \
   && grep -q 'join register-index' <<< "$(stub_args 2)" \
   && jq -e '.type=="open" and .state=="exists" and .topic=="topic-a"' "$work/t_open_denied_join.out" > /dev/null 2>&1; then
    ok 'open_joins_after_a_denied_index'
else
    bad 'open_joins_after_a_denied_index' "status=$status_of calls=$(calls) out=$(head -1 "$work/t_open_denied_join.out") err=$(head -1 "$work/t_open_denied_join.err")"
fi

# A second denial stops the open with status 1, before any create.
reset_stub
stub_reply_for 1 "$(denied_index)"
stub_reply_for 2 'Joined register-index'
stub_reply_for 3 "$(denied_index)"
run_wrapper t_open_denied_twice open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 1 ]] && [[ "$(calls)" -eq 3 ]] \
   && [[ ! -s "$work/t_open_denied_twice.out" ]] \
   && grep -q 'denied' "$work/t_open_denied_twice.err" \
   && grep -q 'Access Denied' "$work/t_open_denied_twice.err"; then
    ok 'open_stops_when_the_index_stays_denied'
else
    bad 'open_stops_when_the_index_stays_denied' "status=$status_of calls=$(calls) bytes=$(wc -c 2>/dev/null < "$work/t_open_denied_twice.out") err=$(head -1 "$work/t_open_denied_twice.err")"
fi

reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room forward-register-x (id 7)'
stub_reply_for 3 'Joined forward-register-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 "$(index_record m0 2026-10-20T00:00:00Z 'CHAT-1 | topic-a | task | A')
$(index_record m1 2026-10-20T00:00:02Z 'CHAT-1 | forward-register-x | task | A title')"
run_wrapper t_open_race open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] \
   && jq -e '.type=="open" and .topic=="topic-a" and .pointer_id=="m0"' "$work/t_open_race.out" > /dev/null 2>&1; then
    ok 'open_race_selects_earliest_pointer'
else
    bad 'open_race_selects_earliest_pointer' "status=$status_of out=$(head -1 "$work/t_open_race.out")"
fi

reset_stub
stub_reply "$(index_record 9 2026-10-20T00:00:00Z 'CHAT-2 | forward-register-x | task | Another')"
run_wrapper t_open_conflict open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -ne 0 ]] && grep -q 'CHAT-2' "$work/t_open_conflict.err" && [[ "$(calls)" -eq 1 ]]; then
    ok 'open_topic_name_conflict_names_other_id'
else
    bad 'open_topic_name_conflict_names_other_id' "status=$status_of err=$(head -1 "$work/t_open_conflict.err")"
fi

reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room forward-register-x (id 7)'
stub_reply_for 3 'Joined forward-register-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 ''
run_wrapper t_open_unconfirmed open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_open_unconfirmed.out" ]]; then
    ok 'open_without_pointer_readback_is_75'
else
    bad 'open_without_pointer_readback_is_75' "status=$status_of bytes=$(wc -c 2>/dev/null < "$work/t_open_unconfirmed.out")"
fi

reset_stub
stub_status_for 1 1
run_wrapper t_open_down open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -ne 0 && "$status_of" -ne 75 ]] && [[ ! -s "$work/t_open_down.out" ]]; then
    ok 'open_fails_when_the_index_read_fails'
else
    bad 'open_fails_when_the_index_read_fails' "status=$status_of"
fi

long_key="$(printf 'k%.0s' {1..65})"
bad_keys=('A|x' '' ' padded ' '-' "$long_key" 'team\ops')
bad_names=(delimiter empty padded dash too_long backslash)
for i in "${!bad_keys[@]}"; do
    reset_stub
    run_wrapper "t_bad_key_${bad_names[$i]}" open "${bad_keys[$i]}" x 'A title' --as Theta
    if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
        ok "open_rejects_key_${bad_names[$i]}"
    else
        bad "open_rejects_key_${bad_names[$i]}" "status=$status_of calls=$(calls)"
    fi
done

# A key that this project does not use is valid. The tool does not know FP.
reset_stub
stub_reply "$(index_record 1 2026-10-20T00:00:00Z 'PROJ-7 | topic-a | | A')"
run_wrapper t_generic_key open PROJ-7 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(jq -r '.key' "$work/t_generic_key.out")" == 'PROJ-7' ]]; then
    ok 'a_key_from_another_project_is_valid'
else
    bad 'a_key_from_another_project_is_valid' "status=$status_of err=$(head -1 "$work/t_generic_key.err")"
fi

for title in 'A|B' $'A\tB' $'A\nB' ''; do
    reset_stub
    run_wrapper t_bad_title open CHAT-1 x "$title" --as Theta
    if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
        ok "open_rejects_title_${title//[^a-zA-Z0-9]/_}"
    else
        bad "open_rejects_title_${title//[^a-zA-Z0-9]/_}" "status=$status_of calls=$(calls)"
    fi
done

# A join failure after a denial stops the open before the post.
reset_stub
stub_reply_for 1 "$(denied_index)"
stub_status_for 2 1
run_wrapper t_open_join_fail open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -ne 0 ]] && [[ ! -s "$work/t_open_join_fail.out" ]] \
   && [[ "$(calls)" -eq 2 ]]; then
    ok 'open_fails_when_the_index_join_fails'
else
    bad 'open_fails_when_the_index_join_fails' "status=$status_of calls=$(calls)"
fi

# Two pointers name one topic for different fp IDs.
reset_stub
stub_reply "$(index_record 8 2026-10-20T00:00:00Z 'CHAT-2 | forward-register-x | task | Another')
$(index_record 9 2026-10-20T00:00:01Z 'CHAT-3 | forward-register-x | task | Third')"
run_wrapper t_open_dup_topic open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -ne 0 ]] && grep -q 'CHAT-2' "$work/t_open_dup_topic.err" \
   && [[ "$(calls)" -eq 1 ]]; then
    ok 'open_fails_when_two_ids_share_a_topic_name'
else
    bad 'open_fails_when_two_ids_share_a_topic_name' "status=$status_of calls=$(calls) err=$(head -1 "$work/t_open_dup_topic.err")"
fi

# A concurrent open wins the create, so this open returns its pointer.
reset_stub
stub_reply_for 1 ''
stub_status_for 2 1
stub_reply_for 3 "$(index_record m0 2026-10-20T00:00:00Z 'CHAT-1 | forward-register-other | task | Other')"
run_wrapper t_open_lost_race open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 3 ]] \
   && jq -e '.type=="open" and .state=="exists" and .topic=="forward-register-other" and .pointer_id=="m0"' "$work/t_open_lost_race.out" > /dev/null 2>&1; then
    ok 'open_create_race_returns_the_other_pointer'
else
    bad 'open_create_race_returns_the_other_pointer' "status=$status_of calls=$(calls) out=$(head -1 "$work/t_open_lost_race.out") err=$(head -1 "$work/t_open_lost_race.err")"
fi

# An older pointer without the new post id does not confirm the open.
reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room forward-register-x (id 7)'
stub_reply_for 3 'Joined forward-register-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 "$(index_record m0 2026-10-20T00:00:00Z 'CHAT-1 | topic-a | task | A')"
run_wrapper t_open_id_absent open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 75 ]] && [[ ! -s "$work/t_open_id_absent.out" ]]; then
    ok 'open_readback_without_the_new_id_is_75'
else
    bad 'open_readback_without_the_new_id_is_75' "status=$status_of out=$(head -1 "$work/t_open_id_absent.out")"
fi

# A failed create keeps its status when the recovery read finds nothing.
reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'register: the topic exists'
stub_status_for 2 1
stub_reply_for 3 ''
run_wrapper t_open_create_status open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 1 ]] && [[ ! -s "$work/t_open_create_status.out" ]]; then
    ok 'open_failed_create_keeps_its_status'
else
    bad 'open_failed_create_keeps_its_status' "status=$status_of bytes=$(wc -c 2>/dev/null < "$work/t_open_create_status.out")"
fi

# The deadline counts every shell call, not each call alone.
reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room forward-register-x (id 7)'
stub_reply_for 3 'Joined forward-register-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 "$(index_record m1 2026-10-20T00:00:02Z 'CHAT-1 | forward-register-x | task | A title')"
printf '0.4\n' > "$stub_dir/sleep"
DEADLINE_MS=700 run_wrapper t_open_deadline open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -eq 124 ]] && [[ ! -s "$work/t_open_deadline.out" ]]; then
    ok 'deadline_covers_every_open_call'
else
    bad 'deadline_covers_every_open_call' "status=$status_of calls=$(calls) bytes=$(wc -c 2>/dev/null < "$work/t_open_deadline.out")"
fi

# A malformed pointer stops the open.
reset_stub
stub_reply "$(index_record 1 2026-10-20T00:00:00Z 'CHAT-1 | topic-a')"
run_wrapper t_open_malformed open CHAT-1 x 'A title' --as Theta
if [[ "$status_of" -ne 0 ]] && [[ ! -s "$work/t_open_malformed.out" ]]; then
    ok 'open_fails_on_a_malformed_pointer'
else
    bad 'open_fails_on_a_malformed_pointer' "status=$status_of"
fi

# A stall before setup ends stays inside the wrapper limit.
reset_stub
stub_reply '{"id":"m7","topic":"forward-register-x","sender":"Theta","timestamp":"2026-10-20T00:00:06Z","text":"seven"}'
mkdir -p "$work/slowbin"
printf '#!/usr/bin/env bash\nsleep 0.4\nexec /usr/bin/mktemp "$@"\n' > "$work/slowbin/mktemp"
chmod +x "$work/slowbin/mktemp"
EXTRA_ENV="PATH=$work/slowbin:/usr/bin:/bin"
DEADLINE_MS=200 run_wrapper t_slow_mktemp read --as Theta
EXTRA_ENV=
if [[ "$status_of" -eq 124 ]] && [[ ! -s "$work/t_slow_mktemp.out" ]]; then
    ok 'deadline_covers_a_stall_before_setup'
else
    bad 'deadline_covers_a_stall_before_setup' "status=$status_of bytes=$(wc -c 2>/dev/null < "$work/t_slow_mktemp.out")"
fi

# -- The shared decoder fixtures ---------------------------------------
# chat-shell/src/test/resources/register/pointer-lines.tsv holds the cases.
# The Kotlin read reads the same file. Both decoders must agree on each row.

fixtures="$repo_root/chat-shell/src/test/resources/register/pointer-lines.tsv"
if [[ -r "$fixtures" ]]; then
    while IFS=$'\t' read -r fx_name fx_line fx_expected; do
        [[ -n "$fx_name" && "$fx_name" != '#'* ]] || continue
        reset_stub
        stub_reply "$(index_record 1 2026-10-20T00:00:00Z "$fx_line")"
        if [[ "$fx_expected" == 'reject' ]]; then
            run_wrapper "t_fx_$fx_name" open fx-key x 'A title' --as Theta
            if [[ "$status_of" -eq 1 ]] && [[ ! -s "$work/t_fx_$fx_name.out" ]] \
               && grep -q 'not in the form' "$work/t_fx_$fx_name.err"; then
                ok "fixture_rejects_$fx_name"
            else
                bad "fixture_rejects_$fx_name" "status=$status_of err=$(head -1 "$work/t_fx_$fx_name.err")"
            fi
            continue
        fi
        fx_key="${fx_expected#ok:}"
        fx_key="${fx_key%:*}"
        fx_topic="${fx_expected##*:}"
        if [[ "$fx_key" == '-' ]]; then
            # open and post reserve the dash key. A parsed dash pointer reads
            # as "no pointer", never as a form error.
            run_wrapper "t_fx_$fx_name" post fx-key --as Theta
            if [[ "$status_of" -eq 1 ]] && grep -q 'no pointer for fx-key' "$work/t_fx_$fx_name.err"; then
                ok "fixture_accepts_$fx_name"
            else
                bad "fixture_accepts_$fx_name" "status=$status_of err=$(head -1 "$work/t_fx_$fx_name.err")"
            fi
            continue
        fi
        run_wrapper "t_fx_$fx_name" open "$fx_key" x 'A title' --as Theta
        if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 1 ]] \
           && [[ "$(jq -r '.key' "$work/t_fx_$fx_name.out")" == "$fx_key" ]] \
           && [[ "$(jq -r '.topic' "$work/t_fx_$fx_name.out")" == "$fx_topic" ]] \
           && [[ "$(jq -r '.state' "$work/t_fx_$fx_name.out")" == 'exists' ]]; then
            ok "fixture_accepts_$fx_name"
        else
            bad "fixture_accepts_$fx_name" "status=$status_of out=$(head -1 "$work/t_fx_$fx_name.out")"
        fi
    done < "$fixtures"
else
    bad 'fixtures_are_present' "missing $fixtures"
fi

# -- Summary ------------------------------------------------------------

printf '\n%d checks, %d failures\n' "$checks" "$failures"
[[ "$failures" -eq 0 ]]
