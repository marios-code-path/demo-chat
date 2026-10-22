# Portable Register Import Implementation Plan (Stage 4A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans`. Work inline. `AGENTS.md` forbids subagent-driven development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Restore an active topic register on a prepared node-9 host from one reviewed Markdown snapshot, with byte proof.

**Architecture:** `register-import pack` builds a private bundle with no network call. `apply` posts it with stable request IDs and keeps receipts in the installed home. `verify` reads the stored bytes back and times five session reads. Offline tests drive a stub shell CLI that keeps its state in a directory.

**Tech Stack:** Bash 3.2, Perl, `jq`, `shasum`, the `chat-shell-cli` launcher, and the installed node-9 home.

**Spec:** `docs/superpowers/specs/2026-10-21-portable-register-import-design.md` (commit `df441dcd`).

**Issues:** `CHAT-jmnjvupr` is the parent. `CHAT-bjqzvsoe` owns the installed shell CLI runtime. It blocks live import and blocks no offline task.

## Global Constraints

- Bundle directory mode `0700`. Bundle file mode `0600`.
- Canonical JSON is `jq -cS` output with one final LF, under `LC_ALL=C`.
- UUIDv5 namespace is `e9d6d78a-0020-43e3-9893-92737b9e536c`.
- A section, a session range, and an entry range each hold at most 16,384 bytes.
- Core ports are `6890` and `6891`. Node ID is `9`. Key type is `uuid`.
- `apply` and `verify` run from `/opt/demo-chat/node-9/bin/` and call `node9_require_installed`. `pack` does not.
- Receipts live in `$NODE9_DIR/data/register-import/<bundle-hash>/`. No receipt, password, or message ID enters a bundle.
- Exit statuses: `0` pass, `1` conflict or byte difference, `2` invalid input or guard, `69` core absent, `75` uncertain outcome, `77` credential absent, `124` time limit.
- Code is Bash 3.2 and Perl, to match `register`. No associative array, no `mapfile`.
- New prose follows `asd-ste100` strict mode. Commit messages name `CHAT-jmnjvupr` and end with the attribution line.

## Review Focus

1. A source with CRLF line ends or no final newline. Ranges keep every byte. (Task 2)
2. A multibyte character in a range. The 16,384 limit counts bytes, not characters. (Task 2)
3. A crash after a post and before its receipt. The retry sends the same request ID and makes no second message. (Task 5)
4. A bundle with an edited manifest, a missing part, or loose file modes. `apply` refuses it before any live call. (Task 3)
5. A title or key with `|`, a newline, or a leading space. `pack` refuses it, because it corrupts a pointer. (Task 2)

## File Structure

- Create `node-9/bin/register-range` (Perl). Cuts a line range from a source into exact bytes.
- Create `node-9/bin/register-uuid5` (Perl). Prints a UUIDv5 for a namespace and a name.
- Create `node-9/bin/register-import` (Bash). Commands `pack`, `apply`, `verify`.
- Keep `node-9/bin/register-split`. It splits a source at `## ` headings.
- Create `shell-scripts/fixtures/register-stub-cli`. A stub with state in `$STUB_STATE`.
- Create `shell-scripts/test-register-bundle.sh` (Tasks 2 and 3) and `shell-scripts/test-register-bootstrap.sh` (Tasks 4 to 7).
- Remove `node-9/bin/import-register`, `shell-scripts/test-register-apply.sh` and `shell-scripts/test-register-import.sh` in Task 9, after the useful cases move.
- Modify `node-9/bin/register` (header comment), `docs/SHELL-COMMAND-CONTRACT.md`, `node-9/README.md`.
- Create `docs/superpowers/proofs/2026-10-21-portable-register-import/RECORD.md` in Task 10.

### Task 1: Rebase onto the installer branch

**Files:** the stage 4 worktree only.

**Interfaces:**
- Consumes: branch `chat-mvvvnujw-node9-install` at `2604d3a3`, pushed. It adds `node9_installed`, `node9_require_installed`, and `NODE9_ALLOW_SOURCE_TREE`.
- Produces: a stage 4 branch whose `lib.sh` has the guard. Every later task relies on it.

- [ ] **Step 1: Save the prototype.** Run `git status --short`. Expect ` M node-9/bin/import-register` and `?? shell-scripts/test-register-apply.sh`. Run `git add node-9/bin/import-register shell-scripts/test-register-apply.sh && git commit -m "CHAT-jmnjvupr: WIP prototype apply, unverified"`.
- [ ] **Step 2: Rebase.** Run `git fetch origin && git rebase origin/chat-mvvvnujw-node9-install`. If the installer branch has merged into `master`, rebase onto `origin/master`.
- [ ] **Step 3: Resolve conflicts** in `lib.sh`, `README.md`, and the node-9 scripts. Keep both sides. The guard call stays right after `source "$script_dir/lib.sh"`.
- [ ] **Step 4: Restore the prototype as uncommitted.** Run `git reset --mixed HEAD~1`. Run `git status --short` and expect the same two entries as Step 1.
- [ ] **Step 5: Verify.** Run `bash shell-scripts/test-node-9.sh`. Expect `all node-9 cases passed`. Run `drift check` and expect no failure.
- [ ] **Step 6: Log.** Run `fp comment CHAT-jmnjvupr "Stage 4A: rebased onto the installer branch."`. Push only with Mario's word.

### Task 2: Pack, part 1: mapping and ranges

**Files:** Create `node-9/bin/register-range`, `node-9/bin/register-import`. Test `shell-scripts/test-register-bundle.sh`.

**Interfaces:**
- Produces: `register-range <source> <first_line> <after_line> <out-file>` writes the exact bytes and prints `{"offset":N,"bytes":N,"sha256":"<hex>"}`. Status `2` on a range outside the file, a range that starts after its end, an empty range, or more than 16,384 bytes.
- Produces: `register-import pack --source FILE --mapping FILE --bundle DIR` (completed in Task 3). This task adds the mapping checks as a function `validate_mapping MAPPING SOURCE` that prints one error line and returns `2`.

- [ ] **Step 1: Write failing tests** in `test-register-bundle.sh`. Each case builds a source in a temp directory and calls the tool:
  - `range_keeps_crlf_bytes`: a three-line CRLF source. `register-range src 2 3 out` gives `out` equal to line 2 with its CRLF (`cmp`), `bytes` equal to its length.
  - `range_without_final_newline`: the last line has no LF. The range keeps that state.
  - `range_counts_bytes_not_characters`: 5,462 copies of a 3-byte character (16,386 bytes) in one line. Status `2`. 5,461 copies (16,383 bytes) pass.
  - `range_rejects_bad_bounds`: `after_line` below `first_line`, `first_line` 0, and `after_line` above the last line plus one. Each gives status `2`.
  - `mapping_rejects_bad_input`: a wrong `source_sha256`, a repeated key, a repeated topic, a topic named `Bad Topic`, a key `a|b`, a title `x|y`, a title with a newline, and a key with a leading space. Each gives status `2` and one error line.
- [ ] **Step 2: Run** `bash shell-scripts/test-register-bundle.sh`. Expect FAIL (`register-range` absent).
- [ ] **Step 3: Implement** `register-range` in Perl with `:raw` I/O. Count a line as bytes through its LF, or to the end of the file.
- [ ] **Step 4: Implement** `validate_mapping`. A topic matches `^[a-z0-9][a-z0-9-]{0,63}$` (my call). Reuse the key rule of `register` (`check_key`). Move `check_key` to `lib.sh` if the copy would differ.
- [ ] **Step 5: Run** the suite. Expect PASS.
- [ ] **Step 6: Commit** `register-range`, `register-import`, and the test.

### Task 3: Pack, part 2: bundle, identity, request IDs

**Files:** Create `node-9/bin/register-uuid5`. Modify `register-import`, `test-register-bundle.sh`.

**Interfaces:**
- Consumes: `register-range`, `register-split`, `validate_mapping`.
- Produces: `register-uuid5 <namespace> <name>` prints a lowercase UUIDv5.
- Produces: the bundle layout (my call): `source.md`, `mapping.json`, `content-manifest.json`, `parts/part-NNNNNN.bin`, `ranges/session.bin`, `ranges/entry-NNN.bin`, `bundle-manifest.json`.
- Produces: `content_hash` (SHA-256 of `content-manifest.json`) is the bundle hash. Request ID name is `<content_hash>|<kind>|<topic>|<index>`. Kinds: `snapshot-header`, `snapshot-part`, `session`, `entry-header`, `entry`, `entry-pointer`, `snapshot-pointer`.
- Produces: `check_bundle DIR` verifies modes, hashes, and request IDs. It returns `2` on any difference.

- [ ] **Step 1: Write failing tests.**
  - `uuid5_known_vector`: namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8`, name `python.org`, expects `886313e1-3b8a-5372-9b90-0c9aee199e5d`.
  - `pack_is_deterministic`: two packs of one source and mapping give equal `bundle-manifest.json` bytes.
  - `pack_changes_identity`: one changed byte in the source (with the hash in the mapping updated) gives a new bundle hash and new request IDs.
  - `pack_modes`: the directory is `0700` and every file `0600`.
  - `pack_refuses_existing_dir`, `pack_refuses_missing_preamble`, `pack_refuses_nul_and_bad_utf8`, `pack_names_oversize_section`: status `2`. The last error line names the heading, index, and byte count.
  - `pack_prints_review_lines`: output holds the source hash, bundle hash, each topic, and byte counts, and no source text.
  - `check_bundle_refuses_tampering`: edit one byte of a part, delete a part, `chmod 644` a file, and edit `bundle-manifest.json`. Each gives status `2` and names the file.
- [ ] **Step 2: Run.** Expect FAIL.
- [ ] **Step 3: Implement** `register-uuid5` with `Digest::SHA` (version 5, RFC 4122 variant bits).
- [ ] **Step 4: Implement** `pack` and `check_bundle`. Write the bundle in a temp directory and rename it, so a failed pack leaves nothing.
- [ ] **Step 5: Run.** Expect PASS. Commit.

### Task 4: The stub CLI and the apply preflight

**Files:** Create `shell-scripts/fixtures/register-stub-cli`, `shell-scripts/test-register-bootstrap.sh`. Modify `register-import`.

**Interfaces:**
- Produces: the stub accepts `whoami`, `register-read`, `add-topic --name N`, `join T`, `post --topic T [--requestId R]` (text on stdin), `messages T --format records`. State is files under `$STUB_STATE`. A repeated `--requestId` returns the first message ID. Failure switches: `STUB_DENY_INDEX=1`, `STUB_INDEX_PRE=<n messages>`, `STUB_FAIL_AFTER_POST=<n>` (store the nth post, then exit 75), `STUB_STALL_READ=1`, `STUB_NO_CORE=1` (exit 69).
- Produces: `register-import apply --bundle DIR --as Mario`. Preflight order: argument check, `check_bundle`, `node9_require_installed`, `CHAT_SHELL_CLI` or `bin/chat-shell-cli` runs, four credentials readable, health on port `6891`, fixed-value check (`fixed` as in `register`).

- [ ] **Step 1: Write failing tests** with the stub, in a temp installed home (run `node-9/install --prefix` into a temp directory):
  - `apply_refuses_source_tree`: no marker, no override gives status `2` and `source tree`.
  - `apply_refuses_other_user`: `--as Sigma` gives status `2`.
  - `apply_refuses_bad_bundle_before_any_call`: the stub log stays empty.
  - `apply_status_for_missing_credential`: remove `Phi.password`. Status `77`, and the stub log is empty.
  - `apply_status_for_absent_core`: `STUB_NO_CORE=1` gives `69`.
  - `apply_refuses_port_override`: `CORE_PORT=1` gives `2`.
  - `apply_prints_no_secret`: no output line holds a password from the fixture.
- [ ] **Step 2: Run.** Expect FAIL. **Step 3: Implement** the stub and the preflight. Health uses `curl` to `127.0.0.1:6891/actuator/health`. The test puts a `curl` stub on `PATH`.
- [ ] **Step 4: Run.** Expect PASS. Commit.

### Task 5: Apply, part 1: the index and its proof

**Files:** Modify `register-import`, `test-register-bootstrap.sh`, the spec if Step 1 changes it.

**Interfaces:**
- Produces: `ensure_index` creates `register-index` as Mario, saves `index.json` (`{"id":..., "owner":"Mario"}`, mode `0600`) in the receipt directory, and returns `0`. It returns `1` on a conflict.
- Produces: `ownership_proof INDEX_ID` prints `Mario` or nothing. It reads the `*` permission row of the index.

- [ ] **Step 1: Define the proof query.** `node-9/schema/keyspace-uuid.cql` has `auth_metadata_target` keyed by `target` with a `permission` column. Write the exact read-only `node9_cqlsh` query and the principal-to-user lookup into the spec section "Destination workflow". The offline stub answers it from `$STUB_STATE/owner`.
- [ ] **Step 2: Write failing tests.**
  - `index_fresh_creates_and_saves_proof`.
  - `index_present_without_proof_and_empty_rebuilds_proof` (`STUB_INDEX_PRE=0`, owner Mario, no `index.json`).
  - `index_present_with_messages_and_no_proof_stops`: status `1`, and no post reached the stub.
  - `index_other_owner_stops`: status `1`.
  - `index_denied_stops`: `STUB_DENY_INDEX=1` gives `1`.
  - `existing_target_topic_stops_before_first_write`.
- [ ] **Step 3: Run.** Expect FAIL. **Step 4: Implement.** **Step 5: Run.** Expect PASS. Commit.

### Task 6: Apply, part 2: topics, pointers, receipts

**Files:** Modify `register-import`, `test-register-bootstrap.sh`.

**Interfaces:**
- Produces: `post_once KIND TOPIC INDEX FILE` posts `FILE` with the derived request ID. It appends `{"requestId","messageId","kind","topic","index"}` to `receipts.jsonl` after success. It refuses a receipt whose message ID differs for the same request ID (status `1`). On an uncertain post it retries once with the same ID, then returns `75`.
- Produces: the snapshot topic is `forward-register-snapshot-<first 12 hex of source sha>` (my call).
- Order: for the snapshot topic, `add-topic`, header, parts in order, session range last. Readback of every message ID and text. All four users join. For each entry, `add-topic`, header, range, readback, joins. Then every key pointer, then the snapshot pointer last. Pointer text is `key | topic | <bundle hash> | title`, and the snapshot key is `snapshot-<12 hex>` (my call).

- [ ] **Step 1: Write failing tests.**
  - `apply_posts_in_spec_order`: the stub log shows the order above, and no pointer before the readback of its topic.
  - `apply_session_range_is_newest`: the session range is the last message of the snapshot topic.
  - `apply_joins_all_four_users`.
  - `retry_after_crash_makes_no_duplicate` (Review Focus 3): `STUB_FAIL_AFTER_POST=3`. The first run exits `75`. The second run exits `0`. Each topic holds each message once.
  - `retry_with_changed_receipt_stops`: edit one receipt message ID. Status `1`.
  - `malformed_receipt_stops`: status `1`.
  - `unrelated_existing_topic_stops`: status `1`.
  - `two_hosts_get_equal_request_ids`: two state directories, one bundle. Equal request-ID lists.
  - `pointer_text_has_four_fields`.
- [ ] **Step 2: Run.** Expect FAIL. **Step 3: Implement.** **Step 4: Run.** Expect PASS. Commit.

### Task 7: Verify

**Files:** Modify `register-import`, `test-register-bootstrap.sh`.

**Interfaces:**
- Produces: `register-import verify --bundle DIR --as Mario` returns `0` only if all checks pass. It reads with `bin/register read --as <user>` for each of Sigma, Theta, Phi, Mario.
- Env: `REGISTER_IMPORT_READ_TARGET_MS` (default `10000`) and `CHAT_REGISTER_DEADLINE_MS` (existing wrapper limit).

- [ ] **Step 1: Write failing tests.**
  - `verify_rebuilds_source_bytes`: the parts joined equal `source.md` by `cmp` and SHA-256.
  - `verify_checks_each_entry_range`.
  - `verify_fails_on_changed_message`: edit one stored message in `$STUB_STATE`. Status `1`.
  - `verify_fails_on_denied_warning_with_status_zero`: the stub prints a `denied-topic` warning and exits `0`. Verify exits `1`.
  - `verify_median_gate`: stub sleep of 200 ms with target 50 ms exits `1`. Sleep 0 exits `0`.
  - `verify_stall_returns_124`: `STUB_STALL_READ=1` with `CHAT_REGISTER_DEADLINE_MS=1000` exits `124`.
- [ ] **Step 2: Run.** Expect FAIL. **Step 3: Implement** the five timed reads from command start to the last byte. Use the median. **Step 4: Run.** Expect PASS. Commit.

### Task 8: Contract and documentation

**Files:** Modify `node-9/bin/register` (header), `docs/SHELL-COMMAND-CONTRACT.md`, `node-9/README.md`.

- [ ] **Step 1:** In the `register` header, replace "writes an empty metadata field and never reads it" with: the tool writes an empty field. The importer writes the bundle hash there. No reader interprets it. Make the same change in the contract.
- [ ] **Step 2:** Add a README section "Import a register snapshot": the three commands, the exit table, the receipt path, the rule that Mario keeps agents off the host until `verify` passes, and the UTC-date review rule.
- [ ] **Step 3:** Run `drift check` and `git diff --check`. Update prose before any `drift link`. Commit.

### Task 9: Remove the prototype

**Files:** Remove `node-9/bin/import-register`, `shell-scripts/test-register-apply.sh`. Modify `shell-scripts/test-register-import.sh` or remove it.

- [ ] **Step 1:** List the cases of the two old suites. For each, name the new case that covers it, or the reason none is needed. Record the list in the commit message.
- [ ] **Step 2:** Move uncovered cases into the new suites. Keep `register-split` and its byte-preserving test.
- [ ] **Step 3:** Remove the old files. Do not delete the ignored local preparation state. Ask Mario first.
- [ ] **Step 4:** Run both new suites and `bash shell-scripts/test-node-9.sh`. Expect all to pass. Commit.

### Task 10: Live gate and proof (blocked on `CHAT-bjqzvsoe`)

**Files:** Create `docs/superpowers/proofs/2026-10-21-portable-register-import/RECORD.md`.

- [ ] **Step 1: Check the gate.** `CHAT-bjqzvsoe` has passed its proof: installed `whoami` as Theta, status `0`, identity `Theta`, empty standard error. If not, stop. Do not run Steps 2 to 7.
- [ ] **Step 2:** Run `/opt/demo-chat/node-9/bin/setup-secrets --owner` from Mario's terminal. Confirm `CHAT_USER_MARIO_PASSWORD` is rendered. Run `/opt/demo-chat/node-9/bin/start`. Expect health on port `6891`.
- [ ] **Step 3:** Sigma drafts the snapshot and mapping from the six-item checklist in the spec. The source date equals the UTC import date. Mario confirms both hashes.
- [ ] **Step 4:** Run `pack`. Copy the bundle to the host. Run `apply --as Mario`. Expect status `0`.
- [ ] **Step 5:** Run `verify --as Mario`. Expect status `0` and a median at or under 10 s.
- [ ] **Step 6:** Write `RECORD.md` with hashes, message IDs, counts, statuses, and times. Include no password and no register text.
- [ ] **Step 7:** Mario reviews the record. Run `fp comment CHAT-jmnjvupr` with the result. Stage 4B starts only after that review.

## Self-Review

- **Spec coverage:** pack rules, identity and IDs, bundle modes (Tasks 2, 3). Guard, CLI, credentials, health, fixed values (Task 4). Index, proof, crash recovery (Task 5). Topics, joins, pointers, receipts, retry (Task 6). Verify, median gate, 124 (Task 7). Contract, metadata, exit table (Task 8). Prototype removal (Task 9). Snapshot checklist, UTC rule, CLI prerequisite, proof (Task 10, and Task 1 for the rebase).
- **Gap marked:** the spec names a "Cassandra `*` permission row" and gives no query. Task 5 Step 1 writes it into the spec before code.
- **Calls I made:** bundle file names, topic regex, snapshot topic and key names, request ID name format, and the use of Perl for ranges and UUIDv5.
- **Not covered by offline tests:** the real shell CLI, the real `*` row, and the live 30-second boundary. Stage 3 recorded the boundary. Task 10 covers the rest.
