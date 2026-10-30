# A changed agent JWKS URI reaches a running deployment

Issue: `CHAT-vidnpywh`
Date: 2026-10-30
Status: awaiting approval

## Context

`CHAT-vidnpywh` holds this acceptance item:

> A JWKS URI change reaches a running deployment through a relaunch alone.

The launch channel now works, and `CHAT-vidnpywh` commit `3ecfd354` delivers it.
The reconciliation blocks the acceptance item.

`AgentClients.reconcile` reads one stored client per listed agent.
`AgentClients.differences` names `JWK Set URI` when the stored URI differs from the mapping.
Any named difference throws, and the start fails with this message:

    The stored client '<id>' is not an agent client. It differs in JWK Set URI.
    Delete the row, and the next start registers it again.

`ClientLoader` runs the reconciliation under the `client-init` profile.
`just deploy-authserv` activates `client-init` with `jdbc`, so the rows persist.
A relaunch therefore fails until the operator deletes the stored row.

`AgentClientsTests` records that behavior in three cases, added in commit `6f321b43`.

The refusal has a reason. Commit `CHAT-uizwrxmf` added `requireRowFree` for it.
A save against another client's row takes that client's secret, scopes and settings.
The blanket refusal keeps one row for one client.

The current repair costs the agent its access twice.
The delete removes the old key, and the next start restores nothing until it finishes.
The guide states that cost today.

## Decision

Update the stored client when the mapping differs in the JWK Set URI alone.

The reconciliation reads the stored row as it does today.
It computes the differences as it does today.
When the only named difference is `JWK Set URI`, it saves the client that the mapping describes.
It then prints one line that names the old URI and the new URI.

Every other case keeps its present behavior.
Two or more named differences still fail the start.
One named difference other than `JWK Set URI` still fails the start.
A stored secret client against a mapping with a JWKS URI names `authentication method`, so it still fails.
A stored private key client against a mapping without a JWKS URI names `authentication method`, so it still fails.

The update keeps the client id, the row id, the scope, the grant, the authentication
method and the token settings. It changes one client setting.

## Why this stays safe

The row already holds a `private_key_jwt` agent client for the same client id.
It already holds the same scope and the same grant.
So the update widens no privilege and takes no other client's row.

`requireRowFree` guards the case that `CHAT-uizwrxmf` names.
That case is a row id that belongs to another client id.
This decision does not touch that guard, and the update path keeps calling it.

A changed JWKS URI does change which key authenticates as the agent.
An operator who writes the launch mapping already controls the deployment, so this
grants no new power. A typo in the mapping would still re-point one agent silently.
The printed line is the control for that risk. It names both URIs, so the start log
records every change.

## Failure behavior

A start that updates a URI prints one line and continues.
A start that meets any other drift fails with the present message and the present exit path.
The message keeps naming every differing field, so a reader sees the whole drift.

## Verification

Add a failing check before each code change.

1. A case proves `reconcile` saves the mapping client when only the URI differs.
2. A case proves the printed line names the old URI and the new URI.
3. A case proves `reconcile` still throws when the URI and the scope both differ.
4. A case proves a stored secret client against a JWKS mapping still throws.
5. A case proves a stored private key client against a secret mapping still throws.
6. A case proves `differences` still names `JWK Set URI` on its own.
7. Replace the characterization case that asserts the start failure for a URI change.
   That case records the behavior that this design changes, so it must state the new behavior.
8. Keep the case that proves a removed agent keeps its stored client.

Run the focused authserv unit build.
Run `shell-scripts/test-flags.sh`.
Run `shell-scripts/test-authserv-profile.sh`.
Run `drift check`.

Update both operator documents.
`docs/MCP-CREDENTIAL-ISSUANCE.md` states that a recreate now applies a URI change.
It keeps the row delete for the other drift cases.
`shell-scripts/README-chat-build.md` carries the same change in short form.

## Scope boundary

This design covers the JWK Set URI alone.

It does not withdraw an agent that leaves the mapping.
The reconciliation reads the listed agents alone, and that limit stays.
A removal still needs the stored row deleted. The guide states that.

It does not change `requireRowFree`, the replay store, the token lifetime, or the scope rule.
It does not change core or rest.
It starts no container and registers no live agent.
`CHAT-vnljyxpl` keeps the live two-agent check.
