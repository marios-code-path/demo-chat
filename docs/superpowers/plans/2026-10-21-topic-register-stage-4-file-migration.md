# Topic Register Stage 4: File Migration Plan

> **Execution:** Work inline with `superpowers:executing-plans`. `AGENTS.md` forbids subagent-driven development.

**Goal:** Import the frozen register files into node 9, prove exact recovery, then remove the tracked files.

**Architecture:** A local importer reads seven Git blobs from H. It posts a header and ordered sections to one topic per file. A stored manifest makes retries use the same request IDs. The importer verifies every message before it writes an archive pointer. File removal follows full byte and read-time checks.

**Tech stack:** Bash 3.2, Perl, `jq`, `shasum`, Git, `chat-shell-cli`, and the node-9 core.

**Issue:** `CHAT-acsvenkb`, child of `CHAT-jmnjvupr`.

**Source commit H:** `2d75f0cdd09115da422cdc0a693f0227f31a6ef5`.

## Boundaries

- Use H for every source byte. Do not edit the frozen file.
- Import all six tracked archives and `forward-register.md`.
- Leave the untracked `forward-register-archive.md` in the main checkout untouched.
- Never run `chat-deploy core`. Keep node 16 outside the import path.
- Do not send a secret in an argument or a proof file.
- Use one named user for all import messages. The user must have a working password file.
- Stop before a live write if credentials, health, or index access fail.
- Stop before file removal if any byte comparison, membership check, or read-time gate fails.
- Keep the Git blobs at H after the working-tree files leave the repository.
- Do not merge without Mario's explicit authorization and named `--admin` bypass.

## Sources and topics

| Source at H | Topic |
|---|---|
| `forward-register.md` | `forward-register-frozen-2026-10-21` |
| `forward-register-archive-2026-08.md` | `forward-register-archive-2026-08` |
| `forward-register-archive-2026-09.md` | `forward-register-archive-2026-09` |
| `forward-register-archive-2026-10.md` | `forward-register-archive-2026-10` |
| `forward-register-archive-2026-10-17.md` | `forward-register-archive-2026-10-17` |
| `forward-register-archive-2026-10-18.md` | `forward-register-archive-2026-10-18` |
| `forward-register-archive-2026-10-19.md` | `forward-register-archive-2026-10-19` |

The seven blobs contain 117 sections and 317,268 bytes. Each file has a preamble before its first `##` heading. Each source is valid UTF-8 with no NUL byte. The largest section is 9,512 bytes. These are preflight measurements from 2026-10-21.

## Task 1: Prove the byte split offline

**Files:** Create `node-9/bin/register-split` and `shell-scripts/test-register-import.sh`.

- [x] Use `register-split <source-file> <new-output-directory>`.
- [x] Refuse an existing output directory. Create the new directory with mode `0700`.
- [x] Write `section-000000.bin` and later numbered files with mode `0600`.
- [x] Write `sections.jsonl` with each section's index, path, byte count, and SHA-256.
- [x] Read one source blob as raw bytes. Split immediately before each line that starts with `## `.
- [x] Keep the preamble as section zero. Keep every line ending and the final-byte state.
- [x] Write each section to a separate private file. Write its index, byte count, and SHA-256 to a manifest.
- [x] Refuse a source with invalid UTF-8, a NUL byte, or no preamble.
- [x] Test LF, CRLF, UTF-8, empty sections, and a missing final newline.
- [x] Join the test sections by index. Require `cmp` against each test source.
- [x] Run the splitter on all seven H blobs. Require 117 sections and 317,268 joined bytes.
- [x] Run `bash shell-scripts/test-register-import.sh`, `bash -n`, `perl -c`, `drift check`, and `git diff --check`.
- [x] Commit the splitter and tests with `CHAT-acsvenkb` in the message.

**Stop gate:** A byte difference or invalid source stops the task. Do not normalize text.

## Task 2: Prepare stable import requests

**Files:** Create `node-9/bin/import-register`. Extend `shell-scripts/test-register-import.sh`.

- [ ] Require H, the seven exact source names, `CHAT_SHELL_USER`, and `CHAT_SHELL_PASSWORD_FILE`.
- [ ] Set `CORE_PORT=6890`, `CORE_MGMT_PORT=6891`, node ID `9`, and key type `uuid` for every shell call.
- [ ] Refuse a caller value that would redirect one of those settings.
- [ ] Build a private manifest before any live write. Save one request ID per header, section, and pointer.
- [ ] Reuse the saved IDs on every retry. Never use `_` as a request ID.
- [ ] Put the source name, H, source SHA-256, and section count in the header text.
- [ ] Keep the header outside the reconstructed section bytes.
- [ ] Test a retry after each failure point with a stub shell. Require no second message for one request ID.
- [ ] Test missing credentials, wrong ports, topic conflicts, malformed receipts, and a denied index.
- [ ] Require one clear error line and no success claim after a failed shell call.
- [ ] Run the offline suite and the cheap checks. Commit the importer with the issue ID.

**Stop gate:** A retry must use the original request ID and the original section bytes.

## Task 3: Restore access and inspect node 9

**Files:** Add a preflight record under `docs/superpowers/proofs/2026-10-21-register-migration/`.

- [ ] Locate the current agent credentials. Use the approved source. Do not rotate a live user by accident.
- [ ] Require a successful `whoami` and a readable `register-index` for the chosen user.
- [ ] Require health on 6891 and an unchanged node-16 container list.
- [ ] Check all seven topic names. Refuse a topic with unrelated content.
- [ ] Record only status, IDs, counts, hashes, and topic names. Record no password or message text.
- [ ] Stop if any preflight check fails. Keep node 9 and the files unchanged.

**Credential gate:** The main checkout had no `node-9/secrets/Sigma.password` on 2026-10-21. A read returned `77`. Resolve this before Task 4.

## Task 4: Import and confirm each topic

**Files:** Use `node-9/bin/import-register`. Save the resulting manifest in the proof directory.

- [ ] Create one topic per source. Join it as the import user.
- [ ] Post the header first. Post each section in file order.
- [ ] Pass the saved request ID on every post. Save each returned message ID.
- [ ] Read the topic after its posts. Match each planned request and message ID.
- [ ] On an uncertain post, retry with the same request ID and bytes.
- [ ] Join Sigma, Theta, Phi, and Mario to every archive topic.
- [ ] Post one `-` pointer per verified topic to `register-index`.
- [ ] Read the index. Require each pointer message ID and the intended topic.
- [ ] Stop on a denied read, a duplicate pointer for the same file, or an unexpected topic owner.

**Stop gate:** A pointer follows the full topic readback. A partial topic has no new pointer.

## Task 5: Prove exact recovery and read cost

**Files:** Add a verification record under `docs/superpowers/proofs/2026-10-21-register-migration/`.

- [ ] Read each topic as records. Reject a missing, repeated, or unexpected section message ID.
- [ ] Select sections by manifest index. Write each record's text with `jq -j`, without an added newline.
- [ ] Compare the joined bytes with `git show H:<source>` by `cmp` and SHA-256.
- [ ] Require seven exact matches. Record every source hash and recovered hash.
- [ ] Read the full register as Sigma, Theta, Phi, and Mario.
- [ ] Require status `0`, empty standard error, and no denied warning for each user.
- [ ] Measure at least five complete session reads. Record the median from command start to last byte.
- [ ] If the median exceeds 10 seconds, fix the query cost and repeat this task.
- [ ] Require status `124` at the 30-second limit for a stalled read. Discard its output.

**Stop gate:** Any byte difference or read-time failure leaves all seven files in the repository.

## Task 6: Remove files and update current documentation

**Files:** Remove the seven tracked register files. Modify `README.md`, `docs/BUILD-HEALTH.md`, `shell-scripts/build-health.sh`, and two test comments. Keep `AGENTS.md` pointing to H.

- [ ] Remove only the seven tracked files in the table. Leave the unrelated untracked file untouched.
- [ ] Remove the 65,536-byte file gate from `shell-scripts/build-health.sh`.
- [ ] Update the active register guidance in `README.md` and `docs/BUILD-HEALTH.md`.
- [ ] Update the comments in `DeadEmbeddingEndpointTests.kt` and `RedisDeadEmbeddingEndpointTests.kt`.
- [ ] Keep historical specs, plans, and proofs as history. Correct only active claims that now mislead readers.
- [ ] Prove `git show H:forward-register.md` still has the frozen SHA-256.
- [ ] Run `drift check`, the import suite, both node-9 suites, and `git diff --check`.
- [ ] Commit the removal and guidance with the issue ID.

**Stop gate:** Do not remove a file until Task 5 has seven exact matches and the read target passes.

## Task 7: Final gates and PR

- [ ] Stop node 9 before `shell-scripts/test-node-9.sh`. Restart it after the suite for session reads.
- [ ] Run `shell-scripts/build-health.sh` on the final branch head.
- [ ] Run the register suite, import suite, drift check, and whitespace check.
- [ ] Open a draft PR for stage 4. Check build, integration, and agent-http on its final head.
- [ ] Review the PR and proof in this session. Ask Mario to name an independent reviewer if needed.
- [ ] Keep the PR unmerged until Mario gives explicit merge authorization.
- [ ] Add a final comment to `CHAT-acsvenkb` with the measured results and open limits.

## Decisions before live writes

1. Import all seven tracked files. The old design omitted two later archives.
2. Use the topic names in the table. Refuse a name with unrelated content.
3. Archive pointers can appear in the session read for 24 hours. The read-time gate must still pass.
4. Select the import user after credentials are restored. Use that same user for all seven topics.
