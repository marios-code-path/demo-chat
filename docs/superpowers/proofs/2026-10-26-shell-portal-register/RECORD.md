# Shell portal register proof

Date: 2026-10-26.
Base commit: `d7b2ed4535e91e71083bd560a57e97751685e2c5`.
Task 7 source commit: `61b4f5e95461834d528a064683f143d9ea67ca1b`.
That commit contains `PortalEndToEndStdioTests.kt` and `acceptance.sh`.

## Local verification

`just build-shell-cli` exited 0 after the repeat-reply fix.
The build log reported `BUILD SUCCESS` and `[build-shell-cli] Done in 19 s`.

`mvn -B -ntp -pl chat-shell -am test -Dtest=PortalSocketServerTests -Dsurefire.failIfNoSpecifiedTests=false` exited 0.
The focused suite ran 17 tests with no failures, errors, or skips.
The tests prove two sequential requests each receive a reply.
A protocol failure followed by a valid request also receives both replies.
The test run against the old shared reply flag exited 1 with 2 failures.

`mvn -B -ntp -pl chat-shell,chat-mcp -am test` exited 0.
The full suite ran after the protocol-failure follow-up test was added.
The `chat-shell` suite ran 236 tests with 0 failures, 0 errors, and 1 skip.
The `chat-mcp` suite ran 274 tests with 0 failures, 0 errors, and 0 skips.
The full reactor reported `BUILD SUCCESS`.
`PortalEndToEndStdioTests` ran 6 tests with no failures, errors, or skips.

`bash -n` passed for both portal proof scripts.
`git diff --check` exited 0.
`drift refs` found no bindings for the changed documentation or isolation script.
`drift check` exited 0.

## Disposable core acceptance

The command was `just build-shell-cli`, followed by:

```sh
portal_acceptance_log_dir="$(mktemp -d /private/tmp/chat-portal-task7-acceptance.XXXXXX)"
chmod 700 "$portal_acceptance_log_dir"
bash docs/superpowers/proofs/2026-10-26-shell-portal-register/acceptance.sh "$portal_acceptance_log_dir"
```

The acceptance command exited 0.
It used `isolation.sb.sh` and `core.sh` with one disposable core on ports 6990 and 6991.
The real stdio adapter discovered `chat_read_register` and made two calls in one MCP session.
Both calls returned status 0, `complete=true`, and zero lines.
The empty result was expected because the disposable index had no records.
The adapter exited 0, and stdout had no parse failures.
Both reads used shell PID 77730 in process group 77730.
The socket remained present through both reads and was removed after shutdown.
No shell JVM remained after shutdown.
The private run logs were in `/private/tmp/chat-portal-task7-acceptance.SZdbJZ`.

The harness reported Node `v26.7.0`, SDK `1.32.0`, and protocol `2025-11-25`.
The test fake portal counted one socket connection for each tool call.
The disposable acceptance did not instrument server-side socket accept counts.

The native image proof remains outside this task.

## Installed Node 9 comparison

The installed runtime marker is `/opt/demo-chat/node-9/.installed`.
It reports install time `2026-10-24T02:57:55Z`, source commit `3b025171c06441e441db919f8a3da01d46d71aca`, and `cli: lib, java 25`.
The installed register wrapper hash is `08483f7727dee53aa4edba73d396daa9ac517149a24ff4acaf3dcf04c091e7b3`.
The checkout wrapper hash is `365d93bec74e304459e78faa529a00393d245d010681752a48158afd46da184e`.
The installed wrapper uses the older fixed register read contract.
The checkout wrapper uses the named `register-read --index` contract.
The wrappers do not have compatible read contracts.

Task 7 requires compatibility before a Node 9 read.
No live Node 9 read ran because that check failed.
The Sigma register read also needs elevated access.
The earlier `sudo -n` attempt failed because sudo required a password.
No register content or password appears in this record.

The live wrapper parity, register counts, warning kinds, process IDs, and connection counts remain unverified.
