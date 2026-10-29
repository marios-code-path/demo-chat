# Chat-Shell one-off operations

Per Theta:

The command

CORE_PORT=6890 ROOTKEY_SOURCE_URI=http://127.0.0.1:6891 \
  node-9/bin/node-9-tunnel node2.lan 0 non-persistent \
  just launch-shell 1 --key-type uuid

Verified with --dry-run:

node-9-tunnel: ssh -N ... -L 127.0.0.1:6890:127.0.0.1:6890 -L 127.0.0.1:6891:127.0.0.1:6891 -L 127.0.0.1:6892:127.0.0.1:6892 -- node2.lan
node-9-tunnel: wait up to 10 seconds for ports 6890 6891 6892
node-9-tunnel: run just launch-shell 1 --key-type uuid
node-9-tunnel: stop the tunnel after the command exits

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
