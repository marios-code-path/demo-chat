# Stage 1 acceptance record

Run on 2026-10-18 under `CHAT-jmnjvupr`, in two passes. The script is
`cases.sh`. Every case was one `chat-shell-cli` process in the stage 0 sandbox,
against the disposable memory core on 6990 and 6991. The isolation probe ran
first, and no denied port connected. The script exited 0 both times.

**Pass 1** ran at `e5fb3ed3`. Cases A8 to A10 were blocked, because `add-topic`
failed on the core (`CHAT-siaetqtr`). **Pass 2** ran after the fix of that issue
and after the review of PR #259. The table below is pass 2.

## Results

| ID | Command | Expected | Exit | Result | Wall time | Standard error |
|----|---------|----------|------|--------|-----------|----------------|
| A1 | `whoami` | 0, empty standard error | 0 | pass | 3,544 ms | empty |
| A2 | `whoami`, login as Admin from a password file | 0, empty standard error | 0 | pass | 3,756 ms | empty |
| A3 | `whoami`, wrong password | 77 | 77 | pass | 3,550 ms | `chat-shell: Authentication failed: Invalid Credentials` |
| A4 | `whoami`, `CHAT_SHELL_USER` alone | 2 | 2 | pass | 3,292 ms | `chat-shell: set both CHAT_SHELL_USER and CHAT_SHELL_PASSWORD_FILE` |
| A5 | `no-such-command` | 2 | 2 | pass | 3,321 ms | `chat-shell: unknown command no-such-command` |
| A6 | `@x` | 2 | 2 | pass | 3,315 ms | `chat-shell: a script file is not allowed in this mode` |
| A7 | `listen lobby` | 2 | 2 | pass | 3,534 ms | `chat-shell: listen is not allowed in this mode` |
| A13 | `! false` | 2 | 2 | pass | 3,330 ms | `chat-shell: ! is not allowed in this mode` |
| A14 | `register-read`, launch stamp 28 s in the past | 124, empty standard output | 124 | pass | 2,158 ms | `chat-shell: register-read passed its 30000 ms limit` |
| A15 | `register-read`, launch stamp 31 s in the past | 124, empty standard output | 124 | pass | 219 ms | `chat-shell: register-read passed its 30000 ms limit` |
| A8 | `add-topic stage1-room`, login | 0 | 0 | pass | 3,748 ms | empty |
| A9 | `post stage1-room`, 3-line UTF-8 input with no final newline | 0 | 0 | pass | 3,980 ms | empty |
| A10 | `messages stage1-room --format records` | 0, the A9 text exactly | 0 | pass | 3,968 ms | empty |
| A11 | resident `messages` | 0 | not run | not built | | task 9 is not built |
| A12 | `whoami`, core stopped | 69 | 69 | pass | 3,124 ms | `chat-shell: Connection refused` |

N2, N5, and N10 did not run. The native image does not start a context
(`docs/NATIVE-IMAGE.md`, section "Shell one-shot").

## What the record shows

- **A9 and A10 pass.** The record of A10 holds the text of A9 byte for byte,
  with its UTF-8 characters, its line ends, and no final newline. The script
  compares them with `jq`. The comparison goes through shell command substitution,
  which strips trailing newlines. The entry has none, so the test is exact for this
  entry. The unit tests of `PubSubPostTests` cover entries with trailing newlines
  and `\r\n`.
- **The standard error of A1, A2, A8, A9, and A10 is empty.** The design
  requires this after a success.
- **The fix of `CHAT-siaetqtr`.** The shell connects with CBOR, and CBOR writes a
  UUID as 16 bytes. The `rootOf` route decodes an untyped payload, so the id
  arrived as a byte array and `ExactIds.uuid` refused it. The client answered the
  zero UUID for the same bytes. Both now read 16 bytes as a UUID. Any other length
  is refused. The other tests used a JSON requester, which sends text, so they hid
  this. See `UUIDRootOfTransportTests` and the commit message.
- **The topic-read time of A10 is 3,968 ms.** One JVM process takes 3.1 to 4.0 s.
  See `READ-TIME.md` for a complete register read.
- **`register-read` meets the 10-second target.** It reads the index and the
  newest entry of 3 topics in one process, in 4,476 ms and 4,479 ms in the median of
  five runs (two runs of five),
  from the launch to the exit (`read.sh register`, see `READ-TIME.md`). It is not
  one of the numbered cases. Its checks are in `read.sh`.
- **The end state is clean.** No listener on 6990, 6991, or 9101. The core group
  is empty. `docker ps` is the same before and after.

## Not run

- A11, because resident mode (task 9) is not built.
- N2, N5, and N10, because the native image does not start.

## The gates

The gates are in the pull request description and in the comment on
`CHAT-jmnjvupr`. They ran again after the fix of `CHAT-siaetqtr`.
