# The Demo Chat MCP adapter

The adapter is a standalone program. It speaks the Model Context Protocol
(MCP) over stdin and stdout. It reads chat topics from a running Demo Chat
deployment.

The adapter holds no database credential and no direct store access. It calls
the deployment over HTTP. The deployment decides what the caller may read.

The adapter serves protocol revision `2025-11-25`.

## Build

```sh
mvn -o -B -pl chat-mcp -am package -DskipTests
```

The classes land in `chat-mcp/target/classes`. The adapter has no Spring
context.

## Configure

The adapter takes one argument, `--config <path>`. The environment variable
`CHAT_MCP_CONFIG` names the same file. The adapter refuses any other argument.

The file is a Java properties file. Every key is required unless the table says
otherwise.

| Key | Meaning |
|---|---|
| `backendBaseUrl` | The origin of the deployment. It carries a scheme, a host and an optional port. It carries no path, query, fragment or user info. |
| `credentialFile` | The file that holds the token. A relative path resolves against the directory of the configuration file. |
| `keyType` | `long` or `uuid`. It must agree with the deployment. |
| `topicIds` | The topic ids this adapter may read. Comma separated. One to 100 ids. |
| `enableSend` | Optional. `true` or `false`. The default is `false`. |
| `enableSearch` | Optional. `true` or `false`. The default is `false`. |

An unknown key fails the start. The adapter never accepts a credential as an
argument.

`backendBaseUrl` must use `https`. The adapter permits `http` on a loopback host
alone. That form exists for development.

Example:

```properties
backendBaseUrl=http://127.0.0.1:6791
credentialFile=credential.txt
keyType=long
topicIds=1554361326074068992
```

## Run

```sh
java -cp chat-mcp/target/classes:<dependencies> \
  com.demo.chat.mcp.McpAdapterMainKt --config /path/to/adapter.properties
```

The adapter reads protocol frames on stdin and writes them on stdout. It exits
when stdin reaches end of file.

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
| `<tool>` | The tool name, `chat_list_topics` or `chat_get_topic`. |
| `<verdict>` | `answered`, or `refused: <sentence>` for a failure. |
| `call=<n>` | The correlation id. It counts from one inside one process. |
| `duration=<ms>ms` | The wall time of the call, in milliseconds. |
| `code=<CODE>` | `OK`, or one of the six failure codes in the next section. |
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

## The two tools

| Tool | Input | Output |
|---|---|---|
| `chat_list_topics` | No argument | `{topics: [Topic]}` |
| `chat_get_topic` | `{topicId: string}` | `{topic: Topic}` |

A `Topic` carries `id`, `root` and `name`, each a JSON string. A Long id keeps
every digit, including a value above 2^53.

`chat_list_topics` reads each configured topic by its id endpoint. It does not
call the unbounded list endpoint. A topic that the deployment denies or does not
serve is left out of the answer. The answer carries no name and no count for it.

`chat_get_topic` refuses a topic id that is not in `topicIds`. The refusal
happens before any backend call.

Both tools declare themselves read only and idempotent.

## Failures

A tool failure is an MCP tool error result. It carries `isError` true and the
three application fields. A malformed MCP envelope is not a tool failure. The
SDK answers that one with a JSON-RPC protocol error.

### The three application fields

A failed tool result carries `_meta` with exactly three flat fields:

| Field | Meaning |
|---|---|
| `code` | One of the six codes below. |
| `message` | A sentence this adapter built. |
| `retryable` | `true` when a repeat of the same call may succeed. |

A good result carries no `_meta` at all. The SDK omits an absent field.

### The six codes

| Code | Meaning | Producer today |
|---|---|---|
| `AUTHENTICATION_REQUIRED` | The backend refused the credential, or the credential file is unusable. | Yes |
| `NOT_AVAILABLE` | The object is absent, denied, or outside the configured scope. | Yes |
| `FEATURE_UNAVAILABLE` | A required backend feature is unavailable. | **None** |
| `BACKEND_UNAVAILABLE` | A backend transport or service failure occurred. | Yes |
| `LIMIT_EXCEEDED` | A response or the configured work limit was exceeded. | Yes |
| `OUTCOME_UNKNOWN` | A send may have executed, and no reliable result arrived. | **None** |

`FEATURE_UNAVAILABLE` and `OUTCOME_UNKNOWN` have no producer in this phase.
They are declared so the vocabulary is complete. No path invents one.

### `retryable` is true for a transport failure alone

A transport failure is a connection that did not open, or a deadline that
passed. A repeat of a read may then succeed. Every other class is decided by
the request or by the stored data, so a repeat gives the same answer.

**A failed send must always answer `false`.** The adapter holds no durable
deduplication contract, so a repeat may send twice. No send tool exists yet.
That rule binds the tool that adds one.

### No raw backend text, and no hidden object

The message is a sentence this adapter built. It carries no backend exception
text, no topic name, no argument value and no payload.

**Every code has one fixed sentence, and the code alone selects it.**

| Code | Sentence |
|---|---|
| `AUTHENTICATION_REQUIRED` | `the backend refused the credential of this adapter` |
| `NOT_AVAILABLE` | `the backend does not serve this object, or it refuses this caller` |
| `FEATURE_UNAVAILABLE` | `the backend does not offer a feature this call requires` |
| `BACKEND_UNAVAILABLE` | `the backend did not answer the call` |
| `LIMIT_EXCEEDED` | `the call passed a limit of this adapter` |
| `OUTCOME_UNKNOWN` | `the adapter cannot tell whether the call completed` |

**The message of a `ClientException` never reaches a client.** A transport
builds that message from the material it handled, so it can hold a URL, a
header, a stored value or a credential fragment. The classifier reads the
reason of the failure and nothing else. The message serves a debugger and a
stack trace alone.

Two failures carry adapter prose instead, and each says so.

- A refusal of the adapter's own, such as a missing argument, names the value
  to correct. The adapter wrote that sentence.
- An unplanned failure answers `the adapter could not complete the call`. That
  failure held no backend answer, so no class name and no message exists to
  escape.

**A denied object and an absent object answer the same sentence.** A 403 and a
404 both become `NOT_AVAILABLE` with the sentence above. A reader that could
tell them apart would learn that a hidden object exists. No sentence names a
status, a count or a value from the request.

The backend status is not lost. It reaches the stderr diagnostic line, which
carries operator data alone.

### The adapter owns one transport

The adapter builds one transport for the whole process and closes it at
shutdown. The concurrency limit is per adapter process, so a transport for each
client would make that limit belong to one client.

The transport holds a watchdog executor and one thread. `serveStdio` closes the
transport on every path, so the thread is released whether the run ended well
or badly.

## Bounds

| Bound | Value |
|---|---|
| Connect timeout | 5 s |
| Call deadline | 30 s |
| Concurrent backend requests | 4 |
| Response body | 1 MiB |
| Redirects in one call | 4 |
| Shutdown after stdin close | 5 s |

## The client harness

`chat-mcp/src/test/client/` holds a Node harness. It is a real MCP client at a
pinned version. It connects to the adapter over stdio and calls both tools.

The harness is pinned to `@modelcontextprotocol/sdk` 1.31.0. That release
declares protocol revision `2025-11-25`, which is the revision this adapter
serves.

The harness needs Node on the PATH. It was measured with Node v26.7.0.

Install the pinned client once:

```sh
cd chat-mcp/src/test/client
npm ci
```

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

`McpAdapterHarnessTests` runs the harness against the JVM build. That test is
not the native acceptance test. See Task 6 of the plan.

**The backend of that test is `FakeTopicBackend`.** It is a local HTTP server
inside the test JVM. It is not a deployed Demo Chat server. So the test proves
discovery, tool calls over stdio, protocol framing, stream separation and JVM
shutdown. It proves no production REST authentication, no production route
behaviour and no deployed authorization.

`npm ci` is a manual step on a cold machine. The lock file is committed, so the
step is reproducible.

## Acceptance against a real deployment

The harness test runs against a fake backend. A reader who needs end-to-end
evidence runs the same harness against a deployment.
`docs/MCP-REAL-DEPLOYMENT-ACCEPTANCE.md` records one such run, on 2026-09-29.
Task 8 of the plan holds the full step list. The shape is:

1. Start a deployment. Create a topic through it. Record the id, the root and
   the name.
2. Name that id in `topicIds`, and name one id the deployment does not serve.
3. Run the harness with the deployment behind the adapter.
4. Read the transcript. Discovery must list both tools. The served topic must
   carry the id, the root and the name the deployment holds. The unserved id
   must refuse with no backend text. stdout must carry frames alone.

**One boundary holds for that run.** No deployment enforces a credential today.
`WebFluxSecurity` permits every exchange and adds no authentication, and
`RSocketServerConfiguration` carries `TODO: lock down!`. So a real-deployment
run proves the adapter against real routes, a real key type and a real index.
**It does not prove production REST authentication**, because no deployment
asks for one yet. Do not claim it. `CHAT-pgpmsgvr` closes that boundary.

## What the adapter does not do

- It exposes no MCP prompt, resource, sampling or subscription.
- It exposes no tool that mints a key.
- It accepts no backend URL, credential, sender id, root or partition from a
  tool argument.
- It processes no encrypted payload.
- It reads no live subscription and no historical timeline.
