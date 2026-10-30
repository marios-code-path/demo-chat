# Agent tokens through the node-9 tunnel

Issue: `CHAT-anegybnr`

This guide describes token requests for agents that authenticate with `private_key_jwt`.
The tunnel forwards the token request to authserv on the node-9 host.
The agent signs its own assertion. The tunnel does not handle the private key.

## Before the request

1. Give each agent a distinct client ID and RSA key pair.
2. Keep each private JWK with its agent, outside the repository.
3. Publish each public JWK in an HTTPS JWKS document.
4. Make each JWKS URI reachable from authserv on the node-9 host.
5. Register each client ID with `--agent CLIENT_ID=HANDLE` and `--agent-jwks-uri CLIENT_ID=HTTPS_URI`.
6. Register the same client IDs and handles with core and REST.
7. Start authserv with a working JDBC replay store for a deployed server.

Repeat both authserv flags for each agent. The `--agent-jwks-uri` value selects
`private_key_jwt` for that agent. An agent without that flag uses a client secret.
Authserv uses its own JWK to sign access tokens. Do not use an agent key for that purpose.

Read [credential issuance](MCP-CREDENTIAL-ISSUANCE.md) for client registration and JDBC migration.
Read [the shell operator guide](SHELL-OPERATOR.md#authserv-on-the-associated-node-9-host) for tunnel modes.

## Create an assertion

Select the local port before you sign. The local port determines the assertion audience.
Use the agent's signer to create one compact JWT with these fields:

| Field | Value |
| --- | --- |
| JOSE `alg` | `RS256` |
| JOSE `kid` | The ID of this agent's registered public JWK |
| `iss`, `sub` | This agent's registered client ID |
| `aud` | The exact local token URL used for the request |
| `iat` | Current issue time |
| `exp` | A short expiry after issue time, such as 60 seconds |
| `jti` | A new unique value for this assertion |

Sign the JWT with this agent's private JWK. Create a new assertion for each token request.
Do not share one assertion between agents. Do not use an old assertion again.

The repository has no operator command that signs these assertions.
The agent's signer must supply the compact JWT before the request below.

## Request a token

The direct tunnel command uses offset 100. It forwards remote port 9000 to local port 9100.
Use this URL as both the assertion audience and the request URL:

```text
http://127.0.0.1:9100/oauth2/token
```

Set `CLIENT_ID` to the client ID used in the assertion.
Set `ASSERTION_FILE` to a file that holds only the signed compact JWT.
Set `RESPONSE_FILE` and `CREDENTIAL_FILE` to paths outside the repository.
Restrict access to all three files.

```sh
set -e
umask 077
node-9/bin/node-9-tunnel --authserv node2.lan 100 non-persistent \
  curl --fail-with-body -sS \
    -d grant_type=client_credentials \
    -d "client_id=$CLIENT_ID" \
    -d scope=chat.mcp \
    -d client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer \
    --data-urlencode "client_assertion@$ASSERTION_FILE" \
    http://127.0.0.1:9100/oauth2/token > "$RESPONSE_FILE"
jq -er '.access_token' "$RESPONSE_FILE" > "$CREDENTIAL_FILE"
chmod 600 "$CREDENTIAL_FILE"
```

The response file holds JSON with an `access_token` when authserv accepts the assertion.
The credential file holds only that token. Point the adapter's `credentialFile` at its path.
Do not paste the response into an issue, log, or repository file.
The adapter reads that file on each request. It needs no restart after a token replacement.

For an automatically selected offset, use `node-9-remote --authserv`.
It sets `AUTHSERV_PORT` for its local command. Build the audience URL from that port.
Sign the assertion after the command receives `AUTHSERV_PORT`.
Use the same URL for the token request.

## Check two agents

Use one persistent tunnel session for both agents. Record its reported offset.
The local token port is `9000 + OFFSET`.

```sh
node-9/bin/node-9-remote --authserv node2.lan persistent
```

Create a distinct assertion for each registered client ID.
Send both requests to the same forwarded token URL while the tunnel runs.
Check that each access token contains its matching `client_id` and `chat.mcp` scope.
Keep the two tokens in separate credential files.
Stop only the tunnel process that this command started.

Check refusal cases with separate requests. An unknown client, wrong key, bad audience,
expired assertion, or repeated assertion must fail. The existing JDBC test checks
that a repeated assertion returns HTTP 401 with `invalid_client`.

## Current evidence

The tunnel and remote offline suites passed during the `CHAT-anegybnr` implementation.
The issue record reports no live two-agent token check through the tunnel.
Do not treat this guide as evidence that the live acceptance check passed.
