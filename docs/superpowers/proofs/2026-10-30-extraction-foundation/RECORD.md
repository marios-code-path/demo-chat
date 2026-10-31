# Foundation implementation evidence

Basis: planning commit `72233229`, from master `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
Branch: `impl/chat-zffgckla-foundation`.
The owner approved implementation of all three plans.
Each plan still requires implementation review.
No live installation, register import, or remote deployment is part of these checks.

## Evidence status after re-review and merge

The sections below preserve the original foundation measurements and their environment limits.
The owner approved the corrected implementation at `b9708ae9` on 2026-10-31 in CHAT-qdsmackk.
That approval resolved the inherited-legacy-input blocker.

[Foundation partial CI](https://github.com/marios-code-path/demo-chat/actions/runs/41165417488) passed at `b9708ae9`.
Its 11 module summaries total 2,051 tests, zero failures, zero errors, and 32 skipped tests.
All 13 reactor projects succeeded.
The earlier issue comment reported 283 tests and one skipped test. Those counts describe `chat-shell` alone.
This result supplies independent build evidence for the earlier author-reported local run.
It does not remove the recorded local Surefire timeout or sandbox limits.

All 11 foundation commits are ancestors of merge `c060b9fe` from [PR #315](https://github.com/marios-code-path/demo-chat/pull/315).
[Master CI](https://github.com/marios-code-path/demo-chat/actions/runs/41179621130) passed at that merge commit.
Its build, integration, and agent-http jobs succeeded.
These later CI results are separate from the historical local checks below.
They establish no live deployment, final neutral release, or repository-extraction acceptance.
CHAT-qdsmackk still has status `in-progress` during this record correction.

The re-review also recorded a residual neutral-input isolation gap as nonblocking.
The shared helper clears legacy inputs. It does not clear neutral inputs.
The image-proof work later added neutral-input clearing to its affected suites.
No claim of complete suite isolation follows from the foundation approval.

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
| Config, typed reads, pointers, portal routing, receipts, deadlines. | `test-register.sh`, 119 passing cases with inherited legacy inputs cleared before fixtures. | Original `test-node-9-register.sh` also passes 119 cases until its later split. |
| Caller identity choice. | General command accepts a valid explicit identity. | Original wrapper retains its fixed identity refusal. |
| Bundle validation and request derivation. | `test-register-bundle.sh`, 22 passing cases. | No deployment installer is required. |
| Retry, receipts, index, recovery, collision, and create. | `test-register-bootstrap.sh`, 95 passing cases in a generic fixture home. | Deployment installation and environment assertions have their separate suite. |
| Byte-preserving frozen source split. | `test-register-import.sh`, 117 sections and 317268 bytes. | No deployment source path is required. |
| Installed library closure, derived root, fixed environment, validator delegation. | Source-independence cases are separate. | `test-register-host-install.sh`, six passing cases. |

The existing core deployment suite also passes.
The secret suite fails seven of fourteen checks in this sandbox.
The exact `1a8fd033` baseline fails the same seven checks under the same isolated Ansible cache setting.
This count depends on the environment.
The owner reported one failure in fourteen checks in the separate review environment.
The owner confirmed the same missing-Ansible assertion failure at master and `54113f32`.
Ansible reports that its local RPC server cannot start.
One existing assertion expects `--local` in a missing-Ansible message that does not contain that text.
No secret implementation or assertion changed to conceal these failures.

The metadata transport checks use existing offline stubs.
No CQL or SQL command ran against an application backend.
These checks do not establish resident-portal import support.

## A5: General tunnels and MCP paths

The initial profile tunnel suite failed seven of eight checks.
The initial MCP path suite failed all nine checks.
The required absent-bin and sentinel-bin cases now invoke the general TCP check and CLI.
No deployment sentinel executes.
An added overflow regression returns 2 before a TCP probe or CLI starts.
The profile tunnel suite now passes ten checks.
The MCP path suite passes nine checks.
The general tunnel suite passes 27 checks.
The general remote suite passes 24 checks.
The existing deployment tunnel, remote, bin-only, MCP installer, setup-portal, socket, and TCP suites pass.
The socket and TCP suites required permission for local listeners outside the sandbox.
All SSH lifecycle checks use stubs. No remote connection or live MCP registration changed.
The old deployment tunnel bodies remain until the extraction adapters replace them.
Implementation commit: `a203beeb`.
Final inspection exposed a source fallback after an explicit CLI selection named a missing executable.
The new regression failed before the guard and passed after it.
The expanded profile suite passes 17 checks.
It also verifies all three forwards for both profiles and stops offset search after one refused probe.

## A6: Pinned source validation

The initial `test-tool-release.sh` run failed two of 21 checks.
The absent checker could not accept the clean exact pin or ordinary ignored bytecode.
The fixture created literal bytecode files. No Python compilation ran in the source checkout.
The expanded suite passes 30 checks, including Git index preservation.
The checks cover full commit validation, inventory agreement, unsafe paths, absent support, and symlinks.
They reject tracked changes, tracked bytecode, and untracked source hidden by local or global excludes.
The cache precondition requires an effective rule from a tracked repository `.gitignore`.
A negative rule, local exclude, or global exclude cannot satisfy it.
The current inventory includes temporary compatibility and does not define the final neutral release.
Implementation commit: `f35dc407`.
The checker also accepted that exact clean implementation checkout with its intermediate inventory.

## Original foundation verification

The original author shell sweep passed all 24 selected suites.
It activated the Miniforge base environment before execution.
That sweep inherited the caller environment without clearing the full legacy input set.
The isolation correction below replaces that test condition for revised results.
The suites were:

```text
test-node-profile
test-legacy-node-inputs
test-profile-launchers
test-profile-exit-status
test-shell-cli-lib
test-chat-shell-cli-deadline
test-chat-mcp-installed
test-flags
test-register-source-independence
test-register
test-register-bundle
test-register-bootstrap
test-register-import
test-register-host-install
test-profile-tunnel
test-profile-mcp-paths
test-chat-tunnel
test-chat-remote
test-mcp-install
test-setup-portal
test-node-9-bin-only
test-node-9-tunnel
test-node-9-remote
test-chat-shell-portal
```

Each command was `bash shell-scripts/NAME.sh` with the listed suite name.
Logs reside at `/private/tmp/CHAT-foundation-final-NAME.log`.
The source-check suite separately passed 30 checks under `/bin/bash` 3.2 after its final change.
The socket lifecycle suite passed again after the explicit CLI correction, with permission for local listeners.
The earlier local TCP suite passed eight checks with the same permission.

The first required partial build stopped at a sandbox-denied Kotlin daemon marker write.
In-process compilation reached tests, where Mockito could not initialize its agent.
An explicit installed Byte Buddy agent resolved that error.
The next sandboxed run reached MCP tests, where local listener binds were denied.
The final run received permission for local test listeners and reported `BUILD SUCCESS` with exit status 0.
Its command was:

```bash
source /Users/darkbit1001/miniforge3/etc/profile.d/conda.sh
conda activate base
MAVEN_OPTS='-Dkotlin.compiler.daemon=false -Dkotlin.compiler.execution.strategy=in-process -DargLine=-javaagent:/Users/darkbit1001/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar' \
    just build-partial chat-shell,chat-mcp unit
```

Log: `/private/tmp/CHAT-foundation-partial-allowed.log`.
The reactor reported success for all 13 selected projects and required dependencies.
Surefire terminated the `chat-client-rsocket` test JVM after a 30-second exit timeout.
That module reported 93 tests, no failures, no errors, and no skipped tests before Maven continued successfully.
This timeout is a visible verification limit. It is not a test assertion failure.
The partial build does not establish full PR CI.
No full PR CI, Docker image build, real jar staging, or live two-agent acceptance ran during that original verification.

`git diff --check` and all six existing drift bindings pass.
The main checkout was clean at the stated master basis during that verification.
The separate destination repository was empty with no commits at that time.
The [intermediate scan](INTERMEDIATE-SCAN.md) records 1271 matching lines in 98 files at `f35dc407`.
This report counts matching lines, not occurrences, and makes no final extraction claim.
Owner foundation implementation review was pending at that point. The later approval is recorded above.

## Review correction: inherited test inputs

Review basis: `54113f32`. Correction issue: CHAT-jgtsuyfo.
The owner found that `test-register.sh` inherited legacy selectors while some cases cleared only their neutral counterparts.
The compatibility reader correctly refused unequal values.
The correction changes test isolation and evidence only.

Before the correction, this command reported 119 checks and five failures:

```bash
NODE_9_REGISTER_TOPIC_NAME=demo-chat-register bash shell-scripts/test-register.sh
```

With `NODE_9_REGISTER_TOPIC_NAME=ambient-register`, the same suite reported 119 checks and 77 failures.
The arbitrary topic also changed default register selection.
Logs: `/private/tmp/CHAT-foundation-isolation-topic-red.log` and `/private/tmp/CHAT-foundation-isolation-red.log`.

The new `test-suite-environment.sh` initially failed 24 of 47 checks because no shared boundary cleared its seeded inputs.
After the helper existed, all 47 checks passed under `/bin/bash` 3.2.
The test obtains the full alias table from the reader.
It seeds every legacy alias and neutral counterpart with unequal fixture values.
It also seeds `NODE9_DIR` and `STUB_REQUIRE_NODE9_ENV`.
The helper removes all 23 legacy names while preserving neutral and unrelated inputs.
A deliberate conflict introduced after the boundary still returns 2.
Logs: `/private/tmp/CHAT-foundation-isolation-helper-red.log` and `/private/tmp/CHAT-foundation-isolation-helper-final.log`.

At `b9708ae9`, 36 suite files reference `test-support/clear-legacy-inputs.sh` before fixture setup.
They stop if the helper cannot load.
The 119-case Register suite passes with the normal topic inherited.
The author also reported a passing Register run with all 23 legacy names set to conflicting fixture values before suite entry.
The executable seed assertions for all aliases reside in `test-suite-environment.sh`.
Both revised results require legacy inputs cleared before fixtures, rather than a caller that happens to omit them.
Logs: `/private/tmp/CHAT-foundation-isolation-topic-green.log` and `/private/tmp/CHAT-foundation-isolation-full-green.log`.

The helper is test-only transitional support. It is not an installed tool or a permanent scan exception.
Deployment test transfer must preserve the isolation boundary.
Retained general tests must remove this dependency when final adapters replace the temporary reader.
The lexical report remains scoped to `f35dc407`.
The owner measured 1286 matching lines across 100 files at the later `54113f32` review basis.
Neither count describes this correction's final tree.

The partial Maven result was author-reported at that point. The later independent GitHub result is recorded above.

The corrected sweep activated Miniforge base and sourced the helper before its suite loop:

```bash
source /Users/darkbit1001/miniforge3/etc/profile.d/conda.sh
conda activate base
. shell-scripts/test-support/clear-legacy-inputs.sh
```

All 36 suites in that loop passed.
They comprise the original 24-suite list plus these twelve suites:

```text
test-tool-release
test-authserv-profile
test-build-node-9-image
test-chat-deploy-progress
test-deploy-recipe
test-image-manifest
test-key-type-required
test-node-9-install-secrets
test-node-9-register
test-node-9
test-shell-recipe
test-suite-environment
```

Each command was `bash shell-scripts/NAME.sh`.
Logs reside at `/private/tmp/CHAT-isolation-sweep-NAME.log`.
Separate socket lifecycle and TCP suites also passed under cleared legacy inputs with permission for local listeners.
The unchanged secret suite again reported seven failures in fourteen checks in this sandbox.
Its command used `ANSIBLE_LOCAL_TEMP=/private/tmp/CHAT-isolation-ansible-cache` after the same input clearing.
Its failure count remains environment-dependent, as the owner review established.
No runtime implementation or secret behavior changed.
Shell syntax checks pass under `/bin/bash` 3.2.
`git diff --check` and all six drift bindings pass.
The owner later approved `b9708ae9`. CHAT-qdsmackk still requires issue closure after this record correction.
