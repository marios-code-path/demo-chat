# Agent JWK guide ownership after node-9 extraction

Issues: `CHAT-zffgckla`, `CHAT-anegybnr`
Date: 2026-10-29
Status: proposed

## Decision

Keep the general `private_key_jwt` credential contract in demo-chat.
Keep the node-9 tunnel procedure in node-9.
Publish the finished node-9 guide after the extraction tasks land.
PR #311 remains draft until that order is satisfied.

The owner approved this document split during the guide review.
The owner has not reviewed this written specification yet.

## Ownership

`docs/MCP-CREDENTIAL-ISSUANCE.md` owns the general procedure.
It states agent registration, assertion fields, token request fields, and safe credential replacement.
It names no node-9 host, port, path, network, or wrapper.
It distinguishes the current JWT procedure from the recorded secret-client procedure.
It preserves the secret-client path for existing clients.

`node-9/docs/AGENT-JWK-TUNNEL-GUIDE.md` owns node-9 values and commands.
It states the local token URL, offset selection, tunnel mode, and node-9 authserv wrapper.
It names the actual wrapper after `CHAT-vnljyxpl` lands.
It does not invent a command while that wrapper remains undefined.
The guide uses the generic assertion and token contract from demo-chat.
It keeps enough contract detail to work after the repositories separate.

The final documents must not require a relative link across repositories.
Each document must identify the other project by name when it needs that context.
Links within one repository may remain relative.

## Token file safety

The operator writes a new token to a restricted temporary file.
The operator validates that the response contains a nonempty string token.
The operator then replaces the adapter credential file with the temporary file.
A failed request or invalid response must leave the prior credential unchanged.
The procedure must not print or store the agent private key in the repository.

## Publication order

Finish the boundary and launcher work under `CHAT-zffgckla` first.
Move the node-9 operator steps with the node-9 files.
Run the extraction readiness check before the guide PR becomes ready.
Publish the node-9 guide last among the extraction changes.
Keep PR #311 in draft during this work.
Rebase or replace that PR when the final repository layout is known.

The guide publication does not close `CHAT-anegybnr` by itself.
The issue still requires live two-agent token issuance and negative-case proof.

## Verification

Check that no demo-chat instruction needs a node-9 path after extraction.
Check every local link from both final document locations.
Run the node-9 wrapper and tunnel offline checks in their owning repository.
Test a failed token response against an existing credential file.
Confirm that the file retains its prior token.
Run a live two-agent token check when node-9 authserv is available.
Record the live result separately from document and offline checks.
