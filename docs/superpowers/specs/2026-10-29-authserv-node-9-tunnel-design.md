# Authserv access through the node-9 tunnel

Issue: `CHAT-anegybnr`
Date: 2026-10-29
Status: awaiting review

## Context

The associated node-9 host serves authserv on its loopback port 9000.
The current `node-9-tunnel` forwards remote ports 6890, 6891, and 6892.
`node-9-remote` finds a free local offset for those three ports.
The authserv token endpoint is `/oauth2/token`.
Authserv fetches each agent's public JWKS from the registered HTTPS URI.
The JWKS must answer requests from authserv on the node-9 host.
A JWKS that answers only on the operator's laptop does not meet this requirement.
JWKS publication remains part of `CHAT-anegybnr`, outside this tunnel design.

An unconditional fourth forward would make existing commands depend on authserv readiness.
A separate SSH tunnel would require another connection to the same host.
The optional forward uses the current connection and preserves the current command behavior.

## Decision

Add an optional `--authserv` flag to `node-9-tunnel` and `node-9-remote`.
The flag goes before `HOST`, as the existing `--dry-run` flag does.
When selected, the tunnel forwards remote loopback port 9000 to local loopback port `9000 + OFFSET`.
The tunnel checks that port before it starts the local command.
With the flag, `node-9-remote` checks `9000 + OFFSET` while it finds a free offset.
Keep `offset_ports()` at three values for the existing `read -r` assignment.
Add a separate free-port check for authserv when the flag is set.
Without the flag, both commands keep their three-port behavior and byte-identical output.
Only `node-9-remote` sets `AUTHSERV_PORT` for a local command when the flag is set.
The variable contains the selected local authserv port.

The local token URL is `http://127.0.0.1:$((9000 + OFFSET))/oauth2/token`.
The agent sends its signed assertion to that URL during the tunnel session.
The assertion audience is the forwarded token URL that the agent requests.
`JdbcPrivateKeyAgentTests` already proves an assertion audience with the local token URL.
The test sends `http://localhost:$port/oauth2/token` and receives HTTP 200.
The tunnel acceptance check will verify the forwarded URL on the associated node-9 host.

The tunnel keeps each local bind on `127.0.0.1`.
It forwards only to `127.0.0.1` on the associated node-9 host.
It does not read, sign, print, or store an agent private key or assertion.
The authorization server keeps its token signing JWK separate from agent authentication keys.

## Failure behavior

An invalid flag position exits with status 2 before SSH starts.
The selected tunnel fails if any required local port does not answer within the readiness limit.
With `--authserv`, the fourth port joins the readiness probe and its failure line.
It prints the SSH error text when SSH fails.
The non-persistent mode returns the local command status and closes its tunnel.
The persistent mode runs no local command and prints only the live tunnel process ID.
`node-9-remote` reports the offset on standard error in persistent mode.
The operator guide derives the authserv port as `9000 + OFFSET` for that mode.

## Verification

Extend the offline tunnel checks for the optional fourth forward, readiness, offset selection, and dry-run output.
Keep a control check that confirms the existing three-port command.
Run one token request through the forwarded endpoint with a test assertion on a node-9 host that runs authserv.
This live request is outside the offline tunnel suite.
Check that the returned access token has the expected client ID and `chat.mcp` scope.
Use the existing authserv tests for assertion validation and replay behavior.

## Scope boundary

This design covers the tunnel adaptation that the user approved.
Two-agent acceptance, negative assertion cases, and credential guide changes remain in `CHAT-anegybnr`.
Those tasks need a separate reviewed plan before implementation.
