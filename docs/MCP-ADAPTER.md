# The Demo Chat MCP adapter

The adapter is a standalone program. It speaks the Model Context Protocol
(MCP) over stdin and stdout. In HTTP mode, it reads topics and messages from a
running Demo Chat deployment. Optional sending uses caller-selected request
IDs and command status. Portal-only mode reads the node register through a
local portal socket.

The adapter holds no database credential and no direct store access. Its HTTP
tools call the deployment. The deployment checks route access.
For message-by-ID reads, the adapter also enforces the configured room limit.
The server checks `SUBSCRIBE` on the room of a message read by ID since `CHAT-erkoxybm`.

The adapter serves protocol revision `2025-11-25`.

## Build

```sh
just build-mcp-stdio
```

The build writes `chat-mcp/target/classes` and
`chat-mcp/target/mcp-runtime.classpath`. The adapter has no Spring context.

## Configure

The adapter takes one argument, `--config <path>`. The environment variable
`CHAT_MCP_CONFIG` names the same file. The adapter refuses any other argument.

The file is a Java properties file. The HTTP mode needs every key unless the
table says otherwise.

| Key | Meaning |
|---|---|
| `mode` | Optional. Set `portal-only` to serve the register tool alone. |
| `backendBaseUrl` | The origin of the deployment. It carries a scheme, a host and an optional port. It carries no path, query, fragment or user info. |
| `credentialFile` | The file that holds the token. A relative path resolves against the directory of the configuration file. See `docs/MCP-CREDENTIAL-ISSUANCE.md`. |
| `keyType` | `long` or `uuid`. It must agree with the deployment. |
| `topicIds` | The room IDs this adapter may read or send to. Comma separated. One to 100 ids. |
| `enableSend` | Optional. `true` or `false`. The default is `false`. |
| `enableSearch` | Optional. `true` or `false`. The default is `false`. |
| `portalSocket` | Optional. The absolute path of the portal socket. A relative path fails the start. |
| `registerName` | Optional. The register to read. It wins over `registerId`. |
| `registerId` | Optional. The register to read when no name is given. |

An unknown key fails the start. The adapter never accepts a credential as an
argument.

Portal-only mode accepts `mode`, `portalSocket`, and one register selector. It
rejects HTTP settings. It needs no backend token or topic ID. The read tool is
registered for either selector. The write tools need `registerName`.

The three portal keys form one setting. Set `portalSocket` and one selector, or
set no portal key. A partial setting fails the start. The socket path must be
absolute. Both selectors match `[A-Za-z0-9][A-Za-z0-9-]{0,63}`.

`backendBaseUrl` must use `https`. The adapter permits `http` on a loopback host
alone. That form exists for development.

Example:

```properties
backendBaseUrl=http://127.0.0.1:6791
credentialFile=credential.txt
keyType=long
topicIds=1554361326074068992
```

A portal read adds these keys:

```properties
portalSocket=/run/chat/portal.sock
registerName=register-index
```

A register-only configuration contains these values:

```properties
mode=portal-only
portalSocket=/run/chat/portal.sock
registerName=register-index
```

The full HTTP mode still requires its backend settings and a readable token
file at startup. A portal read does not send that token to the portal.

## Run

```sh
shell-scripts/chat-mcp-stdio --config /path/to/adapter.properties
```

An installed node-9 home provides `bin/chat-mcp-stdio`. A normal install stages
its runtime jars in `lib/mcp/`. An install with `--skip-cli` does not stage them.

An MCP client starts this command and keeps its stdin open. The adapter writes
protocol frames on stdout. It exits when stdin reaches end of file.

For Codex, add a server entry to `~/.codex/config.toml`:

```toml
[mcp_servers.demo-chat]
command = "/absolute/path/to/demo-chat/shell-scripts/chat-mcp-stdio"
args = ["--config", "/absolute/path/to/adapter.properties"]
```

Start a new Codex session after you add the entry.

## Agent configuration files

`shell-scripts/chat_libs.sh` gives each agent one configuration file.
The file name is `mcp-portal-<agent>-<project>.properties`.
Both names are lowercase.
`MY_AGENT_NAME` sets the agent name. The default is `theta`.
`MY_PROJECT_NAME` sets the project name.
The project name defaults to the base name of `MY_PROJECT_HOME`.
The directory is `${XDG_CONFIG_HOME:-$HOME/.config}/demo-chat`.
`CHAT_MCP_CONFIG` overrides the whole path.
Two projects that share one agent thus use two files.
A missing project file falls back to `mcp-portal-<agent>.properties`.
The fallback prints one stderr line.
Create the project file before you start the second project.

## What each stream carries

`stdout` carries protocol frames alone. Every line is one JSON-RPC 2.0 message.

`stderr` carries adapter diagnostics. Every adapter diagnostic line starts with
`chat-mcp: `. The adapter writes one diagnostic line for each tool call. It
writes no topic name, no argument value, no token and no payload.

One call line carries five fields, in this order:

```
chat-mcp: <tool> <verdict> call=<n> duration=<ms>ms code=<CODE> status=<s|->
```

| Field | Meaning |
|---|---|
| `<tool>` | The registered tool name. |
| `<verdict>` | `answered`, or `refused: <sentence>` for a failure. |
| `call=<n>` | The correlation id. It counts from one inside one process. |
| `duration=<ms>ms` | The wall time of the call, in milliseconds. |
| `code=<CODE>` | `OK`, or one of the nine failure codes in the next section. |
| `status=<s\|->` | The backend HTTP status. `-` means the call reached no backend answer. |

**The status reaches this line alone.** It never reaches the client. See the
next section.

**Two libraries write lines of their own, and the adapter suppresses both.**
`kotlin-logging` prints one startup line to stdout, which is not a protocol
frame. `slf4j-api` prints three provider warnings to stderr, which carry no
`chat-mcp: ` prefix.

The adapter sets two system properties before it loads any other class:
`kotlin-logging.logStartupMessage` to `false`, and `slf4j.internal.verbosity` to
`ERROR`. Both are set in code, so every launch path gets them. Measured on
2026-09-29: without the second property, stderr carries
`SLF4J(W): No SLF4J providers were found.` and two more lines.

The Task 5 harness purity test asserts that every stderr line carries the
prefix. So a library line that returns fails that test rather than reaching a
client in silence.

## Tools and results

HTTP mode has five read tools. A complete portal setting adds
`chat_read_register`. A register name also adds `chat_open_register_entry` and
`chat_post_register_entry`. Portal-only mode has `chat_read_register` alone
for a register id, and all three register tools for a register name.
`enableSend=true` adds the send tool.
Search remains unavailable, including when `enableSearch=true`.
Every ID in an MCP argument or result is a string.
Long IDs retain every digit, including values above `2^53`.

| Tool | Input | Successful result |
|---|---|---|
| `chat_list_topics` | `{}` | `{topics: [Topic]}` |
| `chat_get_topic` | `{topicId: string}` | `{topic: Topic}` |
| `chat_list_messages` | `{topicId: string}` | `{messages: [Message]}` |
| `chat_get_message` | `{messageId: string}` | `{message: Message}` |
| `chat_get_command_status` | `{commandId: string}` | `CommandStatus` |
| `chat_send_message` | `{topicId: string, text: string, requestId: string}` | `{receipt, outcome, backends}` |
| `chat_read_register` | `{topic?: string}` | `{status: integer|null, lines: object[], complete: boolean}` |
| `chat_open_register_entry` | `{key: string, short: string, title: string}` | one `open` line |
| `chat_post_register_entry` | `{key: string, text: string, requestId?: string}` | one `post` line |

A `Topic` contains string fields `id`, `root`, and `name`.
A `Message` contains `messageKey`, `senderId`, `topicId`, `text`, and `timestamp`.
A pointer item carries kind "pointer", targetId and targetRoot, and no text.
The adapter holds no root keys, so it does not name the target domain.
A history can hold text items and pointer items. See `CHAT-erkoxybm`.
A `messageKey` contains string fields `id` and `root`.
The timestamp uses ISO-8601 UTC form.
A receipt contains `commandId` and `messageKey`.
A status contains `commandId`, `requestId`, `receipt`, `backends`, and numeric `version`.
Backend entries contain `state`, numeric `attempts`, and nullable `nextRecoveryAt`.
The adapter omits backend reasons and the status owner.
The server restricts command status by owner, without an additional adapter room check.

### Reading the node register

The adapter registers `chat_read_register` only when the portal socket and a
register selector are configured. The selector is fixed at startup. A register
name wins when the configuration also has a register ID. The caller cannot
choose the register or the logged-in shell user.

The optional `topic` must match `[A-Za-z0-9][A-Za-z0-9-]{0,63}`. The adapter
sends it to the portal. The portal reads one fixed register over a local Unix
socket. Each call opens one socket connection. The client deadline is 35
seconds. The client does not retry a failure.

The portal deadline is 30 seconds from socket accept. It covers request
parsing, the backend read, and the reply write. A timeout attempts a `TIMEOUT`
reply, removes its owned socket, and halts the shell JVM with status 124. If
the client cannot receive the reply, it returns `UNAVAILABLE`. Later calls
fail until an operator or process supervisor restarts the portal.

A successful result has `status`, `lines`, and `complete` in structured content
and JSON text content. Each line is a typed record or warning. The adapter
keeps line order. A warning other than `duplicate-pointer` makes the read
incomplete. An incomplete read keeps its lines and sets MCP `isError` to true.

A failed read has `error.code`, `status`, `complete=false`, and no partial
lines. A transport failure uses `UNAVAILABLE` and a null status. The adapter
does not return a socket path, a credential, or an exception message.

### Writing the node register

The adapter registers `chat_open_register_entry` and
`chat_post_register_entry` only when the portal socket and a register name
are configured. A register id alone registers the read tool only, because
the name mints every entry topic.

`chat_open_register_entry` opens the entry of one key. Arguments:

- `key` is nonempty and at most 64 characters. It holds no `|`, no
  backslash, and no control character. It holds no space at its start or
  its end. The key `-` is reserved.
- `short` matches `[a-z0-9][a-z0-9-]*`. It names the new topic.
- `title` is nonempty and holds at most 256 characters. It holds no `|` or control character.

A successful open returns one line with `type` `open`, `state` `created` or
`exists`, `key`, `topic`, and `pointer_id`. A repeat of an open answers
`exists` with the same pointer. The tool declares `idempotentHint` true.

`chat_post_register_entry` posts one entry to the topic of the key pointer.
Arguments:

- `key` follows the open rule.
- `text` is nonblank and at most 3072 UTF-8 bytes.
- `requestId` is optional and holds at most 128 characters. It matches `[A-Za-z0-9._:-]+` and is never `_`.

When `requestId` is absent, the tool derives one from the key and text.
It uses `auto-` plus the first 32 hexadecimal characters of SHA-256.
The hash input is the UTF-8 key, one `0x00` byte, and the UTF-8 text.
The same key and text produce the same ID and no second entry on retry.
An explicit `requestId` overrides the derived ID. Use a new explicit ID to post identical text again.
A replay answers the receipt of the first post. Compare `message_id` and `command_id` across replies.
The post reply has no `state` field.

A successful post returns one line with `type` `post`, `key`, `topic`,
`message_id`, `command_id`, and `request_id`. The tool declares
`idempotentHint` true for repeated calls with the same arguments.

Both tools refuse an unknown argument before they open a socket.
An unknown argument sets `isError` to true and reports `no argument named '<name>'`.
An invalid value sets `isError` to true and names the broken rule.
The portal request frame holds at most 32768 bytes, including the newline.

Error codes:

| Code | Status | Meaning |
|------|--------|---------|
| `INVALID_INPUT` | 2 | The portal refused a field or a value. |
| `WRITE_FAILED` | 1 | The index read, the topic create, the join, or the pointer post failed. |
| `NOT_OPEN` | 1 | The key has no pointer. Run `chat_open_register_entry` first. |
| `UNCONFIRMED` | 75 | The confirmation read never showed the posted message. |
| `BUSY` | none | Another request holds the portal. |
| `UNAVAILABLE` | none | The transport failed. |

A refused or uncertain write answers `isError` true and no success line.
Retry an uncertain post with the same key and text.
If the first call used an explicit `requestId`, use it again.

Backend names are `PERSISTENCE`, `INDEX`, `VECTOR`, and `PUBSUB`.
Backend states are `PENDING`, `SUCCEEDED`, `FAILED`, and `UNCERTAIN`.
An `ACCEPTED` result can contain an empty `backends` object.

`chat_list_topics` reads each configured topic by its ID endpoint.
It omits topics that the deployment denies or does not serve.
`chat_get_topic` and room-based message tools reject unconfigured rooms before HTTP.
Message reads also check each returned room before emitting any result.
The server checks `SUBSCRIBE` on the room of a message read by ID, since `CHAT-erkoxybm`.
This adapter check stays as a second guard, and it limits a read to the configured rooms.
History uses stored messages, without pagination or a live subscription.
An oversized history fails completely, without partial output.

### Sending and retry identity

Example call:

```json
{"topicId":"1554361326074068992","text":"hello","requestId":"agent:turn:42"}
```

Example successful result:

```json
{"receipt":{"commandId":"c-42","messageKey":{"id":"1554361326074068993","root":"1554361143634427905"}},"outcome":"COMPLETED","backends":{"PERSISTENCE":{"state":"SUCCEEDED","attempts":1,"nextRecoveryAt":null},"INDEX":{"state":"SUCCEEDED","attempts":1,"nextRecoveryAt":null}}}
```

The adapter sends `POST /message/submit/{topicId}` with an `Idempotency-Key` header.
The server derives the sender from the authenticated token.
Request IDs and command IDs require 1 to 128 visible ASCII characters, without spaces.
Text must be nonblank and contain at most 16,384 UTF-8 bytes.
The adapter alone enforces that byte limit.
It preserves the text without trimming or truncation.

The request namespace belongs to the authenticated agent, across all its adapters and rooms.
A repeat with the same request ID and payload returns the same command and message.
Changed content or room under that identity produces `REQUEST_CONFLICT`.
Stage 1 retains this mapping only while the server process remains unchanged.
A server restart removes retry safety for earlier requests.

`COMPLETED` means the selected backend requirement succeeded.
The default requirement is `P,I`, which does not prove vector completion or recipient delivery.
`PENDING` returns a receipt while required work remains unresolved.
`ACCEPTED` requires no backend completion.
`INCOMPLETE` means a required backend recorded a definitive refusal.
Use `chat_get_command_status` to inspect later progress.

Configure `app.command.completion.timeout=5s` on the server.
That starting value stays below the adapter's `30s` deadline.
The adapter cannot inspect the remote setting.
A longer server wait or a transport failure can produce `OUTCOME_UNKNOWN` instead of `PENDING`.
The adapter never repeats a submission or follows its redirect.
Repeat an unknown submission only with the same request ID while the server process remains unchanged.
Legacy send routes provide no caller-owned retry safety.

Read tools declare `readOnlyHint=true` and `idempotentHint=true`.
Sending declares `readOnlyHint=false`, `destructiveHint=false`, and `idempotentHint=false`.
A repeat is safe only with the same request ID while the server process remains unchanged.
That condition is not unconditional, so the send hint is false.
These hints grant no permission.

## Failures

Application failures are MCP tool results with `isError=true`.
Malformed protocol envelopes receive SDK protocol errors.
Successful results contain the same JSON in text and `structuredContent`, without `_meta`.

Most failures contain exactly three `_meta` fields: `code`, `message`, and `retryable`.
`OUTCOME_UNKNOWN` adds the validated caller `requestId` as a fourth field.
It contains no receipt or `structuredContent`.
`COMMAND_INCOMPLETE` retains the validated send result in `structuredContent`, including its receipt.
Its `_meta` still contains exactly three fields.
There is no separate error `outcome` field.

| Code | Fixed message |
|---|---|
| `AUTHENTICATION_REQUIRED` | `the backend refused the credential of this adapter` |
| `NOT_AVAILABLE` | `the backend does not serve this object, or it refuses this caller` |
| `FEATURE_UNAVAILABLE` | `the backend does not offer a feature this call requires` |
| `BACKEND_UNAVAILABLE` | `the backend did not answer the call` |
| `LIMIT_EXCEEDED` | `the call passed a limit of this adapter` |
| `OUTCOME_UNKNOWN` | `the submission outcome is unknown. Repeat only with the same request ID while the server process remains unchanged.` |
| `INVALID_INPUT` | `the tool input is not valid` |
| `REQUEST_CONFLICT` | `the request ID conflicts with an earlier submission` |
| `COMMAND_INCOMPLETE` | `a required backend refused the command` |

`FEATURE_UNAVAILABLE` has no producer because search remains unavailable.
The new message tools use `INVALID_INPUT` for invalid arguments.
Existing topic tools preserve their earlier `NOT_AVAILABLE` behavior for invalid arguments.
Adapter input refusals and unexpected adapter failures can use their existing adapter-generated sentences.
No failure exposes backend text, credentials, hidden object identifiers, or content.
Denied and absent objects both produce `NOT_AVAILABLE`.
Backend HTTP status appears only in stderr diagnostics.

Only read transport failures can have `retryable=true`.
Every send error has `retryable=false`, including an unknown outcome.
The caller must decide whether the process-lifetime retry condition still holds.
The adapter owns one HTTP transport and closes its watchdog during shutdown.

## Bounds

| Bound | Value |
|---|---|
| Connect timeout | 5 s |
| Call deadline | 30 s |
| Concurrent backend requests | 4 |
| Response body | 1 MiB |
| Read redirects in one call | 4, within the configured origin |
| Submission redirects | Never followed |
| Shutdown after stdin close | 5 s |

## The client harness

`chat-mcp/src/test/client/` holds a Node harness. It is a real MCP client at a
pinned version. It connects to the adapter over stdio and calls the selected tools.

The harness is pinned to `@modelcontextprotocol/sdk` 1.32.0. That release
declares protocol revision `2025-11-25`, which is the revision this adapter
serves.

The harness needs Node on the PATH. It was measured with Node v26.7.0.

The pinned client installs itself. The `chat-mcp` build runs `npm ci` in
`chat-mcp/src/test/client/` when that directory holds no `node_modules`, and it
skips the step when one is present. So a cold checkout needs no manual step, and
an ordinary build makes no network call. **A missing npm fails the build.** See
`CHAT-vkqdeoct`.

To install the client alone, run `npm ci` in `chat-mcp/src/test/client/` by hand.

Run the harness:

```sh
node harness.mjs --read-id <allowed id> --refused-id <other id> -- \
  java -cp <classpath> com.demo.chat.mcp.McpAdapterMainKt --config <file>
```

Everything after `--` is the adapter command. The harness appends nothing to it.

The harness prints one JSON transcript on its own stdout. The transcript carries
the Node version, the client version, the negotiated revision, the discovered
tools, the answer to each call, every raw stdout line, every stderr line and the
exit.

The harness asserts nothing. The reader decides.
Messaging calls use a JSON array of tool names and argument objects:

```json
[{"name":"chat_send_message","arguments":{"topicId":"1554361326074068992","text":"hello","requestId":"agent:turn:42"}}]
```

```sh
node harness.mjs --calls-file calls.json -- \
  java -cp <classpath> com.demo.chat.mcp.McpAdapterMainKt --config <file>
```

`McpAdapterHarnessTests` runs the harness against the JVM build. That test is
not the native acceptance test. See Task 6 of the plan.

**The backend of that test is `FakeTopicBackend`.** It is a local HTTP server
inside the test JVM. It is not a deployed Demo Chat server. So the test proves
discovery, tool calls over stdio, protocol framing, stream separation and JVM
shutdown. It proves no production REST authentication, no production route
behaviour and no deployed authorization.

The build installs the pinned client itself, so the test runs on a cold
checkout with one command. The `install-pinned-mcp-client` profile activates
when `chat-mcp/src/test/client/node_modules` is absent. It runs `npm ci` in that
directory at `generate-test-resources`, and it uses the committed lock file. A
warm tree activates no profile and makes no network call.

## Acceptance against a real deployment

Adapter-unit harness tests use fake backends.
`McpMessagingDeploymentTests` uses authenticated REST, real handlers, and the real adapter.
A reader can also run the same harness against a deployment.
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` records one such run, on 2026-09-29.
Task 8 of the plan holds the full step list. The shape is:

1. Obtain a credential by the procedure in `docs/MCP-CREDENTIAL-ISSUANCE.md`.
   Write it to `credentialFile`.
2. Start a deployment with the four `app.security` values that the procedure
   names. Create a topic through it. Record the id, the root and the name.
3. Name that id in `topicIds`, and name one id the deployment does not serve.
4. Run the harness with the deployment behind the adapter.
5. Read the transcript. Discovery must list five read tools, or six tools when sending is enabled. The served topic must
   carry the id, the root and the name the deployment holds. The unserved id
   must refuse with no backend text. stdout must carry frames alone.
6. Repeat with a junk credential in the file. Every call must report `AUTHENTICATION_REQUIRED`, with status 401 in stderr.

**One boundary held for the 2026-09-29 run, and one no longer holds.**

The enforcement boundary is closed. `CHAT-pgpmsgvr` merged on 2026-09-30, and
the application chain now requires a valid agent token on every route it owns.
So a real-deployment run proves production REST authentication.
`RSocketServerConfiguration` stays outside that issue and keeps its
`TODO: lock down!` note.

The credential boundary is closed too.
`docs/MCP-CREDENTIAL-ISSUANCE.md` states the procedure that gives
`credentialFile` its value. It starts an authorization server, requests a token
with the `client_credentials` grant, and writes that token to the file. It also
records a control run, in which a junk credential made every call answer 401.

**The 2026-09-29 token came from a local mint, and the document says so.** That
run predates both fixes, so its token was unconstrained. Read
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` for what that run does and does not
prove.

A reader who needs a credential runs the issuance procedure. A reader who needs
end-to-end evidence runs the acceptance recipe with that credential.

## What the adapter does not do

- It exposes no MCP prompt, resource, sampling or subscription.
- It exposes no tool that mints a key.
- It accepts no backend URL, credential, sender id, root or partition from a
  tool argument.
- It processes no encrypted payload.
- It reads no live subscription and provides no history pagination.
