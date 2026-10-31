# Profile and tool foundation implementation plan

For the implementer: use `superpowers:executing-plans` after owner plan approval.
Use one agent. Do not use subagents or Markdown task checkboxes.

Goal: provide neutral mechanisms while every supported legacy entry point retains an alias reader.
Architecture: temporary compatibility surrounds neutral profile, runtime, register, and tunnel mechanisms.
The separate deployment repository receives the final compatibility adapters under the second plan.
Technology: existing Bash 3.2, Python builder, Perl register utilities, Kotlin contracts, Maven, and Git.

Status: approved for implementation, with the owner-required Task A5 correction applied.
Review issue: CHAT-qdsmackk. Parent: CHAT-zffgckla.
Basis: `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
Approved contracts: `../specs/2026-10-30-node-profile-contract.md` and `../specs/2026-10-30-demo-chat-tool-consumption.md`.

## Gates and evidence

The owner must approve this plan before its implementation starts.
The owner must review implementation before release or extraction relies on it.
The second plan requires separate approval and verified completion of this foundation.
This plan creates no files in the separate node-9 repository.
It changes no installed home, credentials, running process, or remote deployment.

Each numbered task below is a separate change and review unit.
Each task starts with a behavioral test that fails against the preceding accepted state.
Record the failing command, assertion, patch, passing command, and resulting commit in FP.
A missing test file is not the required failure.
Do not claim a stubbed check as Maven, Docker, or live acceptance evidence.

Run all commands from the demo-chat implementation checkout unless a command specifies another directory.
Before editing a bound path, run `drift refs PATH`.
Update stale prose before refreshing reviewed provenance.
Finish each change with its focused checks, `git diff --check`, and `drift check`.
Commit messages must include the owning FP issue.

## File responsibilities

| File | Responsibility |
| --- | --- |
| `shell-scripts/node-profile.sh` | Parse literal profile records and validate allowed keys without execution. |
| `shell-scripts/node-profile` | Emit validated `KEY=value` records for callers that cannot source Bash. |
| `shell-scripts/legacy-node-inputs.sh` | Temporarily translate actual legacy inputs before neutral tools read them. |
| `shell-scripts/register-lib.sh` | General register validation and neutral deployment-path inputs. |
| `shell-scripts/chat-tunnel`, `shell-scripts/chat-remote` | General SSH lifecycle and command execution. |
| `shell-scripts/tunnel-ports.sh`, `shell-scripts/isTCPOpen` | Port arithmetic, selection, availability, and local readiness. |
| `shell-scripts/tool-interface.version` | Tool interface version `1`. |
| `shell-scripts/tool-files.json` | Source and installed dependency inventory, including support libraries. |
| `docs/NODE-PROFILES.md`, `docs/INSTALLED-TOOLS.md` | General operator contracts without deployment-specific defaults. |

`shell-scripts/isTCPOpen` and `shell-scripts/tunnel-ports.sh` do not exist at the inspection commit.
The TCP implementation to promote is `node-9/bin/isTCPOpen`.
The final release omits the temporary compatibility library.
The intermediate inventory includes it whenever a copied entry point reads it.

## Task A1: Establish profile parsing and explicit status boundaries

Owner: CHAT-zjezvtwh. Implementation work remains under the parent after the design issue closes.

Create `shell-scripts/test-node-profile.sh` and `shell-scripts/fixtures/profiles/alpha.env` and `beta.env`.
Create the loader library and command listed above.
Add `.gitignore` rule `__pycache__/` in the later tool-precondition task, not as an unrecorded local exclusion.

The alpha fixture contains these literal records:

```text
NODE_PROFILE_VERSION=1
NODE_PROJECT=chat-alpha
NODE_NETWORK=chat-alpha_default
NODE_ID=7
NODE_KEY_TYPE=uuid
NODE_RSOCKET_PORT=15890
NODE_MGMT_PORT=15891
NODE_REGISTRY_PORT=15892
NODE_IMAGE_REPO=alpha
CORE_IMAGE=alpha/core:0.0.1
NODE_VECTOR_PERSIST=on
```

The beta fixture changes the project, network, image prefix, node ID, and three ports.
Use project `chat-node1001`, node ID `1001`, and ports `16890`, `16891`, and `16892` for beta.
These fixtures test generic behavior. They contain no node-9 alias.

Define library function `chat_node_profile_load` with required key names as arguments.
The command accepts `--require KEY` repeatedly and emits only validated schema records.
The command writes diagnostics to standard error and preserves literal values after the first equals sign.
The command returns 2 for loader errors.
The library exports validated values without `source`, `eval`, or command substitution on file content.
Reject duplicate keys, unknown keys, unsupported versions, invalid ports, and missing or empty required values.
Reject record delimiters and control bytes in values.
Validate identity through the existing accepted launch constraints.
Do not add a new node-ID range that disagrees with the compiled contract.

Add this behavioral case before the implementation:

```bash
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp shell-scripts/fixtures/profiles/alpha.env "$work/profile.env"
printf 'CHAT_EMBEDDING_OPENAI_MODEL=$(touch %s/executed)\n' "$work" >> "$work/profile.env"
status=0
CHAT_NODE_PROFILE="$work/profile.env" bash shell-scripts/node-profile \
    --require NODE_PROJECT > "$work/records" 2> "$work/error" || status=$?
test "$status" -eq 0
test ! -e "$work/executed"
grep -Fqx 'NODE_PROJECT=chat-alpha' "$work/records"
```

Run `bash shell-scripts/test-node-profile.sh`.
Expected initial failure: the command cannot return validated records because no loader exists.
After implementation, every loader case must pass without creating the execution marker.
Also test an explicit invalid path from another current directory with a valid profile beside that directory.
It must fail with 2 instead of discovering the nearby profile.
Test duplicate, unknown, empty, non-absolute, unsupported-version, port-collision, and overflow inputs separately.

## Task A2: Supply the temporary alias reader before neutral renames

Owner: CHAT-zjezvtwh and CHAT-rxfvixhr.

Create `shell-scripts/test-legacy-node-inputs.sh` and `shell-scripts/legacy-node-inputs.sh`.
Implement `chat_legacy_node_inputs` as the named reader for the approved table.
Use an explicit table of actual aliases, not a substring replacement over environment names.
Retain a wrapper-derived deployment root. Do not permit `NODE9_DIR` to redirect it.
The test stub has a separate test-only mapping to `STUB_REQUIRE_PROFILE_ENV`.
There is no alias for a whole variable named `NODE9_ENV`.

The reader must enforce all six approved rules.
It distinguishes unset and empty values before profile defaults apply.
Translate legacy profile records through the same literal parser rules.
Do not execute an old profile as shell code.

Add these executable assertions to the new suite:

```bash
status=0
NODE9_PROJECT=legacy NODE_PROJECT=neutral bash -c \
    '. "$1"; chat_legacy_node_inputs' _ shell-scripts/legacy-node-inputs.sh || status=$?
test "$status" -eq 2
actual=$(env -u NODE_PROJECT NODE9_PROJECT=legacy bash -c \
    '. "$1"; chat_legacy_node_inputs; printf "%s" "$NODE_PROJECT"' \
    _ shell-scripts/legacy-node-inputs.sh)
test "$actual" = legacy
```

Run `bash shell-scripts/test-legacy-node-inputs.sh`.
Expected initial failure: the reader cannot map the legacy-only value or reject the conflicting pair.
Add equal, explicitly empty, absent, changed-root, legacy-profile, and every table-row case.
The neutral bodies must not duplicate alias resolution.
Do not remove an existing legacy read until its entry point invokes this reader.

## Task A3: Neutralize staging and launcher configuration

Owner: CHAT-rxfvixhr. Related contract: CHAT-ekduxjqn.

Modify `shell-scripts/build-shell-cli-lib`, `chat-shell-cli`, `chat-mcp-stdio`, and `chat-shell-portal`.
Modify `shell-scripts/chat-build` only to receive validated profile values and preserve existing argument precedence.
Modify `shell-scripts/test-shell-cli-lib.sh`, `test-chat-mcp-installed.sh`, and `test-flags.sh`.
Create `shell-scripts/test-profile-launchers.sh` and `shell-scripts/test-profile-exit-status.sh`.

Before changing each legacy reader, invoke Task A2 compatibility at its existing public entry point.
Obtain the key type, node ID, and connection ports from explicit inputs or the selected profile.
Remove fixed staging values `9`, `6890`, and `6891` from general behavior.
Freeze key-type and node-ID headers from the effective inputs.
Retain mismatch refusals in installed mode.
Apply effective client ports to both JVM properties and environment values.
Do not let a tunnel offset leave the root-key URI frozen at its remote port.
Keep the existing hard deadline around any new profile work in deadline-governed launch paths.

Add explicit `CHAT_MCP_LIB` selection before source or marker discovery.
An explicitly selected missing runtime must fail without a source fallback.
Retain `CHAT_MCP_JAVA`, `CHAT_SHELL_JAVA`, and the existing explicit runtime overrides.
Do not embed a required build-host Java path in a portable runtime.

The new launcher suite must stage both profiles through the existing Maven and Java stubs.
Assert the stage headers, JVM node ID, root-key URI, client ports, and selected classpath.
Run the installed fixture with no checkout and no Maven in PATH.
Use this refusal assertion before the launcher change:

```bash
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
status=0
CHAT_MCP_LIB="$work/absent" bash shell-scripts/chat-mcp-stdio \
    --config "$work/config.properties" > "$work/out" 2> "$work/error" || status=$?
test "$status" -ne 0
test ! -s "$work/out"
grep -Fq "$work/absent" "$work/error"
```

Run `bash shell-scripts/test-profile-launchers.sh`.
Expected initial failure: staging still freezes deployment identity and ports, or the MCP launcher ignores the explicit runtime.
Run `bash shell-scripts/test-profile-exit-status.sh`.
The suite must run every existing refusal from the approved Exit status scope table without a profile.
It must retain their measured statuses and separately assert 2 for new loader errors.
Use `--dry-run` and refused flags so these cases start no build or service.
Finish with the existing deadline, shell runtime, MCP runtime, and flag suites.

## Task A4: Separate general register tools from deployment setup

Owner: CHAT-kbuahkun. This task prepares general ownership before the physical extraction.

Create general copies beneath `shell-scripts` of `register`, `register-create`, `register-import`, `register-range`, `register-split`, and `register-uuid5`.
Separate the general validation and register selectors from `node-9/bin/lib.sh` into `shell-scripts/register-lib.sh`.
Retain deployment Compose, image, secret installation, and isolation behavior in the deployment library.
Existing node-9 public commands remain compatible entry points during this intermediate state.

Modify `shell-scripts/test-register-import.sh` and `test-register-bundle.sh` to call the general source tools directly.
Split `test-register-bootstrap.sh` by behavior.
Keep generic bundle, retry, receipt, index, recovery, collision, and register-create behavior in that demo-chat suite.
Create `shell-scripts/test-register-host-install.sh` for its deployment installation, environment, secret-path, and prepared-home cases.
That new suite moves to node-9 under the second plan.
Keep shared fixture data in `shell-scripts/fixtures/register-stub-cli` when it tests the general contract.
Do not replace authenticated portal calls with a direct backend path.
Do not extend existing importer CQL behavior or claim that import now uses the resident portal.
Any missing app capability remains a separate issue and cannot be solved by adding a new CQL workaround here.

First add `shell-scripts/test-register-source-independence.sh`.
It copies the general tool dependency closure into a fixture without a node-9 subtree.
It must run byte-preserving split/range/UUID cases and offline register command cases from that fixture.
The actual `STUB_REQUIRE_NODE9_ENV` control becomes `STUB_REQUIRE_PROFILE_ENV` in retained generic stub cases.
Deployment assertions move with their behavior rather than receiving a false neutral name.

```bash
if git grep -n -i -E 'node-9|node9|node_9' -- \
    shell-scripts/register shell-scripts/register-lib.sh \
    shell-scripts/test-register-bundle.sh shell-scripts/test-register-bootstrap.sh; then
    printf 'General register files retain deployment references.\n' >&2
    exit 1
fi
```

Run `bash shell-scripts/test-register-source-independence.sh` after adding its fixture and assertions.
Expected initial failure: the general tools or suites still require node-9 paths or its installer.
Run all three retained register suites and the new deployment split suite after the separation.
Use offline stubs only. No register migration or backend operation is authorized.

## Task A5: Promote tunnel mechanisms and remove generic MCP path discovery

Owner: CHAT-dnteampq and CHAT-rxfvixhr.

Create general tunnel files from the responsibility map.
Promote `node-9/bin/isTCPOpen` into `shell-scripts/isTCPOpen` without changing its exit-status meaning.
Create `shell-scripts/tunnel-ports.sh` for the shared port mechanism.
Modify `shell-scripts/chat-shell-portal-tunnel`, `mcp-install-lib.sh`, `claude-mcp-install.sh`, and `codex-mcp-install.sh`.
Modify their existing tests and `shell-scripts/test-is-tcp-open.sh`.
Create `shell-scripts/test-profile-tunnel.sh` and `test-profile-mcp-paths.sh`.

The current general portal helper resolves three executables through the deployment bin:

- Line 26 resolves `$node9_bin/node-9-tunnel`.
- Line 27 resolves `$node9_bin/isTCPOpen`.
- Line 28 resolves `$node9_bin/chat-shell-cli`.

This is a current ownership violation, not the intended generic resolution rule.
The new general helper resolves its tunnel, TCP check, and CLI through its general tool directory or explicit neutral inputs.
The intermediate node-9 paths serve existing public callers and compatibility wrappers only.
General tools must not use those paths to locate their own mechanisms.
Add a test-profile-tunnel case with a general tool directory and an absent deployment bin.
Place recording TCP and CLI executables only in the general directory.
Require the portal helper to invoke those executables without accessing a deployment bin.
Place failing sentinels in a separate deployment bin to detect accidental fallback in a second case.
Both cases must preserve the general tool calls and produce no deployment-sentinel invocation.

Keep `isTCPOpen` semantics: 0 means all ports are free, 1 means busy, and 2 means a refused check.
Use it for availability selection, not as a success predicate for tunnel readiness.
Use `nc -z -w 1 127.0.0.1 PORT` for each required local readiness probe.
Readiness proves the local bind only. It does not prove remote service health.
Reuse one offset calculation and reject overflow before SSH starts.
Keep option placement, command arguments, process ownership, signal cleanup, and socket tri-state behavior.
Retain the optional authserv forward through explicit operation inputs supplied by node-9.
Do not add unapproved authserv keys to the profile schema.

MCP resolution must use explicit neutral home or executable inputs.
It must not discover an installed deployment beneath `/opt` or a sibling checkout.
Temporary entry points translate legacy home inputs before the neutral resolver runs.
Keep agent and project selection separate from portal authentication.
Do not modify any live Codex MCP registration while testing.

Run `bash shell-scripts/test-profile-tunnel.sh` after adding two-profile SSH argument and port assertions.
Expected initial failure: the old helper names deployment tools and fixed remote ports.
The suite must assert that a refused availability probe stops offset search with its refusal status.
Run `bash shell-scripts/test-profile-mcp-paths.sh` after adding explicit-path and absent-input cases.
Expected initial failure: the resolver discovers the old fixed home or source subtree.
Then run the existing TCP, socket, MCP installer, and deployment tunnel suites.
The old node-9 implementations may remain temporarily until the second plan installs thin wrappers.
Record their scan matches as intermediate work. Do not mark CHAT-gdddeqkb complete.

## Task A6: Declare the tool interface and Python cache precondition

Owner: CHAT-ekduxjqn. Implementation work remains under the parent after the design issue closes.

Create `shell-scripts/tool-interface.version` with `1` and `shell-scripts/tool-files.json`.
Create `shell-scripts/test-tool-release.sh` and `shell-scripts/check-tool-source`.
Modify `.gitignore` with tracked rule `__pycache__/`.

The inventory has `interfaceVersion`, `sourceTools`, and `runtimeFiles` fields.
Each file record has a relative `source` path and an installed `target` basename when installation copies it.
Reject absolute paths, parent traversal, duplicate targets, missing support files, and unsupported versions.
Include the profile, register, tunnel, MCP, and Java-probe dependencies from Tasks A1 through A5.
Include temporary compatibility only in intermediate releases whose entry points still read it.
The final release inventory must omit that temporary file.

`check-tool-source LOCK` reads `DEMO_CHAT_HOME` and validates its full Git commit against `demoChatCommit` in the lock.
It checks interface version, file inventory, tracked changes, and non-ignored untracked build inputs.
It verifies `git check-ignore -v shell-scripts/__pycache__/probe.pyc` against a tracked repository `.gitignore` origin.
Global excludes and `.git/info/exclude` cannot satisfy the precondition.
It never modifies the checkout, Git excludes, or installed state.

The new test creates independent throwaway Git repositories for these cases:

| Fixture | Expected result |
| --- | --- |
| Exact commit, clean source, tracked cache rule, all files present. | Success. |
| No cache rule. | Refusal before installation. |
| Rule only in `.git/info/exclude` or a global exclude. | Refusal. |
| Ordinary bytecode beneath ignored `__pycache__`. | Success. |
| Changed tracked source or tracked bytecode. | Refusal. |
| Non-ignored untracked source, wrong commit, or absent support file. | Refusal. |

Run `bash shell-scripts/test-tool-release.sh`.
Expected initial failure: the tracked cache rule and source validator are absent.
Do not run Python compilation in the real checkout to manufacture this failure.
The fixture may create a literal cache file because this test checks Git classification, not Python compilation.
A separate controlled Python check may run only after Miniforge activation succeeds.

## Foundation completion review

Update `docs/NODE-PROFILES.md` and `docs/INSTALLED-TOOLS.md` with the implemented interfaces and transition limits.
Run the focused suites from every task and existing affected shell suites.
Run `just build-partial chat-shell,chat-mcp unit` for compiled runtime consumers.
The partial build does not replace full PR CI.
Run `drift check` and `git diff --check`.
Record exact commits, test failures and passes, skipped checks, and remaining temporary scan matches.
Request implementation review under CHAT-qdsmackk before the second plan consumes this work.
Do not install the foundation into a live home or mark extraction complete.
