# Tool consumption proposal

Date: 2026-10-30.
Issue: CHAT-ekduxjqn.
Status: approved for planning. Implementation still requires plan approval.
Revision: 3. The owner resolved all three findings in the joint review.
Inspection basis: demo-chat master `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.

## Release shape

I recommend an explicitly selected demo-chat Git checkout pinned to an exact commit.
Node-9 uses that checkout for builds and installation.
Installed execution uses copied launchers and staged jars, as the current installer already does.
This approach extends the existing installation mechanism.
It does not require a new binary release service.

A prebuilt tool archive is an alternative.
It can remove Maven from installation, but it requires artifact publication and compatibility evidence that do not exist in this proposal.
Implicit sibling-checkout discovery is another alternative.
It cannot establish the intended revision and breaks independent repository placement.

## Selection and pin

`DEMO_CHAT_HOME` selects the absolute source checkout path.
Node-9 must not infer that path from its parent directory or the current directory.
The installer must not clone, fetch, update, or switch that checkout implicitly.

A node-9-owned lock records the required demo-chat commit and tool interface version.
Version 1 defines the agreed commands, environment inputs, installed layout, and staging metadata.
The lock also lists required tools and their support files.
The selected checkout must match the full commit and contain no tracked changes or untracked build inputs.
The pinned checkout must include a tracked `.gitignore` rule that excludes Python `__pycache__` directories.
This is a precondition for the first compatible pinned commit.
The current inspection commit lacks that rule and does not satisfy this precondition.
The installer checks that `git check-ignore shell-scripts/__pycache__/probe.pyc` succeeds in the selected checkout.
It also checks verbose output to establish that a tracked repository `.gitignore` supplies the matching rule.
Global excludes and `.git/info/exclude` cannot satisfy this precondition.
The check requires no generated file and writes no bytecode.
Repository ignore rules may classify generated output only after this precondition succeeds.
The gate still rejects every tracked change, even when an ignore rule matches its path.
The gate rejects non-ignored untracked build inputs.
The installer must not add local ignore rules or modify the checkout to pass the gate.
Missing tools, mismatched versions, and mismatched commits cause failure before installation changes.

The inspection commit `1a8fd033` is not a claimed compatible tool release.
The first compatible commit must include the approved neutral contracts.
Node-9 will pin that commit after its implementation review.
The tracked ignore rule must exist before node-9 selects that compatible pin.
Adding a profile key also requires a compatible new demo-chat pin.
An older pinned tool must continue to reject an unknown key.

## Dependency direction

Node-9 resolves source tools beneath `$DEMO_CHAT_HOME/shell-scripts`.
Demo-chat tools resolve their own source files from their own checkout.
They receive `CHAT_NODE_PROFILE` from their caller.
Neither side requires sibling repositories.

The lock inventory must include each selected command and all support files that it reads.
Known surfaces include `chat-build`, `chat-deploy`, `build-shell-cli-lib`, and `java-probe.sh`.
Runtime surfaces include the shell launchers, `chat-mcp-stdio`, reusable tunnel utilities, and general register tools.
MCP installer support includes `mcp-install-lib.sh`.
General register logic currently beneath node-9 must become demo-chat-owned before node-9 consumes it.
The old claim that exactly six scripts define the dependency is insufficient.
The dependency audit must include indirect files and general functions currently mixed into deployment libraries.

Node-9 supplies its composition to general builders.
Generic image verification receives the expected composition and source revision from its caller.
Node-9 owns the expected composition and deployment acceptance.
Fresh source stamps alone do not prove composition.
CHAT-mylxclsx and CHAT-scbvcozr retain that work.

## Installed layout and compatibility

The existing public entry paths under `$prefix/bin` remain valid.
Node-9 compatibility adapters occupy public paths when legacy translation is necessary.
The installer copies general executables and their support files beneath `$prefix/tools/demo-chat/bin`.
Adapters call those general executables directly and must not resolve themselves recursively.
The source checkout continues to expose general tools beneath `$DEMO_CHAT_HOME/shell-scripts`.

CHAT-zjezvtwh names the temporary demo-chat reader `shell-scripts/legacy-node-inputs.sh`.
Its compatibility entry points and copied dependency set must exist before neutral renames remove existing legacy reads.
Node-9 `bin/compat-inputs.sh` becomes the final alias reader.
Node-9 adapters must become available before temporary demo-chat compatibility is removed.
An installation must retain a compatible public entry point throughout that transition.
The temporary compatibility files are not part of the final neutral-only demo-chat release.
The final lock must select a commit whose general tools need no temporary legacy reader.
These are design constraints, not an approved sequence of implementation tasks.

The existing `$prefix/lib` and `$prefix/lib/mcp` runtime layout remains the staging target.
General shell launchers receive the explicit runtime path through `CHAT_SHELL_LIB`.
General MCP launchers receive an equivalent proposed input, `CHAT_MCP_LIB`.
An explicit runtime path must not fall back to source execution when its runtime is absent.
General launchers must not depend on a node-9 installation marker to select an explicitly supplied runtime.

The installer records the tool interface version, full source commit, and stage identity in non-secret installation metadata.
The staged jars and copied launchers must come from the same pinned checkout.
Existing per-jar commit checks remain necessary.
Tool paths must be relative to the installed prefix or supplied explicitly.
The installed home must not record a required build-checkout path.

The runtime option file retains its key-type and node-ID checks.
Build-host Java selection remains distinct from target-host runtime selection.
Installation must select a target Java executable without requiring a build-host absolute path on another host.
Existing explicit runtime Java overrides retain precedence.
Minimum Java requirements remain those of the compiled runtime.
This design does not include a JVM upgrade.

## Installation behavior

The installer validates the lock, profile, tools, Java, and existing state before replacing code.
It builds and checks the jar stage before replacing the installed runtime.
It preserves the current explicit secret-selection choices.
It preserves state directories and existing credentials.
Existing rollback behavior for a failed runtime swap remains required.

`--bin-only` must reject copied launchers incompatible with the installed runtime interface.
`--skip-cli` must record that no new runtime was installed.
Neither mode may claim a complete new runtime without evidence.
Installation failures must identify the failed phase without printing credentials.

## Joint acceptance contract

- Independent paths for both repositories exercise the same build and installation commands.
- Missing `DEMO_CHAT_HOME`, a wrong commit, or a missing support file causes failure before installed code changes.
- The same pinned tools accept node-9 and node1001 profiles without new deployment code.
- A copied installation runs with the demo-chat checkout unavailable and Maven absent from PATH.
- Existing public command paths and node-9 legacy inputs continue through compatibility adapters.
- Each adapter invokes a distinct general executable.
- Explicit invalid runtime paths cause failure without source fallback.
- Incompatible `--bin-only` updates cause failure before replacing installed executables.
- A failed stage or runtime swap preserves the prior runtime and state.
- Generic tests run in demo-chat CI without node-9 files.
- Deployment tests run through the node-9 repository runner.
- A checkout without the tracked Python cache ignore rule fails the compatibility precondition before installation changes.
- A compatible checkout still passes the cleanliness gate after an ordinary Python compilation check creates ignored bytecode.
- Tracked bytecode, changed tracked source, and non-ignored untracked source still cause failure.
- A missing ignore rule cannot be bypassed through an installer-created local exclusion.
- A global exclude without the tracked repository rule cannot satisfy the precondition.
- The compatibility transition preserves a legacy reader at each supported public entry point.

These conditions require future tests.
Full implementation acceptance remains untested.
The owner reported a successful throwaway-repository check of ignore-rule origins.

## Joint review result

The profile and tool designs share one dependency direction: node-9 consumes demo-chat.
The proposed compatibility adapters keep legacy names outside demo-chat after extraction.
Both designs require explicit paths and reject implicit sibling discovery.
Both designs preserve installed operation without a source checkout.
The owner permits file-move planning after joint design approval.

The owner approved both contracts for planning.
An approved design permits preparation of a plan only.
The owner must approve that plan before implementation.
Implementation review remains a separate gate.
CHAT-vnljyxpl retains launch wrappers, draft PR #311, and live two-agent acceptance.
The guide remains the final publication in the extraction.
CHAT-vepxpmlk retains agent withdrawal and other drift gaps.

## Inspection and limits

The authenticated MCP register read returned status 0 and complete=true without warnings.
The master checkout was clean during contract inspection.
The node-9 destination had no files or commits during contract inspection.
The old Codex worktree supplied no implementation evidence for these designs.
Contract inspection changed no repository code, documentation, provenance, or installed state.
The approved contracts reside in FP and will accompany the implementation plans.
Contract inspection changed no drift binding.
Contract inspection ran no build, test suite, or live deployment check.

## Revision evidence

The review checked master `1a8fd033` again without modifying repository files.
The checkout remained clean.
The measured chat-build refusal statuses support the narrower profile error rule.
Semantic inspection confirmed that `parse_agent_jwks` raises `SystemExit` with a message for refused input.
`git check-ignore` did not recognize the Python cache path at the inspection commit.
Commit `1f944e88` replaced `py_compile` with a parse that writes no bytecode.
That commit changed its plan document, not the repository ignore rules.
The approved ignore-rule requirement remains unimplemented.
Both designs received owner approval for planning.
The owner-required profile inventory correction requires no further design review.
