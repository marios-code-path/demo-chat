# Foundation implementation evidence

Basis: planning commit `72233229`, from master `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
Branch: `impl/chat-zffgckla-foundation`.
The owner approved implementation of all three plans.
Each plan still requires implementation review.
No live installation, register import, or remote deployment is part of these checks.

## A1: Literal profiles

Command: `bash shell-scripts/test-node-profile.sh`.
Before implementation: 26 checks, 25 failures.
The missing command could not supply records, validate schema, or export values.
After the initial implementation: 26 checks, no failures.
Additional regression cases exposed stripped trailing newlines and a missing neutral profile version.
The expanded suite passes 28 checks under Bash 5 and `/bin/bash` 3.2.
Implementation commit: `0090a0f2`.
The regression fixes accompany A3 in `7f143630`.

## A2: Temporary compatibility

Command: `bash shell-scripts/test-legacy-node-inputs.sh`.
Before implementation: 112 checks, 111 failures.
The absent reader could not map aliases or reject conflicting counterparts.
The initial reader passed all 112 checks.
A later regression exposed unequal values hidden by trailing-newline removal.
The expanded suite passes 113 checks under Bash 5 and `/bin/bash` 3.2.
Implementation commit: `83a102a3`.
The regression fix accompanies A3 in `7f143630`.

## A3: Staging and launchers

The initial `test-profile-launchers.sh` run failed 11 of 14 cases.
It exposed fixed ports, an ignored MCP runtime input, and ignored profile selection.
Both added stage-profile assertions also failed before staging changed.
The expanded launcher suite passes 18 checks.
Later failing regressions cover explicit shell runtime refusal, portable Java selection, and Unicode record separators.
`test-profile-exit-status.sh` passes seven cases, including all measured status 1 refusals.
`test-shell-cli-lib.sh` passes 47 cases, including both profile stages.
`test-flags.sh` retains all 69 golden cases.
The existing launcher deadline suite passes.
Bin-only and installed MCP launcher checks pass.
Implementation commit: `7f143630`.
These staging checks use Maven and Java stubs. They do not establish a real staged jar set.

## A4: Register ownership

The initial `test-register-source-independence.sh` run failed six of seven checks.
No general tool closure could provide split bytes, range bytes, configuration, or validation.
The expanded suite passes eleven checks in a fixture without a deployment subtree.
Failing regressions also exposed status conversion and application calls after failed connection preparation.
The new `test-register-host-install.sh` initially failed three of six cases.
It now passes all six cases, including an injected shared validator.

| Assertion group | General suite | Deployment suite during transition |
| --- | --- | --- |
| Config, typed reads, pointers, portal routing, receipts, deadlines. | `test-register.sh`, 119 passing cases. | Original `test-node-9-register.sh` also passes 119 cases until its later split. |
| Caller identity choice. | General command accepts a valid explicit identity. | Original wrapper retains its fixed identity refusal. |
| Bundle validation and request derivation. | `test-register-bundle.sh`, 22 passing cases. | No deployment installer is required. |
| Retry, receipts, index, recovery, collision, and create. | `test-register-bootstrap.sh`, 95 passing cases in a generic fixture home. | Deployment installation and environment assertions have their separate suite. |
| Byte-preserving frozen source split. | `test-register-import.sh`, 117 sections and 317268 bytes. | No deployment source path is required. |
| Installed library closure, derived root, fixed environment, validator delegation. | Source-independence cases are separate. | `test-register-host-install.sh`, six passing cases. |

The existing core deployment suite also passes.
The secret suite fails seven of fourteen checks in this sandbox.
The exact `1a8fd033` baseline fails the same seven checks under the same isolated Ansible cache setting.
Ansible reports that its local RPC server cannot start.
One existing assertion expects `--local` in a missing-Ansible message that does not contain that text.
No secret implementation or assertion changed to conceal these failures.

The metadata transport checks use existing offline stubs.
No CQL or SQL command ran against an application backend.
These checks do not establish resident-portal import support.
