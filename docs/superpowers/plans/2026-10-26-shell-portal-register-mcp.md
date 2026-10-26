# Shell portal register MCP implementation plan

> **For agentic workers:** Use superpowers:executing-plans task by task. AGENTS.md forbids subagents. Steps use checkboxes.

**Goal:** Add `chat_read_register` through one logged-in shell portal process, without a new shell JVM for each read.

**Architecture:** A dedicated `portal` shell mode serves bounded JSON requests on a Unix socket. The existing `chat-mcp` process calls that socket. Its current tools retain REST.

**Tech Stack:** Kotlin, Java 25 Unix sockets, Spring Shell 4, Jackson, Kotlin MCP SDK, JUnit 5, Bash.

**Tracking:** CHAT-hmbigvmf. Approved specification: `docs/superpowers/specs/2026-10-25-register-read-mcp-design.md`.

---

## Boundaries and order

Run every task inline. Write a failing test before production code.
Commit only the named paths. Do not push new commits without owner direction.
Do not install on node 9 or stop node 9 during this plan.
The older Stage 1 Task 9 defines a general command socket. This plan creates a separate read-only portal protocol.
The portal holds one fixed login. The MCP config holds one fixed register selector.
The MCP caller supplies only an optional topic. The socket never accepts a shell command line.
Requester reuse does not prove one physical RSocket connection.

| Unit | Responsibility | Planned file |
|---|---|---|
| Protocol | Validate JSON frames, names, types, and byte limits. | `chat-shell/.../portal/PortalProtocol.kt` |
| Read service | Call shell reads and normalize typed output. | `chat-shell/.../portal/PortalReadService.kt` |
| Socket server | Bind, report BUSY, enforce deadlines, clean its socket. | `chat-shell/.../portal/PortalSocketServer.kt` |
| Shell entry | Require login, expose interactive command, launch portal mode. | `ShellModeConfiguration.kt`, `PortalCommandRegistrar.kt`, launcher |
| MCP client | Load fixed settings and exchange one JSON frame. | `AdapterConfig.kt`, `AdapterConfigLoader.kt`, `PortalClient.kt` |
| MCP tool | Validate topic and return structured results. | `PortalToolRegistration.kt`, `McpAdapterServer.kt` |
| Proof | Check stdio, process reuse, wrapper parity, and docs. | Tests and documentation |

The table abbreviates paths. Each task names exact paths.

These signatures keep the tasks aligned:

- `PortalProtocol(ObjectMapper).readRequest(InputStream): PortalRequest`.
- `PortalProtocol.writeReply(OutputStream, PortalReply)`.
- `PortalReadService(readIndex, readTopic, ObjectMapper).read(PortalRequest, Duration): PortalReply`.
- `PortalSocketServer.bind()`, `serve()`, and `close()`.
- `PortalClient(Path, readTimeout: Duration = Duration.ofSeconds(35)).read(index: String, topic: String?): JsonObject`.
- `registerPortalTool(Server, AdapterConfig, PortalClient)`.

`chat-mcp` does not depend on `chat-shell`. Its client validates the JSON reply independently.

## Task 1: Define the portal wire contract

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalProtocol.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalProtocolTests.kt`

**Contract:** `PortalRequest(operation: String, index: String, topic: String?)`.
Construct `PortalProtocol(ObjectMapper())` in the protocol tests.
The operation is `read-register`. Names match `[A-Za-z0-9][A-Za-z0-9-]{0,63}`.
The request holds one JSON object and ends with one newline.
The client half-closes its write side after that frame.
The server reads to end of input before it starts a backend read.
`PortalReply` holds `status: Int?`, `lines: List<JsonNode>`, `complete: Boolean`, and optional `error.code`.
Define error codes `INVALID_INPUT`, `BUSY`, `UNAVAILABLE`, `TIMEOUT`, `OUTPUT_TOO_LARGE`, `INVALID_OUTPUT`, and `READ_FAILED`.
The request limit is 4096 UTF-8 bytes. The reply limit is 4194304 UTF-8 bytes.

- [x] **Step 1: Add failing tests.** Cover valid topic, empty frame, bad UTF-8, extra field, non-string name, invalid name, two frames, and byte limits.

```kotlin
@Test fun `a topic request keeps its selector and topic`() {
    val protocol = PortalProtocol(ObjectMapper())
    val input = "{\"operation\":\"read-register\",\"index\":\"register-index\",\"topic\":\"CHAT-123\"}\n"
    assertEquals(PortalRequest("read-register", "register-index", "CHAT-123"),
        protocol.readRequest(input.byteInputStream()))
}
```

- [x] **Step 2: Run the focused test.** Run `mvn -B -pl chat-shell -am test -Dtest=PortalProtocolTests -Dsurefire.failIfNoSpecifiedTests=false`.
  Expected: compilation fails because `PortalProtocol` is absent.
- [x] **Step 3: Add the protocol.** Reject malformed UTF-8 and count bytes before JSON parsing.
  Read through end of input. Reject unknown JSON keys and extra frames.
  Serialize one bounded reply.
- [x] **Step 4: Run the same test.** Expected: `PortalProtocolTests` passes.
- [x] **Step 5: Commit.** Stage the two named files. Use `CHAT-hmbigvmf: Define portal JSON frames`.

## Task 2: Map shell reads to portal replies

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalReadService.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalReadServiceTests.kt`
- Read: `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt`
- Read: `node-9/bin/register`

**Seam:** Construct `PortalReadService(readIndex, readTopic, ObjectMapper())`.
Inject `readIndex: (String, Duration) -> String` and `readTopic: (String) -> String`.
Production wiring calls `PubSubCommands.registerRead(index, Duration.ofHours(24), remaining)`.
The topic lambda calls `PubSubCommands.messages(topic, format = "records")`.
This seam tests output without another JVM or a live RSocket server.

- [ ] **Step 1: Add failing tests.** Cover ordered index lines, typed topic lines, empty success, and invalid JSON.
  Test `duplicate-pointer`, `denied-index`, `denied-topic`, unknown warnings, and shell statuses separately.
  Wrap a `ShellExit(TIMEOUT)` in another exception. Prove status 124 still produces `TIMEOUT`.

```kotlin
@Test fun `an unknown warning makes the read incomplete`() {
    val service = PortalReadService(
        readIndex = { _, _ -> "{\"type\":\"warning\",\"kind\":\"new-kind\"}" },
        readTopic = { "" },
        mapper = ObjectMapper())
    val reply = service
        .read(PortalRequest("read-register", "index", null), Duration.ofSeconds(30))
    assertFalse(reply.complete)
}
```

- [ ] **Step 2: Run `PortalReadServiceTests`.** Use the Task 1 Maven command with `-Dtest=PortalReadServiceTests`.
  Expected: compilation fails because `PortalReadService` is absent.
- [ ] **Step 3: Add the service.** Split nonempty output on line boundaries. Parse each line as one JSON object.
  Require `type=record` or `type=warning` for index lines. Add `type=record` to topic lines.
  Set `complete=false` for every warning except `duplicate-pointer`.
  Use `ShellExitStatus.of(error)` to read the status through wrapped causes.
  Map status 69 to `UNAVAILABLE` and 124 to `TIMEOUT`.
  Map malformed typed lines to `INVALID_OUTPUT` with null status.
  Map other shell failures to `READ_FAILED` with their original status.
  Return no partial lines on failure. Keep raw exception text out of replies.
- [ ] **Step 4: Run the focused test.** Expected: all read-service cases pass.
- [ ] **Step 5: Commit.** Stage the two named files. Use `CHAT-hmbigvmf: Map shell register reads`.

## Task 3: Serve and protect one portal socket

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalSocketServer.kt`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalSocketServerTests.kt`

**Seam:** `PortalSocketServer(socket: Path, protocol: PortalProtocol, read: (PortalRequest, Duration) -> PortalReply, deadline: Duration, halt: (Int) -> Unit, identityOf: (Path) -> SocketIdentity?)`.
Use `ServerSocketChannel` with `StandardProtocolFamily.UNIX`.
The production deadline is 30 seconds. Tests supply a shorter deadline and a recording `halt` function.
The production `halt` calls `Runtime.getRuntime().halt(code)`.
`SocketIdentity` holds the owner and file key returned by `lstat`.
Tests inject `identityOf` to prove mismatch and unavailable-identity behavior.
Production uses `lstatSocketIdentity` as the default `identityOf`.

- [ ] **Step 1: Add failing tests.** Use a private temporary parent with mode `0700`.
  Cover bind, mode `0600`, occupied file, stale socket, owner mismatch, path replacement, shutdown, and one request per connection.
  Check `INVALID_INPUT` for a bad frame and `OUTPUT_TOO_LARGE` for a large reply.
  Hold the first read in a latch. Prove the second connection gets `BUSY` without another backend read.
  Inject a short deadline. Prove timeout calls `halt(124)` and blocks later reads.
  Return a `TIMEOUT` reply before the socket deadline. Prove `halt(124)` and no later backend read.
  Check one bounded standard-error diagnostic with no request text or secret values.
- [ ] **Step 2: Run `PortalSocketServerTests`.** Use the Task 1 Maven command with `-Dtest=PortalSocketServerTests`.
  Expected: compilation fails because `PortalSocketServer` is absent.
- [ ] **Step 3: Add the server.** Check parent owner and mode before bind. Refuse every existing path, including a stale socket.
  Set socket mode `0600`. Record owner and file identity with `lstat` after bind.
  Close and fail startup if either value is invalid. Leave an unproven path untouched.
  At shutdown, unlink only when owner and file identity still match.
  Keep the accept loop free while one worker reads. Reject concurrent calls with `BUSY`.
  Map frame and reply-size failures to their fixed error codes with null status.
  Start each 30-second budget at accept. Include parsing, backend work, and response write.
  On a `TIMEOUT` read reply, mark the server terminal before writing the reply.
  Apply the same atomic transition when the socket deadline expires. Never clear `BUSY` after that transition.
  Let only the first timeout path perform cleanup and call `halt(124)`.
  Attempt `TIMEOUT`, close the client, and apply safe socket cleanup.
  End the portal process after cleanup.
  Keep a separate hard-stop timer so a blocked write cannot retain the process.
  Write one bounded code-and-status diagnostic for each failed request to standard error.
  Do not log request data.
- [ ] **Step 4: Run the focused test.** Expected: ownership, BUSY, deadline, and cleanup cases pass.
- [ ] **Step 5: Commit.** Stage the two named files. Use `CHAT-hmbigvmf: Serve the guarded portal socket`.

## Task 4: Expose portal mode and the interactive command

**Files:**
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalCommandRegistrar.kt`
- Create: `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalConfiguration.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ShellModeConfiguration.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/EnvironmentLogin.kt`
- Modify: `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/CommandLineExecution.kt`
- Modify: `shell-scripts/chat-shell-cli`
- Create: `shell-scripts/chat-shell-portal`
- Test: `chat-shell/src/test/kotlin/com/demo/chat/test/portal/PortalModeTests.kt`
- Test: `shell-scripts/test-chat-shell-portal.sh`

**Wiring:** `PortalCommandRegistrar` registers `portal --socket <absolute-path>` for interactive use.
`PortalConfiguration` wires `PubSubCommands`, `PortalReadService`, `PortalProtocol`, and the server factory.
The command checks `ShellStateConfiguration.loggedInUser` and `loginMetadata` before bind.
`ShellModeConfiguration` adds `springShellApplicationRunner` only for `chat.shell.mode=portal`.
That runner calls `EnvironmentLogin.loginRequired()` before it binds the socket.
`EnvironmentLogin.loginRequired()` checks both values and returns status 77 when either is absent.
It then calls `loginIfSet()` for the existing credential check.
`CommandLineExecution` refuses `portal` in one-shot mode.

- [ ] **Step 1: Add failing tests.** Test missing and refused login before bind.
  Test one valid login in portal mode and one valid login through the interactive command.
  Test the interactive login gate, command registration, one-shot refusal, and stop-signal cleanup.
  Test source and installed option fixtures with `CHAT_SHELL_CLI_PRINT_OPTIONS=1`.
  Prove the installed fixture holds exactly one portal mode option and no one-shot mode option.
- [ ] **Step 2: Run focused tests.** Run `mvn -B -pl chat-shell -am test -Dtest=PortalModeTests -Dsurefire.failIfNoSpecifiedTests=false`.
  Expected: the mode and command tests fail because portal wiring is absent.
- [ ] **Step 3: Add mode wiring and launcher.** The new script requires `CHAT_SHELL_USER`, `CHAT_SHELL_PASSWORD_FILE`, and `CHAT_SHELL_PORTAL_SOCKET`.
  It invokes the installed or source shell classpath once with `chat.shell.mode=portal`.
  Set `CHAT_SHELL_MODE=portal` in `chat-shell-portal` before it invokes `chat-shell-cli`.
  In `chat-shell-cli`, accept only `one-shot` and `portal` for `CHAT_SHELL_MODE`.
  Filter `-Dchat.shell.mode=*` from installed options. Append the selected mode exactly once.
  Filter any mode option from the source plan. Append the selected mode exactly once.
  Keep `one-shot` as the default.
  Preserve one-shot command and watchdog behavior. Close the server through one shutdown hook.
- [ ] **Step 4: Run tests.** Run the focused Maven command and `bash shell-scripts/test-chat-shell-portal.sh`.
  Expected: both pass. The shell test finds one `-Dchat.shell.mode=portal` option.
- [ ] **Step 5: Commit.** Stage only the files listed in this task.
  Use `CHAT-hmbigvmf: Add portal shell launch paths`.

## Task 5: Configure and call the portal from chat-mcp

**Files:**
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/AdapterConfig.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/config/AdapterConfigLoader.kt`
- Create: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalClient.kt`
- Modify: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/config/AdapterConfigLoaderTests.kt`
- Modify: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/tool/ToolTestFixtures.kt`
- Create: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalClientTests.kt`

**Config:** Add nullable `portalSocket`, `registerName`, and `registerId` fields with absent defaults.
Require an absolute socket path. Reject every partial setting.
Select `registerName` before `registerId` when both exist.
Expose that choice as `AdapterConfig.registerSelector: String?`.
The client opens one Unix socket connection per call and exchanges one frame.

- [ ] **Step 1: Add failing tests.** Cover absent portal config, complete config, both selectors, relative path, unknown key, and partial config.
  Cover missing socket, short reply, oversized reply, and an intact status from a portal failure.
  Let a fake portal accept a connection without replying. Use a 100 ms test deadline.
  Prove the client returns `UNAVAILABLE` after that deadline.

```kotlin
@Test fun `the register name wins when both selectors exist`() {
    val config = loadConfig(properties(
        "portalSocket" to "/tmp/chat-portal.sock",
        "registerName" to "register-index",
        "registerId" to "123e4567-e89b-12d3-a456-426614174000"), baseDirectory)
    assertEquals("register-index", config.registerSelector)
}
```

- [ ] **Step 2: Run focused tests.** Run `mvn -B -pl chat-mcp -am test -Dtest=AdapterConfigLoaderTests,PortalClientTests -Dsurefire.failIfNoSpecifiedTests=false`.
  Expected: compilation or assertions fail for the new config and client.
- [ ] **Step 3: Add config and client.** Keep REST settings required for current tools.
  Validate selectors with the portal name rule. Never place credentials in a portal request.
  Decode one bounded JSON reply. Preserve `error.code`, `status`, `complete`, and line order.
  Half-close the socket write side after sending the request frame.
  Use a nonblocking `SocketChannel` and `Selector` for the complete exchange.
  Set a 35-second default deadline. Close the socket when the deadline expires.
  Map connect, framing, and read-deadline failures to `UNAVAILABLE` with null status.
  Do not retry `BUSY` or another failure.
- [ ] **Step 4: Run focused tests.** Expected: config and socket client cases pass.
- [ ] **Step 5: Commit.** Stage only the files listed in this task.
  Use `CHAT-hmbigvmf: Add fixed portal client configuration`.

## Task 6: Register the MCP read tool

**Files:**
- Create: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt`
- Modify: `chat-mcp/src/main/kotlin/com/demo/chat/mcp/McpAdapterServer.kt`
- Modify: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/McpAdapterToolStdioTests.kt`
- Create: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/portal/PortalToolTests.kt`

**Tool:** Register `chat_read_register` only when portal settings are complete.
Accept no properties except optional string `topic`. Reject empty, invalid, and non-string values.
Keep selector and identity out of the tool schema.
Success carries `status`, `lines`, and `complete` in structured content and JSON text content.
An incomplete read keeps its lines and sets `isError=true`.
A failed read returns `error.code`, `status`, `complete=false`, and no partial lines.

- [ ] **Step 1: Add failing tool tests.** Test discovery, input schema, hints, both read forms, warnings, BUSY, missing portal, and failed status propagation.
  Define a `FakePortal` helper in `PortalToolTests.kt`.
  It binds one temporary Unix socket and returns one canned reply per connection.
  Extend `testConfig` in Task 5 with optional portal settings.

```kotlin
FakeTopicBackend().use { backend ->
    FakePortal("{\"status\":0,\"lines\":[],\"complete\":true}\n").use { portal ->
        val config = testConfig(backend.origin, listOf(LongId(7)),
            portalSocket = portal.path, registerName = "register-index")
        StdioHarness(config).use { client ->
            client.initialize()
            val reply = client.callTool(3, "chat_read_register", "{\"topic\":\"CHAT-123\"}")
            assertFalse(client.isError(reply))
            assertEquals(0, client.structuredOf(reply)["status"]!!.jsonPrimitive.int)
        }
    }
}
```

- [ ] **Step 2: Run focused tests.** Run `mvn -B -pl chat-mcp -am test -Dtest=PortalToolTests,McpAdapterToolStdioTests -Dsurefire.failIfNoSpecifiedTests=false`.
  Expected: discovery or compilation fails for `chat_read_register`.
- [ ] **Step 3: Register the tool.** Use the existing `Server.addTool` pattern.
  Set read-only and idempotent hints. Run blocking socket work on `Dispatchers.IO`.
  Return fixed error sentences. Keep paths, credentials, and raw exceptions out of stderr and results.
- [ ] **Step 4: Run focused tests.** Expected: new tool and existing stdio assertions pass.
- [ ] **Step 5: Commit.** Stage only the files listed in this task.
  Use `CHAT-hmbigvmf: Expose the portal register tool`.

## Task 7: Check the complete path and update docs

**Files:**
- Modify: `docs/MCP-ADAPTER.md`
- Modify: `docs/BUILD.md`
- Modify: `docs/SHELL-COMMAND-CONTRACT.md`
- Create: `chat-mcp/src/test/kotlin/com/demo/chat/mcp/PortalEndToEndStdioTests.kt`
- Create: `docs/superpowers/proofs/2026-10-26-shell-portal-register/acceptance.sh`
- Create: `docs/superpowers/proofs/2026-10-26-shell-portal-register/RECORD.md`

- [ ] **Step 1: Add an end-to-end stdio test.** Start a fake local portal socket.
  Drive the pinned MCP client through `StdioHarness`.
  Check discovery, typed index output, topic output, warnings, and failed `error.code`.
- [ ] **Step 2: Run the new test.** Run `mvn -B -pl chat-mcp -am test -Dtest=PortalEndToEndStdioTests -Dsurefire.failIfNoSpecifiedTests=false`.
  Expected: the test fails until the complete path is wired.
- [ ] **Step 3: Finish the complete path.** Fix only gaps exposed by the test.
  Update all three docs with portal mode, socket config, tool shape, warning behavior, and limits.
- [ ] **Step 4: Run bounded checks.** Run both module suites and capture logs.

```bash
portal_plan_log_dir="$(mktemp -d)"
mvn -B -pl chat-shell,chat-mcp -am test > "$portal_plan_log_dir/modules.log" 2>&1
rg 'Tests run:|BUILD SUCCESS|BUILD FAILURE|ERROR' "$portal_plan_log_dir/modules.log" | tail -45
git diff --check
drift refs docs/MCP-ADAPTER.md
drift refs docs/BUILD.md
drift refs docs/SHELL-COMMAND-CONTRACT.md
drift check
```

  Expected: `BUILD SUCCESS`, no test failures, and `drift check` passes.
  Review every drift binding before any `drift link`.
- [ ] **Step 5: Run process acceptance against a disposable core.**
  Use `docs/superpowers/proofs/2026-10-17-one-shot/isolation.sb.sh` for the sandbox.
  Use `docs/superpowers/proofs/2026-10-17-one-shot/core.sh` for the disposable core.
  Run `just build-shell-cli` before the portal launch.
  Write `acceptance.sh` with those named prerequisites.
  Make `acceptance.sh` start one portal JVM under the sandbox on ports 6990 and 6991.
  Make it call the portal twice. Check both replies, one unchanged PID, and no second shell JVM.
  Make it stop the disposable core with the Stage 1 cleanup path.

```bash
just build-shell-cli
portal_acceptance_log_dir="$(mktemp -d)"
bash docs/superpowers/proofs/2026-10-26-shell-portal-register/acceptance.sh "$portal_acceptance_log_dir"
```

  Expected: exit 0 and one portal PID across both calls.
  The native-image build is outside this portal proof. Record that limit in `RECORD.md`.
- [ ] **Step 6: Run live comparison after a compatibility check.**
  Compare checkout and installed wrapper contracts before calling node 9.
  Read as Sigma. Capture output in a private temporary file.
  Report status, warning kinds, record counts, process IDs, and connection counts.
  Keep passwords and register text out of reports.
  Run no install, restart, stop, or register write.
- [ ] **Step 7: Record exact evidence.** Write commands, exit codes, summary counts, and unverified claims in `RECORD.md`.
  Name the source commit and installed runtime marker separately.
- [ ] **Step 8: Commit.** Stage only the files listed in this task and any reviewed drift link files.
  Use `CHAT-hmbigvmf: Verify and document portal reads`.

## Self-review before execution

- [ ] Check every approved spec section against one task.
- [ ] Check every named file and command against the current tree.
- [ ] Check the planned mode is `portal` everywhere.
- [ ] Check the socket protocol never accepts a command line.
- [ ] Check timeout tests prove fail-stop and no later read.
- [ ] Check the installed runtime remains unchanged.
- [ ] Request plan review before implementation.
