# Stage 1 acceptance record

Run on 2026-10-18 under `CHAT-jmnjvupr`, at the head of the branch
`chat-jmnjvupr-topic-register`. The script is `cases.sh`. Every case was one
`chat-shell-cli` process in the stage 0 sandbox, against the disposable memory
core on 6990 and 6991. The isolation probe ran first, and no denied port
connected. The script exited 0.

**The acceptance of `post` and `messages --format records` is incomplete.**
`add-topic` fails on the core (`CHAT-siaetqtr`), so cases A9 and A10 did not run.
The unit tests of tasks 3 and 4 do not replace a live run. These tests exist and
pass: `PubSubPostTests` and `PubSubMessagesTests`.

## Results

| ID | Command | Expected | Exit | Result | Wall time | Standard error |
|----|---------|----------|------|--------|-----------|----------------|
| A1 | `whoami` | 0, empty standard error | 0 | pass | 3,875 ms | empty |
| A2 | `whoami`, login as Admin from a password file | 0, empty standard error | 0 | pass | 3,884 ms | empty |
| A3 | `whoami`, wrong password | 77 | 77 | pass | 3,651 ms | `chat-shell: Authentication failed: Invalid Credentials` |
| A4 | `whoami`, `CHAT_SHELL_USER` alone | 2 | 2 | pass | 3,658 ms | `chat-shell: set both CHAT_SHELL_USER and CHAT_SHELL_PASSWORD_FILE` |
| A5 | `no-such-command` | 2 | 2 | pass | 3,445 ms | `chat-shell: unknown command no-such-command` |
| A6 | `@x` | 2 | 2 | pass | 3,656 ms | `chat-shell: a script file is not allowed in this mode` |
| A7 | `listen lobby` | 2 | 2 | pass | 3,660 ms | `chat-shell: listen is not allowed in this mode` |
| A8 | `add-topic stage1-room`, login | 0 | 1 | **blocked** | 3,864 ms | `chat-shell: A key id cannot be a byte[].` |
| A9 | `post stage1-room`, 3-line UTF-8 input | 0 | not run | **blocked** | | blocked by `CHAT-siaetqtr` |
| A10 | `messages stage1-room --format records` | 0, the A9 text exactly | not run | **blocked** | | blocked by `CHAT-siaetqtr` |
| A11 | resident `messages` | 0 | not run | not built | | task 9 is not built |
| A12 | `whoami`, core stopped | 69 | 69 | pass | 3,235 ms | `chat-shell: Connection refused` |

N2, N5, and N10 did not run. The native image does not start a context
(`docs/NATIVE-IMAGE.md`, section "Shell one-shot").

## What the record shows

- **The standard error of A1 and A2 is empty.** The design requires this after a
  success. The launcher drops `--add-modules=jdk.incubator.vector` to get it.
- **A8 fails with the finding F1 of stage 0.** The one-shot path reports it as
  status 1 with one line on standard error. This also shows that the one-shot path
  does not cause F1, because the status and line come from the core refusal.
  Whether the interactive shell fails the same way is still not known.
- **The A10 topic-read time was not measured.** A single JVM process takes 3.2 to
  3.9 s in A1 to A12. See `READ-TIME.md` for four reads in a row.
- **The end state is clean.** No listener on 6990, 6991, or 9101. The core group
  is empty. `docker ps` is the same before and after.

## Not run

- A9, A10, and A11, as the table says.

## The gates

| Gate | Result |
|------|--------|
| `shell-scripts/build-health.sh` | exit 0. 31 modules, 2,354 tests, 0 failures, 0 errors, 47 skipped. `reality matches docs/BUILD-HEALTH.md`. |
| `shell-scripts/build-health.sh --ci`, empty `DOCKER_CONFIG` | exit 0. 31 modules, 2,776 tests, 0 failures, 0 errors, 82 skipped. `agent http gate: ok`. `reality matches docs/BUILD-HEALTH.md`. |
| `docker events` for `oom`, over the CI window | no event |
| `git diff --check master...HEAD` | pass |
| `drift check` | pass |
