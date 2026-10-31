# Foundation implementation review

Review issue: CHAT-qdsmackk.
Parent: CHAT-zffgckla.
Status: owner implementation review pending.
Branch: `impl/chat-zffgckla-foundation`.
Approved planning basis: `72233229`.
Plan corrections: `8d830cf0`.
Original implementation range: `8d830cf0..bdb8c1c8`.
Isolation correction issue: CHAT-jgtsuyfo.
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
The count depends on the environment. The owner measured one failure in fourteen checks in the separate review environment.
No secret behavior changed to hide them.
The original partial Maven result remains author-reported until independent CI supplies its result.
The partial build's JVM exit timeout remains visible in the evidence record.
No real register import, credential change, installed-home update, or remote deployment occurred.
The main checkout remains at `1a8fd033`.
The separate node-9 repository remains empty.

## Required gate

Review the foundation implementation before the next plan consumes it.
Each later plan retains its separate implementation review.
The final release pin must select a reviewed neutral release without temporary compatibility.
No release pin, PR merge, or extraction completion is claimed here.

## Isolation review correction

The owner rejected `54113f32` because the general Register suite inherited legacy selectors.
The correction changes test isolation and evidence only.
No runtime reader or alias-conflict behavior changes.

Each affected suite now sources `shell-scripts/test-support/clear-legacy-inputs.sh` before creating fixtures.
The helper clears all 21 reader aliases, the derived-root override, and the legacy test control.
It preserves neutral and unrelated inputs.
A suite stops if the helper cannot load.
Compatibility cases can inject deliberate legacy inputs after that boundary.
The new `test-suite-environment.sh` checks this behavior against the reader's complete alias table.
The revised 119-case Register result uses this cleared environment.

The helper is temporary test support. It is not an installed tool or a permanent lexical exception.
The extraction must preserve test isolation when deployment tests move.
Retained general suites must remove this temporary dependency when the final legacy reader leaves demo-chat.
Foundation implementation approval remains pending.
