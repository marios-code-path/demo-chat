# Register through the portal socket

Status: proposed on 2026-10-28. It waits for owner approval.
Related: CHAT-jmnjvupr, `docs/superpowers/specs/2026-10-24-project-register-design.md`,
`docs/MCP-ADAPTER.md`.

## Intent

The register has two front ends. The MCP tools read and write through the
portal socket. The `node-9/bin/register` command logs in to the core on every
call. One machine thus needs the same credential in two places, and a stale
copy breaks one front end alone. On 2026-10-28 the MCP read worked while
`register read` failed with `Invalid Credentials`. The installed copy of the
secrets held an older value.

The portal holds one valid login. The command should use it when one exists.
Then a person needs no password for a read, and no second copy of a secret
can go stale.

## Why now

The portal, the socket protocol, and the MCP adapter all work. The adapter's
config file has no producer. The owner wrote the current file by hand during
a repair. That file copies the socket path, so a portal restart under a new
path leaves it wrong.

## Goals

- One portal login serves both the MCP tools and the register command.
- No app value in a platform tool.
- A live socket path, read at call time, never copied.
- One producer for the app config.
- No silent default register.
- No write on a register that the caller did not resolve.

## Non-goals

- A change to the portal protocol or its frames.
- A change to the register data model or the topic shapes.
- An automatic portal start.
- A change to the one-shot login for a machine with no portal.
- A shared register across projects.

## Anti-goals

- A default register name.
- `setup-portal` reading, naming, or validating a register.
- A fallback that can repeat a write.
- A copy of the socket path inside the app config.
- A second producer of the same config file.

## Constraints

- The state directory stays `~/.local/state/<project>/<host>-portal-<agent>`.
- The socket stays `/tmp/<project>-portal-<UID>-<agent>/p`.
- The app config name stays `mcp-portal-<agent>-<project>.properties`, in
  lower case.
- The adapter reads one config file. It gains one key.
- The register name rule stays `^[a-z0-9][a-z0-9-]{0,63}$` and 37 characters.
- `register` keeps its 30 second limit and its JSON line output.

## Owner decisions

These came from the owner on 2026-10-28.

1. The command uses the portal when one is up.
2. `--as` is omitted when a portal is up. A supplied `--as` gives a warning.
3. The command takes an agent name and a project name.
4. The platform writes a socket file. The state tree is the place to look.
5. `setup-portal` owns the platform files. It must not see app-specific
   values. The app config is an app file, so the app tool owns it. This
   corrects the earlier answer that `setup-portal` owns both files.
6. The app config points at the live socket file. Add a `portalSocketFile`
   key.
7. The app config has no default register. An unresolved register is an
   error, because a wrong path can receive a write.

## Builder calls

These are mine. Check them while you read.

1. **Route.** `register` starts the MCP adapter and sends one tool call. It
   does not open the socket itself. This reuses `shell-scripts/chat_libs.sh`
   and the tested `PortalClient`.
2. **Stable socket file.** The file lives at
   `~/.local/state/<project>/portal-<agent>/socket`. The host label is absent,
   because the app config is per agent and project, and the host can change.
   Both names are lower case in the path.
3. **Adapter launcher.** `register` starts the adapter from `CHAT_MCP_STDIO`
   when set, else the installed home's `bin/chat-mcp-stdio`, else the source
   tree's `shell-scripts/chat-mcp-stdio`. A source tree needs its build output.
   The command names the missing build step when the output is absent.
4. **Wrapper budget.** The adapter client deadline is 35 seconds. The
   `register` limit is 30 seconds. The command passes its remaining budget to
   the adapter wait, so the wrapper stops first and prints its own line. It
   does not let the adapter outlive it.
5. **Both socket keys.** In `portal-only` mode exactly one of
   `portalSocket` and `portalSocketFile` holds a value. Both set is a startup
   error. Neither set is a startup error.
6. **Live path read.** The adapter reads the socket path again for each call,
   not once at startup. A portal restart under a new path keeps the MCP tools
   working.
7. **Agent and project defaults.** They come from `MY_AGENT_NAME` and
   `MY_PROJECT_NAME`. The project falls back to the base name of
   `MY_PROJECT_HOME`, as `chat_libs.sh` does. Both names become lower case in
   the config file name.
8. **Register mismatch.** On the portal path the config supplies the register.
   When `NODE_9_REGISTER_TOPIC_NAME` or `NODE_9_REGISTER_TOPIC_ID` is also set
   and differs, the command stops with an error. It does not pick one.
9. **Read fallback.** A read falls back to one-shot when the socket is absent
   or the connect fails. It prints one line:
   `register: portal unavailable, using the one-shot login`.
10. **Write fallback.** A write never falls back. An ambiguous transport
    failure reports the error. A repeated write could post twice.
11. **Warning text.** `register: user selection is ignored when connected
    through portal`.

## The platform file

`setup-portal AGENT PROJECT start` writes the file after the portal reports
ready. `stop` removes it. The file holds one line: the absolute socket path.

```
~/.local/state/<project>/portal-<agent>/socket
```

The file has mode 600. The directory has mode 700. The file holds no register,
no user password, and no core port.

`setup-portal` keeps its current use of the host label for the state
directory, the pids, the offset, and the logs. The socket file is the only
new file.

## The app config

`register config --agent <a> --project <p> [--register <name> | --register-id <id>]`
writes:

```
~/.config/demo-chat/mcp-portal-<agent>-<project>.properties
```

```
mode=portal-only
portalSocketFile=/Users/<user>/.local/state/<project>/portal-<agent>/socket
registerName=<name>
```

Rules:

- The register value comes from `--register`, else `--register-id`, else
  `NODE_9_REGISTER_TOPIC_NAME`, else `NODE_9_REGISTER_TOPIC_ID`. A missing
  value is an error. A name wins over an id.
- The command requires `--agent` and `--project`. It writes no default
  register and no default project.
  The `register` command itself may default agent and project from the
  environment (builder call 7). This command may not.
- The command refuses an invalid register name, agent name, or project name
  before it writes.
- The command replaces the file whole. It prints the path it wrote.
- The command needs no portal and no core.

A read-only config may hold `registerId` in place of `registerName`. The
writes then refuse, as they do today.

## The adapter key

`AdapterConfigLoader` accepts `portalSocketFile` beside `portalSocket` in
`portal-only` mode.

- It reads the first line and drops one trailing newline.
- An empty file, a missing file, or a relative path is a startup error.
- A value in both keys is a startup error.
- In `portal-only` mode, neither key set is a startup error.
- The startup check reads the file once. Each tool call reads it again, so a
  live portal restart needs no adapter restart.
- The existing config keys keep their meaning.

## The register command

Resolution order:

1. Agent: `--agent`, else `MY_AGENT_NAME`.
2. Project: `--project`, else `MY_PROJECT_NAME`, else the base name of
   `MY_PROJECT_HOME`.
3. Config: `~/.config/demo-chat/mcp-portal-<agent>-<project>.properties`.
4. Socket: the `portalSocketFile` line of the config.
5. Live check: the socket file exists and the adapter connects.

When steps 1 to 5 succeed, the command sends one adapter call:

| Command | MCP tool |
|---|---|
| `read` | `chat_read_register` |
| `read <topic>` | `chat_read_register` with `topic` |
| `open` | `chat_open_register_entry` |
| `post` | `chat_post_register_entry` |

The command prints the returned lines. It keeps its JSON line rule. Each line
already carries a `type`. A line without one gains `record`.

**Selection comes first.** When the app config is absent, the command starts
on the one-shot path. That is selection, not fallback. A read and a write may
use that path, as they do today. The one-shot path keeps today's rules: it
needs `--as`, the password file, and `NODE_9_REGISTER_TOPIC_NAME`.

**A fallback happens only after selection.** The command chose the portal
path, then the transport failed. A read may then use the one-shot path. A
write never does, because a repeated write could post twice.

## Failure table

| Condition | Read | Write |
|---|---|---|
| No app config (selection, not fallback) | One-shot | One-shot |
| No socket file | One-shot, one line | Error |
| Connect refused | One-shot, one line | Error |
| `UNAVAILABLE` after the request | One-shot, one line | Error |
| `BUSY` | Error | Error |
| `TIMEOUT` | Error | Error |
| `INVALID_INPUT` | Error | Error |
| `NOT_OPEN` | Not applicable | Error |
| `UNCONFIRMED` | Not applicable | Error |
| Register mismatch | Error | Error |

An error prints one line on standard error and returns a nonzero status.
The error names the reason. It never prints a password, a token, or a socket
path that the caller did not give.

Only the first row is selection. Every other row happens after the command
chose the portal path.

## Tests

- `shell-scripts/test-setup-portal.sh`: the socket file appears after
  start, holds the live path, and goes at stop. A failed start writes none.
- `shell-scripts/test-register-portal.sh`: portal path, no password, the
  `--as` warning, both fallbacks, and every row of the failure table.
- `shell-scripts/test-chat-libs.sh`: unchanged, but runs beside the new
  config name.
- `chat-mcp` config tests: `portalSocketFile` alone, both keys, missing file,
  empty file, relative path, and a second call after the file changes.
- An end-to-end proof in `docs/superpowers/proofs/`: a portal read with no
  password file present, then a write, then a stop and a one-shot read.

## Documents to update

- `node-9/README.md`: the socket file, the state layout, and the no-register
  rule for `setup-portal`.
- `docs/MCP-ADAPTER.md`: the `portalSocketFile` key and the config producer.
- `docs/SHELL-COMMAND-CONTRACT.md`: the portal path, the warning, and the
  failure table.
- `CHAT_MCP_AGENTS.md`: the shell command now has a safe portal path.
- `forward-register.md`: the issue row.

## Open item

The owner has not set the issue number. The plan will name it.
