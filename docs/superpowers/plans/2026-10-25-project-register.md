# Project Registers Implementation Plan

> **For the implementer:** Execute Native, in this session, in task order. The project forbids sub-agent driven development and markdown checklists. Track the work with `fp comment CHAT-jmnjvupr` at each task boundary.

**Goal:** Give node 9 one register room per project, named by an environment variable, and one command that creates a register from a `forward-register.md`.

**Architecture:** A small helper in `node-9/bin/lib.sh` resolves the register value. The wrapper, the importer, and the new `register-create` script use it. Topic names carry the register name as a prefix. Install stays unchanged.

**Tech Stack:** Bash 3.2 or newer, Perl, jq, the chat-shell CLI, Kotlin for the shell command option.

**Spec:** `docs/superpowers/specs/2026-10-24-project-register-design.md`

## Global Constraints

- The room topic rule is `^[a-z0-9][a-z0-9-]{0,63}$`.
- A topic name holds at most 64 characters.
- A register name holds at most 37 characters.
- One source section holds at most 16,384 bytes.
- The pointer line form is `<key> | <topic> | <metadata> | <title>`.
- A command that reads, writes, or names a register room stops with status 2 when it has no register variable and no `--register` value. `register-create` takes the name as an argument. `register-import pack` names the room in its request IDs and touches no room.
- No command prints a secret value.
- Every commit message starts `CHAT-jmnjvupr: `.

## Review Focus

1. A heading with no letter or digit gives an empty slug. It must refuse, never make an empty topic. Test in Task 6.
2. Two headings share a slug, and the suffixed name matches another heading's plain slug. It must refuse. Test in Task 6.
3. An entry short name equals `snapshot-<12 hex>` of the source hash. It must refuse. Test in Task 6.
4. One source section is above 16,384 bytes. It must refuse and name the section. Test in Task 6.
5. A 37 character register name plus a long short name passes 64 characters. It must refuse. Test in Tasks 1 and 3.

---

### Task 1: Register value helpers in lib.sh

**Files:**
- Modify: `node-9/bin/lib.sh`
- Test: `shell-scripts/test-register-bootstrap.sh`

**Interfaces:**
- Produces: `node9_register_value` prints the register value and returns 0. It prints one line and returns 2 with no variable.
- Produces: `node9_register_name` prints the name and returns 0. It returns 2 when only the id is set, and 2 when the name fails `node9_register_name_error`.
- Produces: `node9_register_name_error NAME` prints one reason and returns 1 for a bad name. It returns 0 for a good name.

**Step 1: Write the failing tests**

Add to `shell-scripts/test-register-bootstrap.sh`, before the summary block:

```bash
# -- Register value helpers ---------------------------------------------

lib="$repo_root/node-9/bin/lib.sh"

# register_probe PROGRAM [ENV ASSIGNMENT...]
# Source lib.sh in a clean environment, run one program, and print
# "<status>|<output>".
register_probe() {
    local program="$1"; shift
    local out status
    out="$(env -u NODE_9_REGISTER_TOPIC_NAME -u NODE_9_REGISTER_TOPIC_ID "$@" \
        bash -c 'source "$0" > /dev/null 2>&1; eval "$1"' \
        "$lib" "$program" 2>&1)"
    status=$?
    printf '%s|%s' "$status" "$out"
}

out="$(register_probe 'node9_register_value')"
if [[ "$out" == 2\|* ]] && [[ "$out" == *NODE_9_REGISTER_TOPIC_NAME* ]] \
   && [[ "$out" == *NODE_9_REGISTER_TOPIC_ID* ]]; then
    ok 'register_value_needs_a_variable'
else
    bad 'register_value_needs_a_variable' "$out"
fi

out="$(register_probe 'node9_register_value' \
    NODE_9_REGISTER_TOPIC_NAME=demo-chat-register)"
[[ "$out" == '0|demo-chat-register' ]] && ok 'register_value_prefers_the_name' \
    || bad 'register_value_prefers_the_name' "$out"

out="$(register_probe 'node9_register_value' NODE_9_REGISTER_TOPIC_ID=abc)"
[[ "$out" == '0|abc' ]] && ok 'register_value_takes_the_id' \
    || bad 'register_value_takes_the_id' "$out"

out="$(register_probe 'node9_register_name' NODE_9_REGISTER_TOPIC_ID=abc)"
[[ "$out" == 2\|* ]] && ok 'register_name_refuses_an_id_alone' \
    || bad 'register_name_refuses_an_id_alone' "$out"

out="$(register_probe 'node9_register_name' NODE_9_REGISTER_TOPIC_NAME=Demo)"
[[ "$out" == 2\|* ]] && ok 'register_name_refuses_an_invalid_env_name' \
    || bad 'register_name_refuses_an_invalid_env_name' "$out"

out="$(register_probe 'node9_register_value' \
    NODE_9_REGISTER_TOPIC_NAME=demo-chat-register NODE_9_REGISTER_TOPIC_ID=abc)"
[[ "$out" == '0|demo-chat-register' ]] && ok 'register_value_name_wins' \
    || bad 'register_value_name_wins' "$out"

for bad_name in Demo --x a_b "$(printf 'a%.0s' $(seq 1 38))"; do
    out="$(register_probe "node9_register_name_error $bad_name")"
    [[ "$out" == 1\|* ]] && ok "register_name_refuses_$bad_name" \
        || bad "register_name_refuses_$bad_name" "$out"
done

out="$(register_probe 'node9_register_name_error demo-chat-register')"
[[ "$out" == '0|' ]] && ok 'register_name_accepts_a_good_name' \
    || bad 'register_name_accepts_a_good_name' "$out"
```

**Step 2: Run the suite and watch the new checks fail**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: `FAIL register_...` lines and a nonzero exit.

**Step 3: Add the three helpers to `node-9/bin/lib.sh`**

```bash
node9_register_value() {
    local name="${NODE_9_REGISTER_TOPIC_NAME:-}" id="${NODE_9_REGISTER_TOPIC_ID:-}"
    if [[ -n "$name" ]]; then printf '%s\n' "$name"; return 0; fi
    if [[ -n "$id" ]]; then printf '%s\n' "$id"; return 0; fi
    printf 'node-9: set NODE_9_REGISTER_TOPIC_NAME or NODE_9_REGISTER_TOPIC_ID.\n' >&2
    return 2
}

node9_register_name() {
    local name="${NODE_9_REGISTER_TOPIC_NAME:-}" reason
    if [[ -z "$name" ]]; then
        printf 'node-9: a write needs NODE_9_REGISTER_TOPIC_NAME. An id gives no topic prefix.\n' >&2
        return 2
    fi
    reason="$(node9_register_name_error "$name")" \
        || { printf 'node-9: %s\n' "$reason" >&2; return 2; }
    printf '%s\n' "$name"
}

node9_register_name_error() {
    local name="${1-}"
    if [[ -z "$name" ]]; then printf 'the register name is empty.\n'; return 1; fi
    if (( ${#name} > 37 )); then printf 'the register name passes 37 characters.\n'; return 1; fi
    if [[ ! "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
        printf 'the register name is not lowercase letters, digits, and dashes.\n'; return 1
    fi
    return 0
}
```

**Step 4: Run the suite and check it passes**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: `0 failures`.

**Step 5: Commit**

```bash
git add node-9/bin/lib.sh shell-scripts/test-register-bootstrap.sh
git commit -m "CHAT-jmnjvupr: Resolve the register value in one helper"
```

---

### Task 2: The wrapper uses the register value

**Files:**
- Modify: `node-9/bin/register` (`load_pointers`, `read_index`, the `read`, `open`, and `post` cases)
- Test: `shell-scripts/test-node-9-register.sh`
- Test: `shell-scripts/test-node-9.sh` (the installed checks give a register value, and one check covers the absent case)
- Test: `shell-scripts/test-register-bootstrap.sh` (exports a register name for the importer verify read)

**Interfaces:**
- Consumes: `node9_register_value` and `node9_register_name` from Task 1.
- Produces: the wrapper variable `register` holds the value for every shell call.

**Step 1: Set the default register in the test harness and update fixtures**

In `run_wrapper`, add `NODE_9_REGISTER_TOPIC_NAME="${REGISTER_NAME-demo-chat-register}"` to the `env` list. The `-` form keeps an empty value empty, so `REGISTER_NAME=''` simulates an absent variable.

Change `index_record` to `register_record`, with topic `${REGISTER_NAME-demo-chat-register}`. Replace every `register-index` literal in fixtures and assertions with the same default. A caller that sets `REGISTER_NAME` changes both the room and the fixture.

**Step 2: Write the failing checks**

```bash
reset_stub
stub_reply "$(register_record m1 2026-10-20T00:00:00Z 'CHAT-1 | demo-chat-register-fr-x | | Fix the pump')"
run_wrapper t_reg_index read --as Theta
if [[ "$status_of" -eq 0 ]] && grep -qx -- '--index' "$stub_dir/call.1.args" \
   && grep -qx 'demo-chat-register' "$stub_dir/call.1.args"; then
    ok 'read_names_the_register'
else
    bad 'read_names_the_register' "args=$(stub_args 1)"
fi

reset_stub
REGISTER_NAME='' run_wrapper t_reg_missing read --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'missing_register_is_status_2'
else
    bad 'missing_register_is_status_2' "status=$status_of calls=$(calls)"
fi
```

**Step 3: Run the suite and watch both checks fail**

Run: `bash shell-scripts/test-node-9-register.sh`
Expected: `FAIL read_names_the_register` and `FAIL missing_register_is_status_2`.

**Step 4: Change the wrapper**

Right after `node9_require_installed`, read the value:

```bash
register="$(node9_register_value)" || exit 2
```

At `read`: `run_shell register-read --index "$register"`.
At `load_pointers`: add `--arg index "$register"` and compare `.topic == $index`.
At `read_index`: `run_shell join "$register"` and `run_shell register-read --index "$register"`.
At `open`: the index readback is `messages "$register" --format records`, and the pointer post is `--topic "$register"`.
At `post`: the index record read is `messages "$register" --format records`.

**Step 5: Run the suite and check it passes**

Run: `bash shell-scripts/test-node-9-register.sh`
Expected: `0 failures`.

**Step 6: Commit**

```bash
git add node-9/bin/register shell-scripts/test-node-9-register.sh
git commit -m "CHAT-jmnjvupr: Read and write the named register room"
```

---

### Task 3: `open` mints prefixed topics and both writes check the name

**Files:**
- Modify: `node-9/bin/register` (the `open` and `post` cases)
- Test: `shell-scripts/test-node-9-register.sh`

**Interfaces:**
- Consumes: `node9_register_name` from Task 1.
- Produces: the topic `<name>-fr-<short>`.

**Step 1: Write the failing checks**

```bash
reset_stub
stub_reply_for 1 ''
stub_reply_for 2 'Created room demo-chat-register-fr-x (id 7)'
stub_reply_for 3 'Joined demo-chat-register-fr-x'
stub_reply_for 4 '{"messageId":"m1","commandId":"c1","requestId":"r1","outcome":"ok"}'
stub_reply_for 5 "$(register_record m1 2026-10-20T00:00:02Z 'CHAT-9 | demo-chat-register-fr-x | | Fix')"
run_wrapper t_open_prefix open CHAT-9 x 'Fix' --as Theta
if [[ "$status_of" -eq 0 ]] && [[ "$(calls)" -eq 5 ]] \
   && grep -qx 'demo-chat-register-fr-x' "$stub_dir/call.2.args"; then
    ok 'open_prefixes_the_register_name'
else
    bad 'open_prefixes_the_register_name' "args=$(stub_args 2)"
fi

reset_stub
run_wrapper t_open_upper open CHAT-9 FixThePump "Fix" --as Theta
if [[ "$status_of" -eq 2 ]]; then
    ok 'open_refuses_an_uppercase_short'
else
    bad 'open_refuses_an_uppercase_short' "status=$status_of"
fi

reset_stub
run_wrapper t_open_empty open CHAT-9 '' "Fix" --as Theta
if [[ "$status_of" -eq 2 ]]; then
    ok 'open_refuses_an_empty_short'
else
    bad 'open_refuses_an_empty_short' "status=$status_of"
fi

EXTRA_ENV='NODE_9_REGISTER_TOPIC_ID=abc123' REGISTER_NAME='' \
    run_wrapper t_open_id open CHAT-9 x "Fix" --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'open_refuses_an_id_alone'
else
    bad 'open_refuses_an_id_alone' "status=$status_of calls=$(calls)"
fi

reset_stub
REGISTER_NAME=Demo run_wrapper t_post_bad_name post CHAT-9 --as Theta
if [[ "$status_of" -eq 2 ]] && [[ "$(calls)" -eq 0 ]]; then
    ok 'post_refuses_an_invalid_register_name'
else
    bad 'post_refuses_an_invalid_register_name' "status=$status_of calls=$(calls)"
fi
```

The existing `open` fixtures and assertions name `forward-register-x` and `forward-register-other`. Change them to `demo-chat-register-fr-x` and `demo-chat-register-fr-other` in this task.

**Step 2: Run the suite and watch the checks fail**

Run: `bash shell-scripts/test-node-9-register.sh`
Expected: the four new `FAIL` lines.

**Step 3: Change the `open` and `post` cases**

Both cases resolve and validate the name before any shell call:

```bash
name="$(node9_register_name)" || exit 2
```

Then the `open` case checks the short name and builds the topic:

```bash
[[ "$short" =~ ^[a-z0-9][a-z0-9-]*$ ]] \
    || fail "the short name $short is not lowercase letters, digits, or dashes." 2
new_topic="$name-fr-$short"
(( ${#new_topic} <= 64 )) \
    || fail "the topic $new_topic passes 64 characters." 2
```

The `post` case uses `$name` for `load_pointers`, the join, and the post topic.

**Step 4: Run the suite and check it passes**

Run: `bash shell-scripts/test-node-9-register.sh`
Expected: `0 failures`.

**Step 5: Commit**

```bash
git add node-9/bin/register shell-scripts/test-node-9-register.sh
git commit -m "CHAT-jmnjvupr: Build entry topics from the register name"
```

---

### Task 4: The importer takes `--register`

**Files:**
- Modify: `node-9/bin/register-import` (`pack_bundle`, `build_requests`, `apply_bundle`, `verify_bundle`, `import_preflight`, `ensure_index`, `index_message_count`, `import_posts`, `verify_pointers`, `verify_reads`, `snapshot_topic`, `join_all`, the receipt path)
- Test: `shell-scripts/test-register-bootstrap.sh`
- Test: `shell-scripts/test-register-bundle.sh` (takes the register name, so `pack` and `check_bundle` keep working)

**Interfaces:**
- Consumes: `node9_register_name` and `node9_register_name_error` from Task 1.
- Produces: the global `import_register`. `pack`, `apply`, and `verify` accept `--register NAME`. All three default to `node9_register_name`, so `pack` and `apply` derive the same request IDs from one environment.

**Step 1: Write the failing checks**

Change the suite fixture. The mapping topics become
`demo-chat-register-fr-first` and `demo-chat-register-fr-second`. The
`snapshot_topic_name` value becomes `demo-chat-register-fr-snapshot-<12>`.
The `entry_id` lookup takes the new topic name.

Change `run_apply` in two ways. Give it the default register in its
environment: `NODE_9_REGISTER_TOPIC_NAME="${NODE_9_REGISTER_TOPIC_NAME-demo-chat-register}"`.
Pass the flag only when `REGISTER` is non-empty:

```bash
local -a reg=()
[[ -n "${REGISTER:-}" ]] && reg=(--register "$REGISTER")
"$install_home/bin/register-import" apply --bundle "$bundle" "${reg[@]}" --as Mario
```

Add these checks:

```bash
run_apply
if [[ $status -eq 0 && "$(grep -c 'demo-chat-register' "$stub_state/log")" -gt 0 ]]; then
    ok 'apply_reads_the_named_register'
else
    bad 'apply_reads_the_named_register' "exit $status, log: $(head -3 "$stub_state/log")"
fi

REGISTER=demo-chat-register NODE_9_REGISTER_TOPIC_NAME='' run_apply
if [[ $status -eq 0 && "$(calls)" -gt 0 ]]; then
    ok 'apply_takes_an_explicit_register'
else
    bad 'apply_takes_an_explicit_register' "exit $status, calls=$(calls)"
fi

REGISTER='' NODE_9_REGISTER_TOPIC_NAME='' run_apply
if [[ $status -eq 2 && "$(calls)" -eq 0 ]]; then
    ok 'apply_needs_a_register'
else
    bad 'apply_needs_a_register' "exit $status, calls=$(calls)"
fi

REGISTER='' NODE_9_REGISTER_TOPIC_NAME='' NODE_9_REGISTER_TOPIC_ID=abc123 run_apply
if [[ $status -eq 2 && "$(calls)" -eq 0 ]]; then
    ok 'apply_refuses_an_id_alone'
else
    bad 'apply_refuses_an_id_alone' "exit $status, calls=$(calls)"
fi

REGISTER=Demo run_apply
if [[ $status -eq 2 && "$(calls)" -eq 0 ]]; then
    ok 'apply_refuses_a_bad_register'
else
    bad 'apply_refuses_a_bad_register' "exit $status, calls=$(calls)"
fi
```

`pack` needs the name too. `build_requests` takes the snapshot topic from
`snapshot_topic`, and every snapshot request ID holds that topic. The top
pack of the suite takes the name from the environment that Task 2 exports.
Add three checks:

```bash
snapshot_short="$(sha_of "$work/source.md" | cut -c 1-12)"
packed_topic="$(sed -n 's/^topic: \(.*\) parts [0-9]*$/\1/p' <<< "$pack_out")"
if [[ "$packed_topic" == "demo-chat-register-fr-snapshot-$snapshot_short" ]] \
   && [[ "$(jq -r --arg t "$packed_topic" \
            '[.requests[] | select(.topic == $t)] | length' \
            "$bundle/bundle-manifest.json")" -gt 0 ]]; then
    ok 'pack_names_the_snapshot_requests'
else
    bad 'pack_names_the_snapshot_requests' "topic=$packed_topic"
fi

run_apply
if [[ $status -eq 0 ]] && grep -q "$packed_topic" "$stub_state/log"; then
    ok 'apply_uses_the_packed_snapshot_topic'
else
    bad 'apply_uses_the_packed_snapshot_topic' "exit $status, log: $(head -3 "$stub_state/log")"
fi

REGISTER=other-register run_apply
if [[ $status -eq 2 && "$(calls)" -eq 0 ]]; then
    ok 'apply_refuses_a_bundle_from_another_register'
else
    bad 'apply_refuses_a_bundle_from_another_register' "exit $status, calls=$(calls)"
fi
```

The second check compares the topic across pack and apply. The third proves
that `check_bundle` derives the request IDs again with the name of the run.
A bundle packed for one register therefore stops before any live call. The
mapping holds the name-prefixed entry topics, and the content manifest holds
the mapping. The bundle hash covers the name through the mapping.

Add one prefix-refusal case. Pack a second bundle from a mapping whose first
entry topic is `forward-register-first`. Apply it with
`--register demo-chat-register`. Expect status 2 before any topic create.

`test-register-bundle.sh` calls `pack` and `check_bundle` many times. It
needs one line, an export of `NODE_9_REGISTER_TOPIC_NAME=demo-chat-register`
beside its `importer` value. It stays offline.

**Step 2: Run the suite and watch the checks fail**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: the new `FAIL` lines.

**Step 3: Change the importer**

- Add `import_register=''` beside `import_owner`.
- In `apply_bundle` and `verify_bundle`, accept `--register`; then set the value before the preflight call. An explicit value must pass `node9_register_name_error`. With no flag, use `node9_register_name`, never `node9_register_value`, because a write mints topic names and receipt paths. A missing name returns 2.
- In `pack_bundle`, accept `--register` and set `import_register` the same way, before the first `build_requests` call. `snapshot_topic` reads that value, so pack derives the same request IDs as apply. Pack stays offline and needs no installed home.
- Update the `pack` usage line and the file header with the flag.
- `check_bundle` runs before any live call in both `apply` and `verify`. It calls `build_requests` again with `import_register`. A bundle packed for another register stops there with status 2.
- In `import_preflight`, after `check_bundle`, add a prefix check. Every entry topic in `content-manifest.json` must start with `$import_register-fr-`. A mismatch returns 2 and names the topic.
- Replace every literal `register-index` with `$import_register`. The sites are lines 537, 539, 549, 567, 569, 575 (`ensure_index`), 1020 and 1028 (the pointer posts), 1031 (the index readback), and 1241 (`join_all`).
- Pass the index to every importer read. Line 513 (`index_message_count`), line 1031 (`import_posts` readback), and line 1382 (`verify_pointers`) run `register-read --index "$import_register"`.
- Line 1442 (`verify_reads`) runs the wrapper. Give it the name in its environment: `env NODE_9_REGISTER_TOPIC_NAME="$import_register" "$NODE9_DIR/bin/register" read --as "$user"`.
- `snapshot_topic`: `printf '%s-fr-snapshot-%s\n' "$import_register" "${1:0:12}"`.
- Ownership: compare the proof to `$import_owner`.
- Receipts: `$NODE9_DIR/data/register-import/$import_register/$bundle_hash`.

**Step 4: Run the suite and check it passes**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: `0 failures`.

**Step 5: Run the bundle suite**

Run: `bash shell-scripts/test-register-bundle.sh`
Expected: its existing count passes.

**Step 6: Commit**

```bash
git add node-9/bin/register-import shell-scripts/test-register-bootstrap.sh \
    shell-scripts/test-register-bundle.sh
git commit -m "CHAT-jmnjvupr: Import into the named register"
```

---

### Task 5: `register-read` requires an index

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt:98`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ShellCommandContractTests.kt:47`

**Interfaces:**
- Produces: `register-read --index` has no default.

**Step 1: Change the contract test row**

`Expected("register-read", "Read the register: its index and the newest recent entry of each topic", listOf(Opt("index", null), Opt("maxAgeHours", "24")))`

**Step 2: Run the test and watch it fail**

Run: `mvn -q -pl chat-shell -am -Dtest=ShellCommandContractTests -Dsurefire.failIfNoSpecifiedTests=false test`
Expected: a failure naming the `index` default.

**Step 3: Make the option required**

In `registerReadCommand`: `.longName("index").required(true).type(String::class.java).build()`.

**Step 4: Run the test and check it passes**

Same command. Expected: BUILD SUCCESS.

**Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt chat-shell/src/test/kotlin/com/demo/chat/test/commands/ShellCommandContractTests.kt
git commit -m "CHAT-jmnjvupr: Require an index on register-read"
```

---

### Task 6: `register-create`

**Files:**
- Create: `node-9/bin/register-create`
- Test: `shell-scripts/test-register-bootstrap.sh`

**Interfaces:**
- Consumes: the importer functions `pack_bundle`, `apply_bundle`, `verify_bundle` with `REGISTER_IMPORT_LIB=1`.
- Consumes: `node9_register_name_error`, `node9_key_error`, `node9_title_error`.
- Produces: `register-create <name> [<path>] [--source-date DATE] --as <owner>`.

**Step 1: Write the failing checks**

Add a fixture with a preamble and three sections. Two sections share a heading. Checks:

| Name | Expectation |
|------|-------------|
| `create_empty_register` | no path, status 0, one `add-topic --name demo-chat-register` |
| `create_derives_entries` | three entries, topics `demo-chat-register-fr-<slug>` |
| `create_key_from_issue_id` | a heading with `CHAT-abc` gives key `CHAT-abc` |
| `create_key_fallback` | a heading without an id gives `section-<index>` |
| `create_repeated_slug_suffix` | the second heading gives `<slug>-<index>` |
| `create_second_collision_refuses` | status 2, both indexes named |
| `create_empty_slug_refuses` | status 2, section named |
| `create_snapshot_collision_refuses` | status 2, section named |
| `create_oversize_section_refuses` | status 2, section named |
| `create_name_collision_refuses` | a room named `demo-chat-register-fr-x` exists, so a create stops with status 1 and names it |
| `create_name_collision_reverse` | register `demo-chat` owns `demo-chat-fr-*`, so `demo-chat-fr-x-register` stops with status 1 |
| `create_retry_same_date_resumes` | two runs with one `--source-date` and one file give status 0 and one bundle hash |
| `create_changed_date_stops` | a second run with a new `--source-date` stops with status 1 |
| `create_date_is_required` | a path and no `--source-date` stops with status 2 |
| `create_missing_owner_refuses` | status 2 |
| `create_path_must_be_absolute` | status 2 |
| `create_refuses_a_symlink` | status 2 |
| `create_resumes` | a second run with the same receipts gives status 0 |

**Step 2: Run the suite and watch the checks fail**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: `FAIL create_...` lines.

**Step 3: Write `node-9/bin/register-create`**

Requirements the script must meet:

- Parse `<name> [<path>] [--source-date DATE] --as <owner>`. Refuse extras with status 2. A path without `--source-date` stops with status 2. A date must match `^[0-9]{4}-[0-9]{2}-[0-9]{2}$`.
- Source `lib.sh`. Source `register-import` with `REGISTER_IMPORT_LIB=1`.
- Call `node9_require_installed`, then `node9_register_name_error`. Refuse a bad name with status 2.
- Resolve the CLI as the wrapper does: `CHAT_SHELL_CLI`, else `$NODE9_DIR/bin/chat-shell-cli`.
- With a path: check absolute, regular file, no link, readable.
- Derive entries in one `awk` pass. Record for each `## ` heading: the line number, the heading text, and the end line of the first paragraph.
- Compute the source SHA-256. Derive each short name, key, and title per the spec.
- Refuse these cases from the design: a register name collision, a second short-name collision, an empty slug, a short name equal to the source snapshot short, and a section above 16,384 bytes.
- Refuse a name collision in both directions. The first direction checks for a room that starts with `<name>-fr-`. The second checks whether `<name>` starts with `<other>-fr-` for a register that already exists.
- Write the mapping JSON to a private temp directory. Include `version: 1`, `source_sha256`, `source_date` (the `--source-date` value, never the run date), `source_basis` (`register-create <name>`), `session` (the preamble range), and the entries. The date enters the bundle hash. A retry repeats it.
- Call `pack_bundle --register <name>`, then `apply_bundle --register <name>`, then `verify_bundle --register <name>`.
- Without a path: read the owner credentials with `import_credentials`, run `add-topic --name <name>` as the owner, run the ownership proof, run `join_all <name>`, and stop with status 0.

**Step 4: Run the suite and check it passes**

Run: `bash shell-scripts/test-register-bootstrap.sh`
Expected: `0 failures`.

**Step 5: Commit**

```bash
git add node-9/bin/register-create shell-scripts/test-register-bootstrap.sh
git commit -m "CHAT-jmnjvupr: Add register-create"
```

---

### Task 7: Docs, gates, and drift

**Files:**
- Modify: `node-9/README.md`
- Modify: `docs/superpowers/specs/2026-10-17-topic-register-design.md` (one superseded note)
- Modify: `.github/workflows/maven.yml`
- Modify: `justfile`

**Step 1: Add the register section to `node-9/README.md`**

State the environment variable, the topic shapes, `register-create`, and the fact that install creates no register. Add `--register NAME` to the `pack` usage line at line 309, and to the `apply` and `verify` usage lines.

**Step 2: Add the superseded note to the 2026-10-17 design**

One paragraph. It names the 2026-10-24 design and the one-register-per-project rule.

**Step 3: Add the suites to CI and the justfile**

`maven.yml` steps for `test-node-9-register.sh`, `test-register-bootstrap.sh`, and `test-register-bundle.sh`. A `check-register` recipe in `justfile` runs the three.

**Step 4: Run every gate**

```bash
bash shell-scripts/test-node-9-register.sh
bash shell-scripts/test-register-bootstrap.sh
bash shell-scripts/test-register-bundle.sh
drift check
git diff --check
```

Expected: `0 failures` from each suite, `drift check` exit 0, and no whitespace error.

**Step 5: Commit**

```bash
git add node-9/README.md docs/superpowers/specs/2026-10-17-topic-register-design.md .github/workflows/maven.yml justfile
git commit -m "CHAT-jmnjvupr: Document project registers and gate the suites"
```
