# Extraction plan review index

The owner approved the two contracts for planning.
The required inventory correction is applied in `../specs/2026-10-30-node-profile-contract.md`.
`STUB_REQUIRE_NODE9_ENV` is a test control. Its neutral target is `STUB_REQUIRE_PROFILE_ENV`.
There is no whole `NODE9_ENV` variable in the inspected tracked source.

All three implementation plans remain proposed and require separate owner approval.
No plan permits implementation merely because its prerequisite design received approval.
Do not use subagents.

| Order | Plan | Review issue | Required implementation result |
| --- | --- | --- | --- |
| 1 | [Foundation](2026-10-30-extraction-foundation.md) | CHAT-qdsmackk | Profiles, temporary compatibility, general mechanisms, and source validation. |
| 2 | [Image proof](2026-10-30-image-composition-proof.md) | CHAT-gyfhbsbd | Composition digest bound to the image plus retained source-stamp verification. |
| 3 | [Repository extraction](2026-10-30-repository-extraction.md) | CHAT-utubzjvi | Final adapters, pinned installation, deployment transfer, and general source independence. |

Each plan starts with failing behavioral tests and names its files, commands, expected failure, and implementation seam.
Existing FP implementation issues retain their responsibilities.
The new review issues track plan approval only.
Their completion does not establish that the implementation passed review.

| Existing issue | Planned responsibility |
| --- | --- |
| CHAT-zjezvtwh | Approved profile design and corrected legacy inventory. |
| CHAT-ekduxjqn | Approved consumption design, foundation source checks, and extraction installation boundary. |
| CHAT-rxfvixhr | Foundation staging and profile port handling. |
| CHAT-dnteampq | General tunnel mechanisms and final deployment wrappers. |
| CHAT-mylxclsx | One deployment composition definition. |
| CHAT-scbvcozr | Generic composition proof and actual image acceptance. |
| CHAT-kbuahkun | General register separation, deployment test separation, and destination transfer. |
| CHAT-gdddeqkb | Complete lexical report, active-reference guard, and source independence. |
| CHAT-vnljyxpl | Launch wrappers, draft PR #311, live two-agent acceptance, and final guide publication. |
| CHAT-vepxpmlk | Agent withdrawal and remaining app-function gaps. |

## Review evidence

The planning branch starts at `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
The old Codex checkout was clean before switching to that basis.
Master remains unchanged.
The destination repository remains empty.
Only contract and plan documents changed during this planning task.

Inspection covered current issue comments, the deployment library and installer, affected wrappers, mixed tests, CI, and active documentation.
The semantic shell lookup returned no usages despite known reads.
That result is incomplete and supplies no proof of absent dependencies.
Whole-file inspection and the tracked literal inventory supply the planning evidence instead.

The inventory distinguishes 21 whole legacy names in 149 occurrences from 22 substring tokens in 152 occurrences.
The extra substring occurs inside the three uses of the test control.
The old claim that the table covered that real control was false and is corrected.
The physical test inventory contains seven filename-matched deployment suites plus the image-builder suite.
Mixed tests and the setup wrapper add deployment cases that require separate ownership.

Planning verification checks document links, obsolete contract rules, the diff, and drift.
It does not run the proposed implementation tests or establish runtime acceptance.
No code, installation, deployment, credential, or process change occurred.

## Remaining gates

Request owner approval for each implementation plan.
After approval, execute each plan with its required evidence and implementation review.
Run full PR CI before merge readiness claims.
Confirm repository-owner approval and at least one GitHub approval before merging.
Keep the final guide publication under CHAT-vnljyxpl after extraction prerequisites and live acceptance.
Keep the parent issue open until its remaining acceptance conditions are established.
