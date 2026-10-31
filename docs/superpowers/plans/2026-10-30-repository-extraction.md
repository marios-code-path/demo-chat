# Deployment repository extraction implementation plan

For the implementer: use `superpowers:executing-plans` after owner plan approval.
Use one agent. Do not use subagents or Markdown task checkboxes.

Goal: transfer deployment configuration and wrappers into the independent node-9 repository without losing legacy input support.
Architecture: node-9 selects profiles, translates legacy inputs, and consumes a pinned demo-chat tool release.
Demo-chat retains general mechanisms and Register and Portal implementations.
Technology: existing shell and Perl utilities, Maven staging, Git pins, and the current installed layout.

Status: proposed plan. No implementation is authorized.
Review issue: CHAT-utubzjvi. Parent: CHAT-zffgckla.
Inspection basis: demo-chat `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
Destination: `/Users/darkbit1001/workspace/node-9`, empty during inspection.
Prerequisite: reviewed implementation of `2026-10-30-extraction-foundation.md`.
Prerequisite: reviewed implementation of `2026-10-30-image-composition-proof.md` before the final release pin.
Contracts: `../specs/2026-10-30-node-profile-contract.md` and `../specs/2026-10-30-demo-chat-tool-consumption.md`.

## Approval and publication gates

The owner must approve this plan separately from the foundation plan.
Implementation must use the reviewed foundation revision, not the inspection commit as a claimed compatible release.
Every source deletion must follow a verified destination copy or a verified retained general implementation.
Keep source and destination commits separate and record their correspondence.
No live home installation, register import, process replacement, or remote deployment is authorized by this plan.
Live work requires its existing owner and acceptance procedure.
Each implementation task requires failing-test evidence before its behavior changes.
Owner implementation review remains necessary before release.
Owner approval and GitHub approval remain necessary before any PR merge.

Use `DEMO_CHAT_HOME` for the implementation checkout and `NODE9_REPO` for the destination checkout in commands.
These task variables must name independent absolute paths.
Do not assign HOME, home, or CODEX_HOME.
Run demo-chat commands from `$DEMO_CHAT_HOME` and deployment commands from `$NODE9_REPO`.

## Exact move and retention map

| Current demo-chat path | Destination or retained responsibility |
| --- | --- |
| `node-9/compose.yaml`, `compose.vector-disk.yaml`, `advertise.yml`, `userinit.yml` | Same basenames at node-9 repository root. |
| `node-9/node-9.env`, `install`, `.gitignore` | Node-9 repository root, with the approved neutral profile and installer changes. |
| `node-9/ansible/`, `schema/`, deployment README sections | Node-9 repository root. |
| Deployment portions of `node-9/bin/lib.sh` | Node-9 `bin/lib.sh`. |
| `node-9/bin/start`, `stop`, `save-images`, `load-images`, `setup-secrets`, `secrets-local.sh`, `setup-portal` | Node-9 `bin/`, retaining deployment policy and thin tool calls. |
| `node-9/bin/node-9-tunnel`, `node-9-remote` | Node-9 thin wrappers over the foundation tunnel tools. |
| `node-9/bin/isTCPOpen` | Public compatibility wrapper. Its implementation remains in demo-chat `shell-scripts/isTCPOpen`. |
| `node-9/bin/register*` | Public compatibility adapters. General implementations remain in demo-chat `shell-scripts/register*`. |
| `shell-scripts/build-node-9-image` | Node-9 `bin/build-node-9-image`. |
| `shell-scripts/generate-agent-instructions` | Node-9 `bin/generate-agent-instructions`. |
| `shell-scripts/test-build-node-9-image.sh` | Node-9 `tests/test-build-node-9-image.sh`. |
| Every tracked `shell-scripts/test-node-9*.sh` | Node-9 `tests/`, with generic register behavior separated first. |
| `shell-scripts/test-setup-portal.sh` | Node-9 `tests/test-setup-portal.sh`, because it tests the deployment wrapper. |
| Foundation `shell-scripts/test-register-host-install.sh` | Node-9 `tests/test-register-host-install.sh`. |
| Installation cases currently under `test-shell-cli-lib.sh` | Node-9 `tests/test-installed-tools.sh`. |
| `test-register-import.sh`, `test-register-bundle.sh`, general `test-register-bootstrap.sh` | Demo-chat, with general tool fixtures and neutral inputs. |
| General behavior currently under `test-node-9-register.sh` | Demo-chat `shell-scripts/test-register.sh`. |
| Generic staging, options, deadline, and Java-probe cases under `test-shell-cli-lib.sh` | Demo-chat, independent of deployment installation. |
| `test-chat-shell-portal-tunnel.sh`, `test-is-tcp-open.sh`, general MCP tests | Demo-chat. |
| Register and Portal Kotlin source and tests | Demo-chat, with accurate general comments and descriptions. |

There are seven tracked `test-node-9*.sh` files and one image-builder suite at the inspection commit.
Do not repeat the old assertion that twelve filename-matched suites exist.
Additional deployment cases come from mixed suites and `test-setup-portal.sh`.
The move map determines ownership by behavior, not a historical suite count.

## Task B1: Establish the destination profile and final alias reader

Owner: CHAT-kbuahkun. Contract owners: CHAT-zjezvtwh and CHAT-ekduxjqn.

Create destination `bin/compat-inputs.sh`, `bin/lib.sh`, `node-9.env`, `tests/test-compat-inputs.sh`, and `tests/test-profile.sh`.
Create `tests/fixtures` for deployment fixtures without copying credentials or runtime state.
Copy only tracked deployment files from the move map.
Do not copy data, secrets, images, local Git files, ignored artifacts, or another worktree.

The final reader implements exactly the six approved alias rules.
Reuse the foundation reader's input vectors in this destination suite.
Keep its implementation deployment-owned after temporary demo-chat compatibility disappears.
The final profile uses neutral keys but preserves node-9 project, port, image, persistence, version, embedding, and vector-owner values.
Keep the current deployment isolation policy.
The wrapper-derived root must remain non-overridable.
Legacy profile records remain literal data during translation.

Add the shared input vectors before implementing the final reader.
Run `bash tests/test-compat-inputs.sh` from the destination.
Expected initial failure: no final reader accepts legacy-only values or rejects unequal counterparts.
Assert equal, unequal, absent, empty, legacy-only, unsafe profile text, and changed-root cases.
Run `bash tests/test-profile.sh`.
Expected initial failure: the profile or isolation implementation is absent.
After implementation, assert the existing fixed remote values and rejected overrides.

Source files remain available until all destination tests pass.
This task does not yet remove the temporary demo-chat reader.

## Task B2: Define one deployment composition and separate generic image checks

Owner: CHAT-mylxclsx and CHAT-kbuahkun. Related proof issue: CHAT-scbvcozr.

Create destination `bin/core-composition.sh`.
It owns one Bash argument array and one environment assignment array for the current core build.
Copy the existing flags and topology values exactly before considering any functional change.
Destination `bin/start` and `bin/build-node-9-image` must read those arrays for execution and dry-run output.
Both call `$DEMO_CHAT_HOME/shell-scripts/chat-build` for source builds.
Installed start continues to refuse a source build and names the existing image-load procedure.

Consume demo-chat `shell-scripts/verify-image-source` and `verify-image-composition` from the reviewed proof plan.
The source verifier receives the boot jar, expected source commit, and immutable image ID as explicit inputs.
Preserve nested chat-jar checks and the image-layer stamp check through that verifier.
The deployment builder supplies the expected image and composition.
It must not infer the demo-chat revision from the node-9 repository commit.

Add destination `tests/test-core-composition.sh` before editing either consumer.
Its fake `CHAT_BUILD_COMMAND` records each argument and environment value in separate records.
Run `bash tests/test-core-composition.sh`.
Expected initial failure: start and builder use independent definitions or no shared composition file.
Assert identical actual calls and dry-run argument order.
Change one fixture composition option and require both consumers to change together.
Then run the moved `tests/test-build-node-9-image.sh` against stale and matching stamp fixtures.
Generate expected composition JSON through the shared definition and the generic builder's `--composition-json` mode.
Inspect the built image once and supply that image ID to both verifiers.
The integration test must assert that both verifiers run and receive the intended expected inputs.

Run the reviewed generic source and composition suites from demo-chat.
Add a deployment case with the correct source commit and a wrong composition digest.
Expected initial failure: the old deployment builder accepts that image because it checks stamps alone.
After integration, it must refuse that image and retain the correct image-ID binding.

The composition proof has its separate approval and implementation review under CHAT-gyfhbsbd and CHAT-scbvcozr.
Source stamps never substitute for that proof.
Do not publish the final pin until both proof implementations receive the required review and acceptance.

## Task B3: Stage pinned tools and publish compatible public adapters

Owner: CHAT-ekduxjqn and CHAT-kbuahkun.

Create destination `demo-chat-tools.lock.json`, `bin/resolve-tools.sh`, and `tests/test-tools-source.sh`.
Modify destination `install` and create `tests/test-installed-tools.sh`.
Create public compatibility adapters for `chat-shell-cli`, `chat-shell-portal`, `chat-mcp-stdio`, and the register tools in destination `bin/`.
Each adapter reads `compat-inputs.sh`, selects the deployment profile, and invokes its distinct general executable.
Set `CHAT_SHELL_LIB=$prefix/lib` and `CHAT_MCP_LIB=$prefix/lib/mcp` when using installed runtimes.
Set neutral deployment home and bin paths explicitly for general register and MCP path resolution.

The lock contains `interfaceVersion`, `demoChatCommit`, `sourceTools`, and `runtimeFiles`.
The file arrays follow the foundation inventory format.
The release pin must be the full commit of the reviewed final general tool release.
The exact future SHA is a release output, not a value to invent in this plan.
Test locks use real commits created inside throwaway fixture repositories.

Before removal of temporary compatibility, allow only a separately labeled transitional fixture pin during offline testing.
Never publish that fixture lock as the final deployment lock.
After Task B5 and implementation review, resolve the release pin from the actual accepted demo-chat commit:

```bash
release_commit=$(git -C "$DEMO_CHAT_HOME" rev-parse HEAD)
git -C "$DEMO_CHAT_HOME" diff --exit-code
git -C "$DEMO_CHAT_HOME" diff --cached --exit-code
jq --arg commit "$release_commit" '.demoChatCommit = $commit' \
    "$NODE9_REPO/demo-chat-tools.lock.json" > "$NODE9_REPO/demo-chat-tools.lock.json.next"
mv "$NODE9_REPO/demo-chat-tools.lock.json.next" "$NODE9_REPO/demo-chat-tools.lock.json"
```

The resolver must validate source cleanliness, inventory, ignore-rule origin, and version through `check-tool-source` before replacement.
The installer stages general files beneath `$prefix/tools/demo-chat/bin` and jars beneath the existing runtime paths.
The installer copies its deployment-owned generator from destination `bin`, not from the demo-chat release inventory.
It makes compatible public adapters available before replacing legacy public paths with neutral-only behavior.
It must never leave an adapter whose private tool or reader is absent.
Keep the earlier compatible files until the replacement set can serve the same public calls.
Preserve explicit secret choices, symlink guards, owned-state checks, and runtime-swap rollback.
Record the interface version, source commit, and stage identity in installation metadata.

First add refusal cases to `tests/test-tools-source.sh`.
Run `bash tests/test-tools-source.sh`.
Expected initial failure: the old installer requires sibling paths or cannot reject wrong pins before copying code.
Use independent source and deployment fixture directories, including paths with spaces.
Then add the installed execution case to `tests/test-installed-tools.sh`.
After installation, rename only the fixture source directory out of reach and remove Maven from the fixture PATH.
Existing public commands must still invoke the installed general executable with expected neutral inputs.
Use fake Java to inspect both JVM arguments and environment values.

The same suite must fail an incompatible `--bin-only` update before replacing any public executable.
It must retain truthful `--skip-cli` metadata and the previous runtime on failed staging or swapping.
Snapshot fixture code, runtime, secrets, and state hashes before each injected failure.
After failure, require the earlier public invocation to work with legacy inputs.
Do not test rollback by altering a real installed home.

## Task B4: Transfer deployment wrappers, tests, and their runner

Owner: CHAT-kbuahkun and CHAT-dnteampq.

Create destination `justfile` and `tests/run.sh`.
Move the exact deployment files and suites from the map after their destination copies pass.
Destination `node-9-tunnel`, `node-9-remote`, `setup-portal`, and `isTCPOpen` call reviewed general tools.
They supply deployment profiles, target host defaults, optional authserv port values, and public path compatibility.
They must not retain a second tunnel lifecycle or offset-selection implementation.
Preserve existing argument words and subprocess statuses.

Before moving the register suite, create demo-chat `shell-scripts/test-register.sh` from its general command-contract cases.
Move pointer parsing, typed JSON, deadlines, portal routing, retry receipts, and write semantics into that general suite.
Keep only deployment path, profile, source-use, and wrapper-environment assertions in the moved `tests/test-node-9-register.sh`.
Split the installation section of `test-shell-cli-lib.sh` into destination `tests/test-installed-tools.sh`.
Keep library/source modes, option generation, Java probing, staging, and jar stamps in demo-chat.
Move the deployment-only `test-setup-portal.sh` rather than leaving a generic CI step that still invokes the deployment wrapper.

Rename the real stub control to `STUB_REQUIRE_PROFILE_ENV` wherever a generic retained case requires profile values.
No whole variable named `NODE9_ENV` is a migration input.
Do not rename a CQL-specific deployment assertion and then present it as generic behavior.

First add destination `tests/test-runner.sh`.
It replaces each listed suite with a fixture that records invocation and one controlled nonzero status.
Run `bash tests/test-runner.sh`.
Expected initial failure: no deployment runner invokes every moved suite or propagates failure.
The runner must list suites explicitly and stop with a nonzero status when any suite fails.
Add `just check` to run `bash tests/run.sh` from the destination root.
Add image-build and deployment operations as node-9 recipes, with no corresponding demo-chat forwarding recipe.

Run `just --justfile "$NODE9_REPO/justfile" check` from an unrelated current directory.
Expected result after implementation: all offline destination suites pass without a sibling demo-chat directory.
Run retained general suites from demo-chat separately.
Document which renamed or split test now owns every original assertion group.
Do not infer coverage preservation from the total number of passing assertions.

## Task B5: Remove source coupling and temporary compatibility

Owner: CHAT-gdddeqkb and CHAT-kbuahkun.

Remove deployment recipes from demo-chat `justfile` after destination recipes pass.
Remove deployment test steps from `.github/workflows/maven.yml`.
Keep generic register, MCP, tunnel, image, and staging checks in demo-chat CI.
Add the retained `test-register.sh` and tool/profile suites to that workflow.
Do not add node-9 checkout or deployment test steps to demo-chat CI.

Remove the demo-chat node-9 subtree only after the verified destination correspondence is complete.
Remove the moved builder and generator from demo-chat `shell-scripts`.
Remove temporary `legacy-node-inputs.sh`, its public compatibility calls, and deployment-specific alias fixtures from demo-chat.
First verify that node-9 `compat-inputs.sh` and adapters serve every supported legacy invocation.
Move the alias input vectors to destination tests as part of that ownership transfer.
Do not remove legacy input support from node-9.

Create demo-chat `shell-scripts/test-source-independence.sh` before deletion.
It exports the tracked candidate tree to a temporary directory without the deployment subtree.
It runs the general profile, tool, MCP, register, tunnel, and staging suites from that export.
Initialize a local Git fixture with one commit for source-build suites that require commit stamps.
That fixture commit identifies the exported test tree. It is not the release pin.
Run `bash shell-scripts/test-source-independence.sh`.
Expected initial failure: a retained suite, wrapper, recipe, or dependency still reads a deployment file.
The passing result must not require Git history for ordinary runtime operation.
Keep the historical splitter proof in the real Git checkout because it intentionally reads frozen source history.

Record a two-repository revision matrix before source deletion:

| Combination | Required behavior |
| --- | --- |
| Original installed fixture. | Existing legacy invocation still works. |
| Foundation tools and temporary reader. | Both accepted input forms work. |
| Destination adapters and final general tools. | Legacy inputs translate before neutral tool execution. |
| Wrong pin, absent private tool, or incompatible interface. | Installation refuses before exposing a broken public command. |

Publish the final pin only after the final neutral-only tool commit passes implementation review.
There is no requirement for the independent repositories to share a commit hash or atomic Git commit.
Their correspondence is the exact pin and tested installation boundary.

## Task B6: Correct active prose and review the complete lexical scan

Owner: CHAT-gdddeqkb.

Update demo-chat `README.md`, `CHAT_MCP_AGENTS.md`, `docs/MCP-ADAPTER.md`, and `docs/SHELL-COMMAND-CONTRACT.md`.
Split `docs/SHELL-OPERATOR.md` into general operator guidance and destination `docs/OPERATIONS.md`.
Keep general Register and Portal documentation in demo-chat.
Keep actual deployment procedures and values in node-9.
The destination generator must emit the same authenticated MCP policy with accurate register paths.
The generator's node-9 ownership does not move the general register contract into that repository.

Correct comments or descriptions in these retained Kotlin files without changing behavior:

- `chat-shell/src/main/kotlin/com/demo/chat/shell/commands/PubSubCommands.kt`.
- `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteRules.kt`.
- `chat-shell/src/main/kotlin/com/demo/chat/shell/portal/PortalWriteService.kt`.
- `chat-shell/src/test/kotlin/com/demo/chat/test/commands/PubSubRegisterReadTests.kt`.
- `chat-mcp/src/main/kotlin/com/demo/chat/mcp/portal/PortalToolRegistration.kt`.

Compiled comments must describe behavior without citing supporting shell scripts as authority.
Preserve the actual schema, limits, statuses, denial behavior, and uncertainty markers.

Create demo-chat `shell-scripts/check-deployment-references.sh` and its `test-deployment-references.sh` suite.
Create `docs/superpowers/proofs/2026-10-30-extraction-references.json` for reviewed historical and planning records.
The raw scan must examine tracked paths and text for only `node-9|node9|node_9`, without case sensitivity.
Exclude the deployment subtree and registered nested worktrees.
Do not add numeric-port patterns or silently exclude documentation directories.
Report every hit with its path and line before classifying it.
Classify frozen history and approved extraction records separately from active code or operator guidance.
Do not rewrite frozen register history to manufacture an empty scan.
Review historical hits explicitly. Do not describe the result as zero textual matches.
The review file records each accepted record path, content hash, classification, and review issue.
Do not accept a directory wildcard or an active implementation file as a historical record.
Changed record content requires review before its stored hash changes.
Active aliases, the old stub name, source reads, temporary readers, and deployment-specific active examples must fail the guard.

Add a test fixture containing `STUB_REQUIRE_NODE9_ENV` in an active test file.
Run `bash shell-scripts/test-deployment-references.sh`.
Expected initial failure: the new guard is absent or ignores the substring inside the larger stub name.
Require that fixture to fail even when no whole `NODE9_ENV` variable exists.
Add fixtures for all three case variants, a deployment subtree, a registered nested worktree, and reviewed frozen history.
The report must retain the history hit while excluding the two permitted subtree classes.
Never grant the temporary reader or old stub a permanent exception.

Run `drift refs` for every changed bound document before editing it.
Review and correct prose before any required `drift link`.
Run `drift check` and `git diff --check` after the correction.
Run `just build-partial chat-shell,chat-mcp unit` for the changed tool description and retained compiled contracts.

## Final acceptance and retained work

Run destination `just check` with the final reviewed source pin.
Run retained demo-chat shell suites, source-independence checks, lexical scan, and full PR CI.
Run one real staging build with the approved target Java before claiming real staged-runtime acceptance.
Keep stubbed installation evidence separate from that real build.
Record source and deployment commits, final lock contents, test correspondence, and every environment-limited check.
Request implementation review under CHAT-utubzjvi.

CHAT-scbvcozr requires reviewed completion of the companion composition-proof plan and its real image acceptance.
CHAT-vnljyxpl retains launch wrappers, draft PR #311, and live two-agent acceptance.
It must adapt the approved private_key_jwt procedure to the final deployment paths.
The guide remains the final publication in the extraction.
CHAT-vepxpmlk retains agent withdrawal and related drift gaps.
These retained issues prevent a false claim that the parent objective is fully complete.
Do not merge without the remaining evidence and approvals.
Do not deploy without the remaining evidence and approvals.
Do not close the parent without the remaining evidence and approvals.
Do not publish the final guide without the remaining evidence and approvals.
