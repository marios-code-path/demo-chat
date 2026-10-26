# Read the node-9 register through a shell portal

Date: 2026-10-25. Revised: 2026-10-26.
Tracking: CHAT-hmbigvmf.
Status: approved on 2026-10-26. Implementation planning is in progress.

## Objective

Expose one `chat_read_register` tool in the existing `chat-mcp` adapter.
The tool reads the node-9 register through a resident, logged-in `chat-shell` process.
Repeated reads must not start another shell JVM.

The first delivery supports the session-start read and one named topic read.
It does not add register writes or general shell command execution to the portal.

## Current boundaries

`node-9/bin/register read --as <name>` reads one selected register index and its recent entries.
`node-9/bin/register read <topic> --as <name>` reads the complete history of one topic.
The wrapper selects the index with `NODE_9_REGISTER_TOPIC_NAME` or `NODE_9_REGISTER_TOPIC_ID`.
The name wins when both values exist.
The wrapper starts a new shell JVM for every read.
The installed `chat-shell-cli` options select one-shot mode.
`ShellModeConfiguration` declares the one-shot runner alone.
The stage 1 plan's conditional task 9 defines a general command socket.
That socket accepts shell command lines in `chat-shell/1` frames.
This portal accepts read-only JSON requests instead.
This delivery adds a separate `chat.shell.mode=portal` runner.
The portal launcher selects that mode explicitly.
See `docs/superpowers/plans/2026-10-18-topic-register-stage-1-shell-tool.md` for the older resident contract.

`chat-shell` already implements the session-start read in `PubSubCommands.registerRead`.
It implements a named topic read in `PubSubCommands.messages` with the `records` format.
Its RSocket requester factory caches requesters by service instance.
The cache does not promise exactly one physical connection.

`chat-mcp` already serves MCP through standard input and output.
Its existing tools use authenticated REST.
This design adds one local socket client to that adapter.

The installed node-9 runtime may differ from this checkout.
This design does not authorize a node-9 reinstall, restart, or stop.

## Ownership and identity

One portal process holds one authenticated shell identity.
The portal starts only after the shell proves login through its current RSocket path.
The operator supplies the identity and password file at runtime.
The password never enters a command argument, socket request, MCP result, or diagnostic.
The portal does not accept a login name or credential in a socket request.

An interactive shell can enter `portal --socket <absolute-path>` after login.
The command serves requests in the foreground until the operator stops it.
The shell cannot accept another login command while the portal command runs.

An unattended launch uses `chat.shell.mode=portal` with the same portal service.
It requires `CHAT_SHELL_USER`, `CHAT_SHELL_PASSWORD_FILE`, and `CHAT_SHELL_PORTAL_SOCKET`.
It refuses an anonymous or failed login before it binds the socket.
The portal process holds the shell context until shutdown.
Portal mode is new work in this delivery.
It adds one runner, one login, one socket bind, and one stop path.
It starts no launch watchdog, because each request has its own deadline.
A stop signal ends the process and runs the socket cleanup rule below.
Both launch paths bind the same fixed identity for the portal lifetime.
One-shot mode refuses the `portal` command, because its launch watchdog would end a long-lived process.
The portal launcher uses the installed shell classpath and runtime settings.
It selects portal mode instead of the frozen one-shot option.
It never fixes a login name or password in installed options.

The portal serves multiple configured project registers through that identity.
The portal does not infer a register from its current directory.
Each MCP adapter configuration fixes one register selector.
The MCP caller cannot select another session-start index or identity.
The portal applies the shell identity to every backend read.
The core remains the authority for topic access.

## Socket boundary

The portal binds one Unix-domain socket at an absolute operator-selected path.
The parent directory must be private to the operating-system account.
The portal restricts socket access to that account.
It refuses a path that names a file, link, or socket from another process.
It binds the socket after it checks the path and parent directory.
It then records the bound path's owner and file identity with `lstat`.
The owner must equal the portal process account.
An owner mismatch or unavailable file identity ends startup and closes the listener.
The portal leaves the path untouched when ownership is unproven.
At shutdown, it removes the path only if its owner and file identity still match the recorded values.

Socket permissions separate operating-system accounts.
They do not separate processes that share one account.
An account that can connect to the socket can request reads as the portal identity.
The operator must treat the socket path as an identity-bearing capability.

The portal accepts one request at a time.
A second concurrent request receives `BUSY` without a backend read.
The request has one UTF-8 JSON object and a size limit of 4 KiB.
The response has one UTF-8 JSON object and a size limit of 4 MiB.
Each object occupies one newline-terminated frame.
The client and server close the connection after one response.
No socket request contains a shell command line.

The request fields are `operation`, `index`, and optional `topic`.
`operation` must equal `read-register`.
`index` names the register by its configured name or ID.
An absent `topic` selects the session-start read.
A present `topic` selects the complete history of that topic.
As with the wrapper, a named topic need not appear in the selected index.
The core checks whether the portal identity can read that topic.
The portal rejects unknown fields and empty strings.
An index or topic must hold 1 to 64 ASCII letters, digits, or hyphens.
The first character must be a letter or digit.
This rule accepts current topic names and UUID text.

The portal answers with `status`, `lines`, and `complete` on a completed read.
`status` is `0` for a completed shell read, including reads with warnings.
`lines` holds ordered JSON objects with the existing `record` or `warning` shapes.
A session-start line already holds `type`.
A topic read line holds five fields and no `type`.
The portal adds `type` `record` to a topic read line, as the wrapper does.
`complete` is false for every warning kind except `duplicate-pointer`.
This rule covers `denied-index`, `denied-topic`, and any kind that a later shell adds.
`complete` remains true for a `duplicate-pointer` warning.
A status `0` and no lines means a readable, empty result.

A failed read answers `error.code` and `status`.
The status is the shell status for a shell failure.
The status is null for a portal or socket failure.
It returns no partial records.
It returns no raw exception, credential, or backend response text.
The portal logs one bounded diagnostic without secret values.
MCP standard output continues to carry protocol frames alone.

| Code | Condition |
|---|---|
| `INVALID_INPUT` | The request breaks the socket schema or topic rule. |
| `BUSY` | Another read is active. |
| `UNAVAILABLE` | The backend or portal cannot answer. |
| `TIMEOUT` | The request deadline expires. |
| `OUTPUT_TOO_LARGE` | The response exceeds its byte limit. |
| `INVALID_OUTPUT` | The shell read emits an invalid typed line. |
| `READ_FAILED` | Another shell read failure occurs. |

The portal maps shell status 69 to `UNAVAILABLE` and 124 to `TIMEOUT`.
It maps other shell failures to `READ_FAILED` without changing their status.
An invalid socket request returns `INVALID_INPUT` and a null status.
An invalid portal login stops startup with shell status 77 before socket binding.
The adapter maps socket errors to its own fixed MCP error sentences.
The adapter never reports a missing portal as an empty register.

## Read implementation and limits

The portal calls the existing shell read services.
It uses `registerRead(index, 24 hours, remaining time)` for the session-start read.
It uses `messages(topic, format = "records")` for one topic read.
Both calls run under one request deadline of 30 seconds.
The deadline starts when the portal accepts the request.
It covers the backend read and the complete socket response.
This budget differs from the wrapper budget.
The wrapper counts from process launch, and that count includes the JVM start.
The portal counts from request accept, so the backend read receives the full budget.

The current one-shot `LaunchDeadline` can halt its JVM.
The current `registerRead` timeout can leave its worker running.
The portal must not reuse the process-wide one-shot watchdog.
If a read times out, the portal returns `TIMEOUT` when it can.
It then closes the socket and ends its own process.
This fail-stop rule prevents a timed-out worker from serving a later request.
A supervisor may start a new portal process.
The portal does not stop node 9.

The portal serializes reads under one fixed login.
The design does not claim one physical RSocket connection.
Acceptance measures JVM starts and requester reuse.
It reports the measured connection count separately.

## MCP contract

The existing `chat-mcp` adapter registers `chat_read_register` only when its portal settings are complete.
The settings are `portalSocket`, `registerName`, and `registerId` in its properties file.
`portalSocket` must be an absolute path.
At least one register selector must be present when `portalSocket` is present.
The name wins if both values exist.
The adapter refuses a partial portal configuration at startup.
Existing REST configuration remains required for the existing tools.

The MCP tool accepts an object with one optional `topic` string.
It rejects unknown properties, empty values, and non-string values.
It declares read-only and idempotent hints.
It does not accept an index, identity, socket path, or command string from a caller.

The tool forwards all ordered record and warning objects in structured content and JSON text content.
It includes the portal `complete` value and original `status`.
It sets `isError=true` when `complete=false`.
It still returns the visible records and warnings on that incomplete read.
It also sets `isError=true` on any failed read and returns no partial records.
A failed read result holds `error.code`, `status`, and `complete=false`.
`error.code` is the portal code.
`status` is the portal status when present.
A missing portal gives `UNAVAILABLE` and a null status.
This MCP error flag does not change the portal's status `0` for an access warning.

The adapter connects to the portal on each tool call.
It starts no shell, wrapper, or other JVM process.
MCP clients can issue concurrent calls.
The adapter does not retry.
A second call gets `BUSY` until the first read ends.
Other MCP tools retain their current REST behavior in this delivery.

## Verification and operation

1. Test portal startup with valid, missing, and refused login credentials in both launch paths.
2. Test socket ownership, safe cleanup, an occupied path, malformed requests, and a concurrent request.
3. Compare both portal read forms with the wrapper's ordered typed output.
4. Test duplicate-pointer, denied-index, and denied-topic warnings independently.
5. Test empty success, failed read, output limit, deadline, and fail-stop recovery.
6. Test MCP discovery and `chat_read_register` through a real stdio client and portal socket.
7. Measure that two reads use one shell JVM process.
8. Compare one live portal read with the installed wrapper after a compatibility check.
9. Test portal-mode startup, login refusal before bind, stop signal, and socket removal.
10. Test that a failed MCP read holds `error.code`, `status`, and `complete=false`.

The live check reports the wrapper status and every warning line.
It does not print passwords or change register content.
It does not claim a node-9 deployment update or a register import.

The implementation adds portal mode, a `chat-shell-portal` launcher, and adapter configuration guidance.
It updates `docs/MCP-ADAPTER.md` for config, tools, and failures.
It updates `docs/BUILD.md` for the portal-mode statement.
Installation and activation require a separate operator action.

## Sources

- `node-9/bin/register` and `node-9/bin/lib.sh` define the wrapper read and register selector.
- `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt` defines both read forms.
- `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/LaunchDeadline.kt` defines the one-shot deadline.
- `chat-shell/src/main/kotlin/com/demo/chat/shell/cli/ShellModeConfiguration.kt` declares the one-shot runner alone.
- `docs/superpowers/plans/2026-10-18-topic-register-stage-1-shell-tool.md` defines the older general command socket in task 9.
- `chat-client-rsocket/src/main/kotlin/com/demo/chat/client/rsocket/RSocketRequesterFactory.kt` defines requester reuse.
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/McpAdapterServer.kt` defines the current MCP server.
- `docs/MCP-ADAPTER.md` defines the current adapter config, tools, and failures.
- `docs/superpowers/specs/2026-10-24-project-register-design.md` defines project registers.
