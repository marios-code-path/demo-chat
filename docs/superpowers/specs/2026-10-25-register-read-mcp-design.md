# Local MCP access to node-9 register reads

Date: 2026-10-25.
Tracking: CHAT-hmbigvmf.
Status: design approved in conversation. Written specification awaits review.

## Objective

Give a local Codex client one read-only MCP tool named `register.read`.
The tool reads one configured project register through the installed node-9 wrapper.
The tool must preserve the wrapper's records, warnings, and failure status.

The first delivery supports the session-start read and one topic read.
It does not expose register creation, import, open, post, membership, or raw shell commands.

## Current boundaries

`node-9/bin/register read --as <name>` reads the register index and recent entries.
`node-9/bin/register read <topic> --as <name>` reads one topic.
The wrapper starts `register-read --index <value>` for the session-start read.
The wrapper already owns login selection, secret-file access, and a 30-second limit.

The current `chat-mcp` adapter uses authenticated REST and a configured topic list.
It does not use the node-9 register wrapper.
This design adds a separate local stdio server and leaves that adapter unchanged.

The installed wrapper currently differs from the project-register code in this checkout.
Live acceptance of a named project register requires a compatible installed wrapper.
This design does not authorize a node-9 reinstall or a node stop.

## Architecture

The server is a separate, native executable built from a new Kotlin module.
It uses the existing Kotlin MCP SDK and GraalVM Native Image.
JVM tests may support development, but delivery requires a working native executable.
If the SDK cannot compile to a native executable, stop and review that result.

One server process holds one agent login and one project register.
The operator sets both when Codex starts the server.
The tool input cannot select a login, register, password, executable, or environment variable.
The server launches the configured installed `register` wrapper for each call.
It never reads password files directly.

The server uses the installed wrapper's absolute path.
It does not run a shell or resolve that path through `PATH`.
It passes command arguments as separate process arguments.
It does not allow caller text to become a command or an option.

## Configuration

The server requires these launch settings:

| Setting | Rule |
|---|---|
| `--register-bin PATH` | Absolute path to an executable installed `register` wrapper. |
| `--as NAME` | One nonempty agent login. The tool call cannot change it. |
| `NODE_9_REGISTER_TOPIC_NAME` or `NODE_9_REGISTER_TOPIC_ID` | Selects one project register. The name wins when both exist. |

The server rejects a missing register selector before it starts MCP service.
The server passes the selected register variables to the wrapper.
It does not infer a project from the current directory.
It does not place a password or register content in process arguments.

The operator controls the server command and its environment in Codex MCP configuration.
An operator may configure a separate server name for another project or agent.
Do not ship a development login or a production login as a default.

## MCP contract

The server exposes one tool, `register.read`.
The tool declares read-only and idempotent hints.
It exposes no MCP resource, prompt, subscription, or write tool.

The input is an object with one optional `topic` string.
An absent `topic` selects the session-start read.
A nonempty `topic` selects one topic read.
The server rejects an empty string, another property, or a non-string value.
The topic must start with an ASCII letter or digit.
It may then contain ASCII letters, digits, or hyphens, up to 64 characters total.
This rule accepts current topic names and UUID text without allowing an option prefix.

The server runs exactly one wrapper command for each tool call:

```text
register read --as <configured agent>
register read <topic> --as <configured agent>
```

A successful result has `records`, an array of the wrapper's JSON record objects.
The server preserves record order and field values.
It also emits the same object as serialized JSON in MCP text content.
An empty array is valid only after status 0 with no warning.

The server parses each nonempty stdout line as one JSON object.
A malformed line, non-record object, or warning prevents a successful result.
The server never converts a denied index or denied topic into an empty array.

For a failed read, the MCP result has `isError=true` and one stable error code.
The error also carries the wrapper exit status when the process exited.
The server does not return partial records or raw standard error on failure.
It writes bounded diagnostics to its own standard error without secret values.

| Error code | Condition |
|---|---|
| `INVALID_INPUT` | The tool input breaks the schema or topic rule. |
| `CONFIGURATION` | A launch setting is absent or invalid. |
| `PROCESS_FAILED` | The wrapper exits with a nonzero status. |
| `TIMEOUT` | The server's process bound expires. |
| `OUTPUT_TOO_LARGE` | A child output stream exceeds its byte limit. |
| `INVALID_OUTPUT` | The child emits malformed JSON or a non-record object. |
| `REGISTER_WARNING` | The child emits a warning, including an access denial. |

The server reserves standard output for MCP frames.
It closes after MCP standard input reaches end of file.

## Process limits

The wrapper keeps its 30-second deadline.
The server uses a 35-second process bound and ends a child that exceeds it.
The server captures at most 4 MiB from each child stream.
It returns `OUTPUT_TOO_LARGE` without partial records when a limit is exceeded.
The server ends the child process group and waits for cleanup before it answers.

## Installation and operation

The repository build adds one command to build the native MCP executable.
A separate user install command places that executable in `~/.local/bin`.
The node-9 installer does not write to a user's home.
Neither build nor user installation changes the installed node-9 runtime.

After installation, the operator configures a local Codex stdio MCP server.
The configuration selects the register and agent explicitly.
For example:

```bash
codex mcp add node-9-register \
  --env NODE_9_REGISTER_TOPIC_NAME=demo-chat-register \
  -- ~/.local/bin/node-9-register-mcp \
  --register-bin /opt/demo-chat/node-9/bin/register --as Sigma
codex mcp list
```

`Sigma` and `demo-chat-register` are development examples in this specification.
An operator supplies independent production values at runtime.
The command passes no password value.
Codex must restart before it discovers the new server.

## Verification

1. Test MCP discovery and one `register.read` call through a real stdio client.
2. Use a stub wrapper to check exact arguments and fixed launch identity.
3. Test missing configuration, invalid input, nonzero status, timeout, malformed JSON, and oversized output.
4. Test `denied-index` and `denied-topic` warnings as errors with no partial records.
5. Build and run the native executable before reporting native support.
6. Compare one live MCP read with the installed wrapper after checking runtime compatibility.

The live check must report the command status and every warning line.
It must not print passwords or change register content.
It does not prove a deployment update or a register import.

## Sources

- `node-9/bin/register` and `node-9/bin/lib.sh` define the wrapper and selector.
- `docs/superpowers/specs/2026-10-24-project-register-design.md` defines project registers.
- `docs/MCP-ADAPTER.md` defines the existing REST adapter boundary.
- [MCP tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools) defines tool names and results.
- [Codex MCP setup](https://learn.chatgpt.com/docs/extend/mcp) defines local stdio configuration.
