# Demo Chat Operator Tips

This document helps to understand how and when to use commands.

## What is Node-9?

Node-9 is the canned 'data plane' used to prove and store
proof and evidence of progress. It is inter-project, or at least MOSTLY
inter-project - the agents seem to have some other idea about
that which I am trying to solve.

The fixed (for now) specification for node-9 deployment is as follows:

Persistence: Cassandra
Index: Lucene
PubSub: Redis
Vector: OpenAI
Discovery: Consul
Protocol: RSocket

## Installing Node-9

Installing node-9 builds then copies a demo-chat deployment over to /opt/demo-chat/node-9.

```bash
node-9/install
# use --bin-only to ONLY refresh bin
```

## Node-9 Tunnels

The command:

```bash
CORE_PORT=6890 ROOTKEY_SOURCE_URI=http://127.0.0.1:6891 \
node-9/bin/node-9-tunnel node2.lan 0 non-persistent \
just launch-shell 1 --key-type uuid
````

Verified with --dry-run:

```
node-9-tunnel: ssh -N ... -L 127.0.0.1:6890:127.0.0.1:6890 -L 127.0.0.1:6891:127.0.0.1:6891 -L 127.0.0.1:6892:127.0.0.1:6892 -- node2.lan
node-9-tunnel: wait up to 10 seconds for ports 6890 6891 6892
node-9-tunnel: run just launch-shell 1 --key-type uuid
node-9-tunnel: stop the tunnel after the command exits

```

Why each part

- CORE_PORT=6890: the shell's RSocket client defaults to 127.0.0.1:6790 (client-rsocket-local.yml). The tunnel gives you node2 host port 6890, which compose maps to container 6790.
- ROOTKEY_SOURCE_URI=http://127.0.0.1:6891: the shell reads root keys from the management actuator at 6791 by default. Through the tunnel that is 6891.
- --key-type uuid: every launch is a deployment and names a key type. There is no default.
- 0 offset: the tunnel needs a multiple of 100, and 0 keeps 6890/6891/6892. With offset 100 use CORE_PORT=6990 and ROOTKEY_SOURCE_URI=http://127.0.0.1:6991.
- non-persistent: the tunnel opens, the shell starts, and the tunnel closes when the shell exits. Standard input stays attached, so the prompt works.

Persistent variant

node-9/bin/node-9-tunnel node2.lan 0 persistent      # prints the tunnel pid
CORE_PORT=6890 ROOTKEY_SOURCE_URI=http://127.0.0.1:6891 just launch-shell 1 --key-type uuid
kill <tunnel-pid>

Build once, after a pull: just build-shell. The launch runs spring-boot:run, so it needs the installed jars. Add --build to the launch to do both.

Login: the interactive prompt signs in with the shell's login command. The environment form (CHAT_SHELL_USER with CHAT_SHELL_PASSWORD_FILE) applies to one-shot and portal modes, and a wrong password gives status 77.

## Authserv on the associated node-9 host

Use `--authserv` when authserv runs on the same node-9 host as the shell service.
The option forwards remote loopback port 9000 to local port `9000 + OFFSET`.
It keeps the local bind on `127.0.0.1`.
The option must precede `HOST`.
Without the option, the tunnel keeps its three-port behavior.

This direct call uses offset 100 and local authserv port 9100:

```sh
umask 077
node-9/bin/node-9-tunnel --authserv node2.lan 100 non-persistent \
  curl --fail-with-body -sS \
    -d grant_type=client_credentials \
    -d "client_id=$CLIENT_ID" \
    -d scope=chat.mcp \
    -d client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer \
    --data-urlencode "client_assertion@$ASSERTION_FILE" \
    http://127.0.0.1:9100/oauth2/token > "$RESPONSE_FILE"
```

Set `CLIENT_ID`, `ASSERTION_FILE`, and `RESPONSE_FILE` before this call.
Keep the assertion file and response file outside the repository with restricted access.
Sign the assertion after you select the port.
Set its audience to `http://127.0.0.1:9100/oauth2/token` for this example.
The direct tunnel prints the selected token URL on standard error.
It does not set `AUTHSERV_PORT`.

Use `node-9-remote` when the local offset must be selected automatically:

```sh
node-9/bin/node-9-remote --authserv node2.lan non-persistent \
  sh -c 'printf "Token endpoint: http://127.0.0.1:%s/oauth2/token\n" "$AUTHSERV_PORT"'
```

`node-9-remote` selects a free offset for all four ports.
It sets `AUTHSERV_PORT` for the local command.
An agent command must read that variable before it signs the assertion.
The command must use the same URL for the assertion audience and token request.

Persistent mode runs no local command:

```sh
node-9/bin/node-9-remote --authserv node2.lan persistent
```

The command prints the tunnel PID on standard output and the offset on standard error.
The local authserv port is `9000 + OFFSET`.
Sign the assertion after you know that port.
Stop only the tunnel process that this command started.

Authserv reads each agent's public JWKS from its registered HTTPS URI.
The URI must answer requests from authserv on the node-9 host.
A JWKS that answers only on the operator's laptop cannot serve this request.
Each agent keeps its private key outside the repository.
The authserv token signing JWK remains separate from those agent keys.

## Open an MCP-bound portal

To open a portal:

```bash

CHAT_SHELL_PASSWORD_FILE=MY_PASSWORD_FILE /opt/demo-chat/node-9/bin/setup-portal AGENT PROJECT START|STOP|STATUS

```
The domain socket is in place at ~/.local/state/demo-chat/portal-${AGENT}/socket

### Configuring the agent

Configure Codex / Claude for use by running from a project's root:

```bash
cd ~/my_project
shell-scripts/[claude|codex]-mcp-install.sh AGENT PROJECT REGISTER_TOPIC_NAME JAVA_BIN SCOPE
# Example
shell-scripts/claud-mcp-install.sh clawd my-project my-project-register $JAVA_HOME/bin/java local
```

This will store an agent/project specific MCP configuration in ~/.config/demo-chat.

### Register command-line wrapper - operation

Some nomenclature:

"Key" represents a thread in the register. It must be  1–64 chars, no |, no backslash, no control chars, no leading/trailing space.


| Arg        | What it is                                     | Rules                                                                    |
|------------|------------------------------------------------|--------------------------------------------------------------------------|
| key        | Thread identity. Lookup only.                  | Rule above.                                                              |
| short-name | Topic suffix. Becomes RegisterName-fr-shortname | Lowercase [a-z0-9-], starts alnum. Must be unique. Topic ≤64 chars total |
| title      | Human label, stored as field 4 of pointer. | Non-empty, no Pipe, no control chars. 256 max on portal.                 |

### Create a register

.. see other MD file

### Open a register

```bash
register open MY_IDENTIFIER snoo 'A Wodnerful Project Title'
```

Example return on the re-open of a same key → {"type":"open","state":"exists",...}. No write. Idempotent.

How to post

Body goes on stdin. There is no --text flag and no body argument.


```bash
/opt/demo-chat/node-9/bin/register open CHAT-cmkbobvi my-topic "My topic title" --as Theta
printf '%s\n' 'first entry body' | /opt/demo-chat/node-9/bin/register post CHAT-cmkbobvi --as Theta
```

### Register read

```bash
read -r -d '' BODY <<'EOF'
Stage 3 note.
Second line.
EOF
printf '%s\n' "$BODY" \
| /opt/demo-chat/node-9/bin/register post CHAT-cmkbobvi --as Theta
```

Post needs the pointer to exist. Without it: register returns 'no pointer for <key>. Run register open first.'

Optional retry guard: '--requestId ID'. The register cli wrapper makes one for you. On status 75 the error line gives the id. Repeat it verbatim to avoid a duplicate entry.

Complete commands

One-shot login path (reads node-9/secrets/Theta.password):

REG=/opt/demo-chat/node-9/bin/register

$REG open CHAT-cmkbobvi register-docs "Register docs" --as Theta

printf '%s\n' 'Entry body here.' | $REG post CHAT-cmkbobvi --as Theta

Portal path (agent holds socket, no password file). Pass both --agent and --project:

REG=/opt/demo-chat/node-9/bin/register

$REG open CHAT-cmkbobvi register-docs "Register docs" \
--agent theta --project demo-chat --as Theta

### Posting to register

```bash
printf '%s\n' 'Entry body here.' \
| $REG post CHAT-cmkbobvi --agent theta --project demo-chat --as Theta
```

Gotchas

- node-9/bin/register refuses to run from the source tree. Use /opt/demo-chat/node-9/bin/register, or set NODE9_ALLOW_SOURCE_TREE=1.
- One --agent without --project silently falls back to one-shot login. Pass both or neither.
- post text must contain a non-space character. Whitespace-only body → status 2.
- Output: one JSON object per line. Success is type=post with key, topic, message_id.


## References

... link to other markdown files and their nature ...
