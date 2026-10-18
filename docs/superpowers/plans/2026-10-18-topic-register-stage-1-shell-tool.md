# Topic register, stage 1: chat-shell as a command tool

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task by task. `AGENTS.md` forbids subagent-driven development, so do not use it. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chat-shell` runs one command per process, or serves commands on a Unix domain socket. It starts silently, logs in from the environment, and gives one exit status per command from the design's table.

**Architecture:** One execution path in `chat-shell` parses a line, refuses the forbidden forms, runs the command with a given writer and input, and maps any failure to a status. A one-shot runner calls it. A new entry point in `chat-deploy` sets the silent mode and turns each status into the process exit status. A launcher script starts the JVM directly, from a classpath that a `just` recipe builds. A native one-shot image is a measured option. A complete register read is measured for each one-shot launcher, and the owner then decides whether the resident socket server is built.

**Tech Stack:** Kotlin, Spring Boot 4.0.8, Spring Shell 4.0.3, GraalVM CE 25 native image (proof only), Java 25 Unix domain sockets, Bash, `nc -U`.

**Spec:** `docs/superpowers/specs/2026-10-17-topic-register-design.md`, section "chat-shell as a command tool", parts 1 to 5. **Baseline:** `docs/superpowers/proofs/2026-10-17-one-shot/RECORD.md`. Read its "Effect on stage 1" table first.

## Global Constraints

- The exit status table of the design, part 2, exactly: 0 success, 1 the command ran and failed, 2 the line is not valid or the mode refuses it, 69 the core or the socket is not available, 75 a post was not confirmed, 76 a protocol error, 77 the login failed, 124 the time limit.
- The modes refuse `listen`, `hangup`, `script`, and a first word that starts with `@`, with status 2.
- The interactive shell keeps its current behaviour. A launch without the new settings changes nothing.
- `CORE_PORT` and `CORE_MGMT_PORT` route the shell. No new address setting (record Q6).
- A password never goes in a program argument.
- Kotlin main code. Bash for the launcher and the client. No new Kotlin tool, so no GraalVM requirement applies.
- `CHAT-siaetqtr` (`add-topic` fails on the core) is a separate blocker. This stage does not fix it.
- Never connect to node 16. Every live run in this plan uses the stage 0 isolation and the disposable core on 6990 and 6991.
- One Maven build per worktree. Log each build to a file.
- Prose follows ASD-STE100 strict mode. Every merge needs the owner's named `--admin` bypass.

## Review Focus

1. **A new direct print.** A later change can add `println` to a command, and the modes then leak text to standard output. Task 2 adds a source scan that fails on any direct print outside `PubSubCommands.listen`.
2. **Entry text at its edges.** An entry can have no trailing newline, CRLF line ends, non-ASCII text, or no text. Task 3 tests each one, and the text arrives byte for byte.
3. **A password file with a trailing newline.** `echo secret > file` writes one. Task 5 removes one trailing `\n` or `\r\n` and nothing more.
4. **A short resident request.** A client sends fewer bytes than its header declares and then waits. Task 9, if it runs, tests that the server answers status 76 after the 10-second request limit, and serves the next client.
5. **A stale or live socket at start.** A `kill -9` leaves a socket file. Task 9, if it runs, tests that a start removes a stale file, and that a start exits 69 when another resident shell answers on the path.

---

### Task 1: The exit status and the execution path

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ShellExitStatus.kt`
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/CommandLineExecution.kt`
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/StreamInput.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/CommandLineExecutionTests.kt`

**Interfaces:**
- Produces: `enum class ShellExitStatus(val code: Int) { OK(0), FAILED(1), USAGE(2), UNAVAILABLE(69), NOT_CONFIRMED(75), PROTOCOL(76), LOGIN(77), TIMEOUT(124) }` with `companion object { fun of(error: Throwable): ShellExitStatus }`.
- Produces: `class ShellExit(val status: ShellExitStatus, message: String) : RuntimeException(message)`.
- Produces: `class StreamInput(input: InputStream) : InputReader` with `fun readAll(): String`. It decodes the bytes as UTF-8 and changes nothing.
- Produces: `class CommandLineExecution(parser: CommandParser, registry: CommandRegistry)` with `fun run(line: String, input: InputStream, out: PrintWriter, err: PrintWriter): ShellExitStatus`. It is a Spring bean in profile `shell`.

`ShellExitStatus.of` walks the cause chain. `ShellExit` gives its own status. `CoreAuthenticationRefusal` gives `LOGIN`. `java.net.ConnectException` or `WebClientRequestException` gives `UNAVAILABLE`. `IllegalArgumentException` or `CommandNotFoundException` gives `USAGE`. Any other error gives `FAILED`.

`run` writes each failure as one line on `err`: `chat-shell: <message>`, where `<message>` is the first line of the deepest cause message. It writes nothing else on `err`.

- [ ] **Step 1: Write the failing tests**

Build a `CommandRegistry` from small `Command` objects, in the style of `HostCommandTests`. Tests:

```kotlin
@Test fun `a command result goes to the writer, and the status is OK`()          // "echo-ish" command → out == "hi\n", status OK, err empty
@Test fun `a blank line is refused with USAGE`()
@Test fun `a first word that starts with @ is refused with USAGE`()               // "@/tmp/x" → USAGE, err "chat-shell: a script file is not allowed in this mode"
@Test fun `listen, hangup, and script are refused with USAGE`()                   // each → USAGE, the command is not called
@Test fun `an unknown command gives USAGE`()                                     // err "chat-shell: unknown command no-such-command"
@Test fun `an IllegalArgumentException gives USAGE and one err line`()
@Test fun `a CoreAuthenticationRefusal anywhere in the causes gives LOGIN`()
@Test fun `a ConnectException anywhere in the causes gives UNAVAILABLE`()
@Test fun `any other error gives FAILED`()
@Test fun `a ShellExit gives its own status`()
@Test fun `the command reads the whole input through StreamInput`()               // input "a\r\nb" (no final newline) → readAll() == "a\r\nb"
```

- [ ] **Step 2: Run them, and see them fail**

Run: `mvn -o -B -ntp -pl chat-core,chat-shell-host,chat-shell test -Dtest=CommandLineExecutionTests -Dsurefire.failIfNoSpecifiedTests=false > "$LOG" 2>&1; tail -20 "$LOG"`
Expected: a compile failure on the missing classes. Then add empty stubs, and see 11 test failures.

- [ ] **Step 3: Implement the three files**

- [ ] **Step 4: Run the tests, and see them pass**

Run: the command of step 2. Expected: 11 passed.

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/cli chat-shell/src/test/kotlin/com/demo/chat/test/cli
git commit -m "CHAT-jmnjvupr: Add the shell exit status and one execution path"
```

### Task 2: One output path

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt` (`send`, `commandStatus`)
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/LoginCommands.kt` (`login`)
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/UserCommands.kt` (`getUser`)
- Modify: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubSubmitTests.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/DirectOutputTests.kt`

**Interfaces:**
- Produces: `PubSubCommands.send(...)` and `PubSubCommands.commandStatus(...)` return `String`, with the same lines as today. The registrar returns that text, so it reaches the writer.
- Produces: `LoginCommands.login` throws its error with the message `Authentication failed: <reason>`, and prints nothing.

- [ ] **Step 1: Write the failing tests**

```kotlin
// DirectOutputTests
@Test fun `send, command-status, and a failed login write nothing to System out`()   // capture System.out with System.setOut, run each through CommandLineExecution with mocked services, assert captured == ""
@Test fun `no chat-shell main source prints directly, except listen`()                // scan chat-shell/src/main/kotlin for "println(", "print(", "System.out", "System.err"; the only allowed hits are inside PubSubCommands.listen
```

Change `PubSubSubmitTests` so that each test asserts the returned text, not captured output.

- [ ] **Step 2: Run them, and see them fail**

Run: `mvn -o -B -ntp -pl chat-core,chat-shell-host,chat-shell test -Dtest='DirectOutputTests,PubSubSubmitTests' -Dsurefire.failIfNoSpecifiedTests=false > "$LOG" 2>&1; tail -20 "$LOG"`
Expected: `DirectOutputTests` fails on the captured text and on the scan hits in `PubSubCommands`, `LoginCommands`, and `UserCommands`.

- [ ] **Step 3: Move the four prints to returned text, and remove the `KEY =` debug print**

- [ ] **Step 4: Run the tests, and see them pass**

Run: the command of step 2, then `mvn -o -B -ntp -pl chat-core,chat-shell-host,chat-shell test > "$LOG" 2>&1`. Expected: all chat-shell unit tests pass.

- [ ] **Step 5: Commit**

```bash
git commit -am "CHAT-jmnjvupr: Send all command output through the context writer"
```

### Task 3: The `post` command

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt`
- Modify: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ShellCommandContractTests.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubPostTests.kt`

**Interfaces:**
- Consumes: `StreamInput.readAll()` from task 1.
- Produces: the command `post`, group `PubSub`, description `Post the text of standard input to a topic`. Main option `--topic` (required, also the first bare word). Option `--requestId` (default `_`, which makes a new ID as `send` does).
- Produces: `PubSubCommands.post(topic: String, text: String, requestId: String): String`. It submits `MessageSubmitRequest(text, roomId, requestId)` and answers one JSON line with the fields `messageId`, `commandId`, `requestId`, and `outcome`. It uses the `ObjectMapper` that `ClaudeImportMap` uses.
- A context whose input reader is not a `StreamInput` gives `IllegalArgumentException("post reads its text from standard input. Use it in one-shot or resident mode.")`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `post sends the input text byte for byte`()        // texts: "a\nb\n", "a\r\nb", "no newline", "ünïcode ✓\n"; captured MessageSubmitRequest.msg equals each
@Test fun `post refuses empty input with USAGE`()           // "" → IllegalArgumentException "post needs the entry text on standard input."
@Test fun `post answers one JSON line with the four fields`()
@Test fun `post outside the modes is refused`()             // a context with a mock InputReader → IllegalArgumentException
```

In `ShellCommandContractTests`, expect 29 commands and 43 options, and pin `post` with its name, group, description, main option, and two options.

- [ ] **Step 2: Run them, and see them fail**

Run: `mvn -o -B -ntp -pl chat-core,chat-shell-host,chat-shell test -Dtest='PubSubPostTests,ShellCommandContractTests' -Dsurefire.failIfNoSpecifiedTests=false > "$LOG" 2>&1; tail -20 "$LOG"`
Expected: a compile failure on `post`. With a stub, 4 test failures and the contract count failure.

- [ ] **Step 3: Implement `post` and its registration**

- [ ] **Step 4: Run the tests, and see them pass**

- [ ] **Step 5: Commit**

```bash
git commit -am "CHAT-jmnjvupr: Add post, which sends standard input to a topic"
```

### Task 4: `messages --format records`

**Files:**
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt` (`messages`)
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommandsRegistrar.kt`
- Modify: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubMessagesTests.kt`
- Modify: `chat-shell/src/test/kotlin/com/demo/chat/test/commands/ShellCommandContractTests.kt`

**Interfaces:**
- Produces: `PubSubCommands.messages(topic: String, limit: Int? = null, format: String = "human", since: Instant? = null): String`. New options: `--format` (default `human`, or `records`) and `--since` (an ISO-8601 instant, no default).
- In `records` format, each message is one JSON line with `id`, `topic`, `sender`, `timestamp`, and `text`. `timestamp` is `Instant.toString()` of the stored value, so UTC with the full stored precision. `text` is the body unchanged. `sender` is the handle, or the key ID when the user is missing, as in the human format.
- `--since` keeps messages at or after the instant. `--limit` then keeps the newest N. Order stays oldest first. An empty result prints nothing in `records` format.
- `--format` with another value, or a `--since` value that does not parse, gives `IllegalArgumentException`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `records print one JSON line per message with an exact UTC timestamp`()   // stored 2026-10-17T23:21:19.123456789Z → "timestamp":"2026-10-17T23:21:19.123456789Z"
@Test fun `records keep a multi-line body unchanged`()                              // "a\nb" → JSON text "a\nb", one output line
@Test fun `since keeps messages at or after the instant, then limit keeps the newest`()
@Test fun `records print nothing for an empty topic`()
@Test fun `an unknown format and a bad since are refused`()
@Test fun `the human format is unchanged`()                                         // the existing tests still pass
```

In `ShellCommandContractTests`, expect 45 options.

- [ ] **Step 2: Run them, and see them fail.** Same command shape as task 3, with `-Dtest='PubSubMessagesTests,ShellCommandContractTests'`.

- [ ] **Step 3: Implement the options**

- [ ] **Step 4: Run the tests, and see them pass**

- [ ] **Step 5: Commit**

```bash
git commit -am "CHAT-jmnjvupr: Add a records format and since to messages"
```

### Task 5: The login from the environment

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/EnvironmentLogin.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/EnvironmentLoginTests.kt`

**Interfaces:**
- Consumes: `LoginCommands.login(username, password)`, and `ShellExit` from task 1.
- Produces: `class EnvironmentLogin(env: (String) -> String?, login: (String, String) -> Unit)` with `fun loginIfSet()`. The modes call it once, before the first command.

Rules:
- Neither `CHAT_SHELL_USER` nor `CHAT_SHELL_PASSWORD_FILE` set: no login.
- One set without the other: `ShellExit(USAGE, "set both CHAT_SHELL_USER and CHAT_SHELL_PASSWORD_FILE")`.
- A password file that cannot be read, or that is empty after the newline removal: `ShellExit(LOGIN, ...)`. The message names the file, never the password.
- One trailing `\n` or `\r\n` is removed. Nothing else changes.
- A refused login: `ShellExit(LOGIN, "Authentication failed: <reason>")`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `no variables means no login`()
@Test fun `one variable alone is refused with USAGE`()
@Test fun `the password file loses one trailing newline and nothing more`()   // "pw\n" → "pw", "pw\r\n" → "pw", "pw\n\n" → "pw\n", " pw " → " pw "
@Test fun `a missing or empty password file gives LOGIN, and the message has no password`()
@Test fun `a refused login gives LOGIN`()
```

- [ ] **Step 2: Run them, and see them fail.** `-Dtest=EnvironmentLoginTests`.

- [ ] **Step 3: Implement `EnvironmentLogin`**

- [ ] **Step 4: Run the tests, and see them pass**

- [ ] **Step 5: Commit**

```bash
git add chat-shell/src/main/kotlin/com/demo/chat/shell/cli/EnvironmentLogin.kt chat-shell/src/test/kotlin/com/demo/chat/test/cli/EnvironmentLoginTests.kt
git commit -m "CHAT-jmnjvupr: Log in from CHAT_SHELL_USER and CHAT_SHELL_PASSWORD_FILE"
```

### Task 6: One-shot mode, the entry point, and the launcher

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ShellModeConfiguration.kt`
- Create: `chat-deploy/src/main/kotlin/com/demo/chat/ShellCli.kt`
- Create: `shell-scripts/chat-shell-cli`
- Modify: `justfile` (new recipe `build-shell-cli`)
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/OneShotRunnerTests.kt`

**Interfaces:**
- Consumes: `CommandLineExecution`, `EnvironmentLogin`, `ShellExit`.
- Produces: the property `chat.shell.mode`, with the values `interactive` (default), `one-shot`, and `resident`. For `one-shot`, `ShellModeConfiguration` defines the bean `springShellApplicationRunner`. That name replaces the runner of Spring Shell. It calls `EnvironmentLogin.loginIfSet()`, joins the program arguments into one line, and calls `CommandLineExecution.run(line, System.in, stdout, stderr)`. A status other than `OK` throws `ShellExit`.
- Produces: `com.demo.chat.ShellCliKt.main(args)`. It runs `ChatApp` with the banner off, closes the context, and exits with the status. It catches every throwable from the run. A `ShellExit` already wrote its line. For any other throwable, it writes one line `chat-shell: <message>` on standard error and exits with `ShellExitStatus.of(error)`. A core that is not available at start (record F2) therefore gives 69.
- Produces: `shell-scripts/chat-shell-cli [command words...]`. It needs `CHAT_SHELL_KEY_TYPE`. It reads `CHAT_SHELL_NODE_ID` (default `1`), `CORE_PORT`, and `CORE_MGMT_PORT`. It takes the JVM options from `chat-build shell --run --notls --node-id <id> --key-type <type> --dry-run`, as the stage 0 `launch.sh` does. It then sets these options last: `-Dchat.shell.mode=one-shot`, `-Dspring.shell.interactive.enabled=false`, `-Dspring.main.banner-mode=off`, `-Dlogging.threshold.console=OFF`, `-Dspring.main.web-application-type=none`, and `--sun-misc-unsafe-memory-access=allow`. `CHAT_SHELL_LOG`, when set, gives `-Dlogging.file.name`. The main class is `com.demo.chat.ShellCliKt`.
- Produces: `just build-shell-cli`. It runs `build-shell`, compiles `chat-deploy` with `deploy,shell,shell-host,client-backend`, and writes `chat-deploy/target/shell-cli.classpath` with `-DincludeScope=runtime`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// OneShotRunnerTests: an ApplicationContextRunner with ShellModeConfiguration, a fake execution and a fake login
@Test fun `one-shot mode defines springShellApplicationRunner, and interactive mode does not`()
@Test fun `the runner logs in before the command`()
@Test fun `the runner joins the arguments into one line`()     // ["messages", "lobby", "--limit", "1"] → "messages lobby --limit 1"
@Test fun `a status other than OK becomes a ShellExit with that status`()
```

- [ ] **Step 2: Run them, and see them fail.** `-Dtest=OneShotRunnerTests`.

- [ ] **Step 3: Implement the configuration, `ShellCli.kt`, the launcher, and the recipe**

- [ ] **Step 4: Run the tests, and see them pass**

- [ ] **Step 5: Check the JVM warnings on a live run**

Start the stage 0 disposable core inside the stage 0 sandbox (`docs/superpowers/proofs/2026-10-17-one-shot/isolation.sb.sh`, `core.sh start`). Run `just build-shell-cli`. Then run, inside the same sandbox, `CHAT_SHELL_KEY_TYPE=uuid CORE_PORT=6990 CORE_MGMT_PORT=6991 shell-scripts/chat-shell-cli whoami`.
Expected: exit 0, one line on standard output, and nothing on standard error except `WARNING: Using incubator modules: jdk.incubator.vector`.

If the context fails with `spring.main.web-application-type=none`, set `reactive` with `server.port=0` and `management.server.port=0` in its place. A port that the kernel picks still lets two runs start at once (record F3). Record that change in the ledger as a ruling.

Then remove `--add-modules=jdk.incubator.vector` from the launcher options and run again. If the run still exits 0, keep it removed, and standard error is empty. If a class of `jdk.incubator.vector` is missing, keep the module, record the one warning line in the ledger as a ruling, and report it to the owner. Stop the core with `core.sh stop`.

- [ ] **Step 6: Commit**

```bash
git add chat-shell chat-deploy/src/main/kotlin/com/demo/chat/ShellCli.kt shell-scripts/chat-shell-cli justfile
git commit -m "CHAT-jmnjvupr: Add one-shot mode, the ShellCli entry point, and chat-shell-cli"
```

### Task 7: The native one-shot proof

The owner asked on 2026-10-18 for native one-shot as an option, measured before stage 1 commits to resident mode. A native image starts faster, but it does not change output, login, exit status, or command input. Tasks 1 to 6 give those in both paths.

**Constraint:** `docs/NATIVE-IMAGE.md` records that the `chat-deploy` native image builds but does not start a full context. Spring AOT fixes properties and profiles at build time, so a native shell is one fixed composition. `chat-build --native` is refused. `CHAT-ulrkvfit` records that `chat-shell` has no native profile. This task is a proof with a limit, not a native delivery.

**Files:**
- Create: `docs/superpowers/proofs/2026-10-18-stage-1/native.sh`
- Modify: `docs/NATIVE-IMAGE.md` (a "Shell one-shot" section with the result)

**Interfaces:**
- Consumes: `ShellCli.kt` and `ShellModeConfiguration` from task 6, and the stage 0 isolation and core scripts.
- Produces: `native.sh build <scratch-dir>` and `native.sh run <scratch-dir> [command words...]`. `build` writes the image path to `<scratch-dir>/native-image`. `run` starts the image inside the sandbox with the environment of `chat-shell-cli`.

- [ ] **Step 1: Write `native.sh build`**

It runs `mvn -B -Pnative,deploy,shell,shell-host,client-backend -pl chat-deploy -am package -Dmaven.test.skip=true` with `-Dspring-boot.aot.jvmArguments="<options>"`. `<options>` are the options that `chat-shell-cli` sets for `CHAT_SHELL_KEY_TYPE=uuid`, with `-Dchat.shell.mode=one-shot`, because AOT fixes the mode at build time. The main class is `com.demo.chat.ShellCliKt`. The log goes to `$S/native-build.log`. The plugin may need the network on its first run (`docs/NATIVE-IMAGE.md`).

- [ ] **Step 2: Build, and start one command**

Start the disposable core in the sandbox. Run `native.sh build "$S"`, then `native.sh run "$S" whoami`.
Expected: one of two results, and both are valid results of this task:
- the image runs `whoami` and exits 0 with one line of output, or
- the context fails. Record the first failure line, as the "Observed failures" table of `docs/NATIVE-IMAGE.md` does.

Make at most three build attempts. Each attempt may change only AOT properties or the main class. A fourth attempt, or a change to a product bean, is out of scope. Record the result, and continue with task 8.

- [ ] **Step 3: Record the result**

Add a "Shell one-shot" section to `docs/NATIVE-IMAGE.md`: the command, the AOT properties, the image size and build time, and the run result.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/proofs/2026-10-18-stage-1/native.sh docs/NATIVE-IMAGE.md
git commit -m "CHAT-jmnjvupr: Record the native one-shot shell proof"
```

### Task 8: Measure a complete register read, then stop for the owner

This task decides whether task 9 (resident mode) runs. The owner makes the decision from these numbers.

**A complete register read** is the read of the session-start command in the design: the index, then the newest entry of each recent topic. The representative data is one `register-index` topic with 20 pointers, and 3 recent topics with 5 entries of about 2 KB each. The read is 1 index read and 3 topic reads, so 4 reads.

**Files:**
- Create: `docs/superpowers/proofs/2026-10-18-stage-1/read.sh`
- Create: `docs/superpowers/proofs/2026-10-18-stage-1/READ-TIME.md`

**Interfaces:**
- Consumes: `chat-shell-cli` from task 6, and `native.sh run` from task 7 if the image ran.

- [ ] **Step 1: Write `read.sh`**

`read.sh seed <scratch-dir>` creates the representative data on the disposable core: 4 topics with `add-topic`, 20 pointer lines with `post` to `register-index`, and 5 entries with `post` to each of the 3 other topics. `read.sh measure <scratch-dir> <launcher>` runs the 4 reads as 4 one-shot processes, one after the other: `messages register-index --format records` and `messages <topic> --format records --limit 1` for each topic. It measures the total wall time, from the first process start to the last process exit. It runs 5 times, and it reports each total and the median.

**`add-topic` fails today (`CHAT-siaetqtr`).** If that issue is not fixed, `seed` cannot run. Then `measure` uses a stand-in: 4 `show-topics` reads, each one RSocket round trip, with no data. The record names the stand-in. A stand-in measures startup and round trips, not representative data.

- [ ] **Step 2: Measure**

Inside the sandbox, with the disposable core: run `read.sh measure` with `chat-shell-cli`, and with `native.sh run` if task 7 ran the image.
Expected: 5 totals and a median for each launcher. No case hits a denied port.

- [ ] **Step 3: Write `READ-TIME.md`**

It holds a table of each launcher, the median total, and the 10-second target of owner decision 20. It also states whether the data was representative or the stand-in.

- [ ] **Step 4: Commit, push, and stop**

```bash
git add docs/superpowers/proofs/2026-10-18-stage-1/read.sh docs/superpowers/proofs/2026-10-18-stage-1/READ-TIME.md
git commit -m "CHAT-jmnjvupr: Measure a complete register read for each one-shot launcher"
git push
```

Comment on `CHAT-jmnjvupr` and PR #257 with the table. **Stop here.** The owner chooses one of these:
- A one-shot launcher meets the target. Task 9 then moves to its own issue, and stage 1 continues with task 10.
- No one-shot launcher meets the target. Task 9 then runs.
- The owner names another choice.

### Task 9: Resident mode (only after the owner's choice in task 8)

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ResidentProtocol.kt`
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ResidentShellServer.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ShellModeConfiguration.kt`
- Modify: `shell-scripts/chat-shell-cli` (a `--resident` flag)
- Create: `shell-scripts/chat-shell-client`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/ResidentProtocolTests.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/cli/ResidentShellServerTests.kt`

**Interfaces:**
- Produces: `object ResidentProtocol` with `fun readRequest(channel: ReadableByteChannel): Request` (`Request(line: String, input: ByteArray)`) and `fun writeResponse(channel: WritableByteChannel, status: ShellExitStatus, output: ByteArray)`. The request header is `chat-shell/1 <command-bytes> <input-bytes>\n`. The response header is `chat-shell/1 <status> <output-bytes>\n`. Lengths count UTF-8 bytes. A bad header or a short body throws `ShellExit(PROTOCOL, ...)`.
- Produces: `class ResidentShellServer(socket: Path, run: (String, InputStream, PrintWriter, PrintWriter) -> ShellExitStatus, requestTimeout: Duration = 10.seconds, commandTimeout: Duration)` with `fun bind()` and `fun serve(stop: AtomicBoolean)`.
- `bind()` rules: the parent directory must be owned by the current user with mode `0700`, or `ShellExit(USAGE, ...)`. An existing path that is not a socket gives `ShellExit(USAGE, ...)`. An existing socket that accepts a connect gives `ShellExit(UNAVAILABLE, "another resident shell holds <path>")`. An existing socket that refuses is removed. The new socket file gets mode `0600`.
- `serve` runs one request at a time. The output of `err` goes into the response body after the output of `out`. At `commandTimeout`, it answers 124 and replaces its worker thread, so a stuck command does not block the next request. On stop, it removes the socket file.
- For `resident`, `ShellModeConfiguration` defines `springShellApplicationRunner`: login, `bind()`, then `serve` until the context closes. `CHAT_SHELL_SOCKET` gives the path, and it has no default. `CHAT_SHELL_COMMAND_TIMEOUT` gives the command limit in seconds, default 120.
- `shell-scripts/chat-shell-client <command words...>` reads `CHAT_SHELL_SOCKET`, sends the words as the line and its standard input as the input, prints the body, and exits with the status. No socket file, or a refused connect, gives 69. A short or bad response gives 76.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ResidentProtocolTests
@Test fun `a request round-trips with a multi-line UTF-8 input`()
@Test fun `a bad header or a short body is a PROTOCOL ShellExit`()
@Test fun `the status comes first, so output that looks like a header is only body`()   // output "chat-shell/1 0 5\n" round-trips as body

// ResidentShellServerTests, with @TempDir set to mode 0700 and a fake run function
@Test fun `a request gets the run output and its status`()
@Test fun `the socket file has mode 0600`()
@Test fun `a parent directory that is not 0700 is refused`()
@Test fun `a stale socket file is removed at bind`()
@Test fun `a live socket makes bind fail with UNAVAILABLE`()
@Test fun `a short request gets 76 after the request limit, and the next client is served`()   // requestTimeout = 1 s in the test
@Test fun `a command past its limit gets 124, and the next request is served`()                 // commandTimeout = 1 s, the fake run blocks forever on the first call
@Test fun `two clients at once each get their own response`()
@Test fun `stop removes the socket file`()
```

- [ ] **Step 2: Run them, and see them fail.** `-Dtest='ResidentProtocolTests,ResidentShellServerTests'`.

- [ ] **Step 3: Implement the protocol, the server, the configuration, and the client**

- [ ] **Step 4: Run the tests, and see them pass**

- [ ] **Step 5: Commit**

```bash
git add chat-shell shell-scripts/chat-shell-cli shell-scripts/chat-shell-client
git commit -m "CHAT-jmnjvupr: Add resident mode on a Unix domain socket, and chat-shell-client"
```

### Task 10: Acceptance against the disposable core, the documents, and the gates

**Files:**
- Create: `docs/superpowers/proofs/2026-10-18-stage-1/cases.sh`
- Create: `docs/superpowers/proofs/2026-10-18-stage-1/RECORD.md`
- Modify: `docs/SHELL-COMMAND-CONTRACT.md`
- Modify: `docs/BUILD.md`
- Modify: `docs/superpowers/specs/2026-10-17-topic-register-design.md` ("Measured in stage 0" gains a stage 1 section, and "Unverified" items 6 and 9 change)

**Interfaces:**
- Consumes: the stage 0 scripts `isolation.sb.sh`, `Probe.java`, and `core.sh`, unchanged. Every live case runs inside the sandbox.

- [ ] **Step 1: Write `cases.sh`**

It reuses the stage 0 case loop: one process group per case, a 60-second limit, and the same row format. It runs the isolation probe first and stops if any denied port connects. Cases, in this order:

| ID | Mode | Command | Input or setting | Expected status |
|----|------|---------|------------------|-----------------|
| A1 | one-shot | `whoami` | none | 0, empty standard error |
| A2 | one-shot | `whoami` | `CHAT_SHELL_USER=Admin` and a password file | 0 |
| A3 | one-shot | `whoami` | a password file with a wrong password | 77 |
| A4 | one-shot | `whoami` | `CHAT_SHELL_USER` alone | 2 |
| A5 | one-shot | `no-such-command` | none | 2 |
| A6 | one-shot | `@x` | none | 2 |
| A7 | one-shot | `listen lobby` | none | 2 |
| A8 | one-shot | `add-topic --name stage1-room` | login | 0 if `CHAT-siaetqtr` is fixed. Otherwise 1, and A9 to A11 record "blocked by CHAT-siaetqtr". |
| A9 | one-shot | `post stage1-room` | login, a 3-line UTF-8 input with no final newline | 0 |
| A10 | one-shot | `messages stage1-room --format records` | login | 0, and the A9 text exactly |
| A11 | resident | `messages stage1-room --format records` | login, a socket in a `0700` directory | 0, and the A9 text exactly. Only if task 9 ran. |
| A12 | one-shot | `whoami` | the core stopped | 69 |

Record the wall time of A10, and of A11 if task 9 ran. These are the topic-read times that decision 20 asks stage 1 to measure. If task 7 ran the native image, add A2, A5, and A10 again with `native.sh run`, as N2, N5, and N10.

- [ ] **Step 2: Run it**

Run: `docs/superpowers/proofs/2026-10-18-stage-1/cases.sh "$S" > "$S/cases.log" 2>&1; echo $?`
Expected: exit 0. Every row has its expected status. A1 and A2 have empty standard error, or the one ruled warning line of task 6 step 5. The end state check of stage 0 passes: no listener on 6990, 6991, or 9101, an empty core group, and an unchanged `docker ps`.

- [ ] **Step 3: Write `RECORD.md`**

It holds the results table, the A10 and A11 times, the standard error of A1 and A2, and the result of A8 to A11 (passed, or blocked by `CHAT-siaetqtr`).

- [ ] **Step 4: Update the documents**

- `docs/SHELL-COMMAND-CONTRACT.md`: `post`, the new `messages` options, the counts (29 commands and 45 options in chat-shell, 30 in a default build), the modes that exist, the exit status table, the refused forms, and the environment login.
- `docs/BUILD.md`: `just build-shell-cli`, `chat-shell-cli`, and their variables. Add `chat-shell-client` only if task 9 ran.
- The design: a "Measured in stage 1" section with the A10 time, the A11 time if task 9 ran, the native result of task 7, and the read times of task 8. Item 6 becomes measured if A9 and A10 passed. Item 9 becomes measured by A2 and A3.

- [ ] **Step 5: Run the gates**

Run: `shell-scripts/build-health.sh > "$S/gate-default.log" 2>&1; echo $?`, then `DOCKER_CONFIG=$(mktemp -d) shell-scripts/build-health.sh --ci > "$S/gate-ci.log" 2>&1; echo $?`, then `docker events --since <ci start> --until <ci end> --filter event=oom`.
Expected: both exit 0, `reality matches docs/BUILD-HEALTH.md`, `agent http gate: ok`, and no OOM event. Then `git diff --check master...HEAD` and `drift check` pass.

- [ ] **Step 6: Commit, push, and log**

```bash
git add docs
git commit -m "CHAT-jmnjvupr: Record the stage 1 acceptance, and document the shell modes"
git push
```

Comment on `CHAT-jmnjvupr` with the A-case results, the A10 and A11 times, and the gate totals.
