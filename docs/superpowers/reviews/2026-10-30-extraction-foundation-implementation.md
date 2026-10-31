# Foundation implementation review

Review issue: CHAT-qdsmackk.
Parent: CHAT-zffgckla.
Status: owner implementation review pending.
Branch: `impl/chat-zffgckla-foundation`.
Approved planning basis: `72233229`.
Plan corrections: `8d830cf0`.
Implementation range: `8d830cf0..bdb8c1c8`.
The image-proof and repository-extraction implementations have not started.

## Implemented behavior

Literal profiles supply identity, ports, and non-secret configuration without shell evaluation.
A temporary named reader preserves legacy inputs and rejects unequal aliases.
Staging and launchers use effective identity and connection inputs while preserving existing refusal statuses.
General Register commands now reside in demo-chat.
General tunnel and MCP mechanisms use their own tools or explicit neutral paths.
The portal helper no longer locates its TCP check or CLI through a deployment bin.
An explicit missing CLI causes refusal before source execution.
Pinned-source validation checks the exact commit, inventory, source cleanliness, and tracked cache rule.
The inventory remains transitional because consumers still need the temporary reader.

## Change units

| Task | Implementation issue | Commit |
| --- | --- | --- |
| A1, literal profiles. | CHAT-mvopalza | `0090a0f2` |
| A2, temporary compatibility. | CHAT-motbqvpr | `83a102a3` |
| A3, stage and launcher inputs. | CHAT-rxfvixhr | `7f143630` |
| A4, general Register ownership. | CHAT-kbuahkun | `82f57b54` |
| A5, general tunnels and MCP paths. | CHAT-dnteampq | `a203beeb` |
| A6, interface and source validation. | CHAT-hfqaauyr | `f35dc407` |
| A5, explicit CLI regression. | CHAT-dnteampq | `bdb8c1c8` |

The [evidence record](../proofs/2026-10-30-extraction-foundation/RECORD.md) gives each task's failing assertion and passing results.
The [intermediate scan](../proofs/2026-10-30-extraction-foundation/INTERMEDIATE-SCAN.md) records remaining lexical matches at an exact commit.
These matches have no permanent exception.
CHAT-gdddeqkb remains pending.

## Verification limits

The required partial build uses selected modules and their reactor dependencies.
It does not establish full PR CI or live deployment acceptance.
Maven and Java stubs prove shell argument and staging behavior only.
The existing secret suite retains its seven measured baseline failures in this sandbox.
No secret behavior changed to hide them.
The partial build's JVM exit timeout remains visible in the evidence record.
No real register import, credential change, installed-home update, or remote deployment occurred.
The main checkout remains at `1a8fd033`.
The separate node-9 repository remains empty.

## Required gate

Review the foundation implementation before the next plan consumes it.
Each later plan retains its separate implementation review.
The final release pin must select a reviewed neutral release without temporary compatibility.
No release pin, PR merge, or extraction completion is claimed here.
