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

`stderr` carries diagnostics. Each line starts with `chat-mcp: `. The adapter
writes one diagnostic line for each tool call. It writes no topic name, no
argument value, no token and no payload.

The logging library `kotlin-logging` prints one startup line to stdout. That
line is not a protocol frame. The adapter sets the library property
`kotlin-logging.logStartupMessage` to `false` before it loads any other class.
The property is set in code, so every launch path gets it.

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
evidence runs the same harness against a deployment. Task 8 of the plan holds
the full step list. The shape is:

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
