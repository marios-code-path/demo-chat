# Profile contract proposal

Date: 2026-10-30.
Issue: CHAT-zjezvtwh.
Status: approved for planning, with the owner-required inventory correction applied.
Revision: 3. The owner requires no further design review for this correction.
Inspection basis: clean demo-chat master `1a8fd03383ccaa35b92c9e23f58b4a172d948301`.
CHAT-axqxlizl defines the settled ownership boundary.

## Input method

I recommend one environment variable: `CHAT_NODE_PROFILE`.
Its value is an absolute path to a readable, non-secret profile file.
General tools must not search the current directory, sibling repositories, or a node-9 installation for a profile.
Node-9 wrappers select their installed profile when the caller supplies none.
Node-9 wrappers retain their deployment isolation checks.

A profile-aware operation requires a profile.
A general command with complete existing explicit inputs can continue without a profile.
An explicitly selected, invalid profile always causes failure.
The loader validates profile inputs before starting Java, Docker, SSH, or installation changes.
Profile selection, parsing, schema, and required-value errors return status 2 from the profile loader.
The caller propagates that status before it invokes the governed tool.
This rule does not change existing command validation or subprocess exit statuses.

## Exit status scope

The status 2 rule covers new profile-loader errors only.
The alias readers separately return status 2 for conflicting legacy and neutral inputs.
Existing tool validation keeps its current statuses.
Wrappers must not convert an existing tool status 1 into status 2.

The following behavior remains unchanged at the inspection commit:

| Existing chat-build validation | Status |
| --- | --- |
| Core index or pubsub selection without Cassandra, line 1392. | 1. |
| Core vector and embedding pairing, line 1396. | 2. |
| JWK bake refusal, line 1443. | 1. |
| Agent mapping bake refusal, line 1454. | 1. |
| Index-root bake refusal, line 1523. | 1. |
| `parse_agent_jwks` refusals through `SystemExit` with a message. | 1. |

No existing validation site requires a status change under this revision.
The later plan must test profile errors separately from existing command refusals.

A `--profile PATH` argument is an alternative.
It is explicit, but it requires argument changes across builders, launchers, and nested calls.
Supporting both methods would add precedence rules without an identified need.

## File format

The proposed version is `NODE_PROFILE_VERSION=1`.
Each non-comment line contains one `KEY=value` assignment.
Values are literal text after the first equals sign.
The loader never sources or evaluates the file.
Blank lines and lines beginning with `#` are allowed.
Duplicate keys, unknown keys, invalid assignments, and unsupported versions cause failure.
Shell quotes, variable expansion, command substitution, and inline comments have no special meaning.
The loader validates each value against its key.

## Keys

| Keys | Purpose |
| --- | --- |
| `NODE_PROFILE_VERSION` | Format version. |
| `NODE_PROJECT`, `NODE_NETWORK` | Deployment project and container network. |
| `NODE_ID`, `NODE_KEY_TYPE` | Node identity and key type supplied to existing launch functions. |
| `NODE_RSOCKET_PORT`, `NODE_MGMT_PORT`, `NODE_REGISTRY_PORT` | Remote or direct service ports. |
| `NODE_IMAGE_REPO`, `CORE_IMAGE` | Image repository prefix and exact core image reference. |
| `NODE_VECTOR_PERSIST` | Persistence toggle, `on` or `off`. |
| `CASSANDRA_VERSION`, `REDIS_VERSION`, `CONSUL_VERSION` | Existing component versions. |
| `CHAT_EMBEDDING_OPENAI_BASE_URL`, `CHAT_EMBEDDING_OPENAI_MODEL`, `CHAT_EMBEDDING_IDENTITY` | Existing non-secret embedding settings. |
| `VECTOR_OWNER` | Existing vector directory owner value. |

The schema lists allowed keys.
Adding a profile key requires a demo-chat commit that accepts that key.
The deployment must pin that newer compatible commit before it supplies the new key.
An older pinned tool continues to reject the unknown key.
Each operation requires only the keys that it consumes.
Ports must be distinct integers from 1 through 65535.
Tunnel calculations must reject local port overflow.
Identity and key-type validation must preserve the compiled launch contract.
Authserv-specific inputs remain with CHAT-vnljyxpl.
This proposal does not choose new authserv deployment values.

## Precedence and mapping

An explicit command flag wins when the command already supports that input.
A neutral environment variable wins over its profile value.
A profile value supplies the remaining input.
Missing required values cause failure.
Empty required values cause failure.
The loader must distinguish an absent variable from an explicitly empty variable.

General tools map `NODE_ID` and `NODE_KEY_TYPE` to their existing launch arguments.
They map direct profile ports to `CORE_PORT`, `CORE_MGMT_PORT`, and the existing registry input.
Existing explicit connection inputs retain precedence for client connections.
Tunnel tools derive local ports from the remote profile ports and the selected offset.
They supply those local ports to client launchers.
Server deployment isolation checks still reject prohibited overrides.

Node-9 retains its fixed project, image, and remote-port checks in its repository.
General tools validate syntax and relationships without containing node-9 constants.

## Runtime options

The current builder fixes node ID `9`, key type `uuid`, and ports `6890` and `6891`.
The current installed launcher also supplies those port defaults.
The replacement builder must obtain identity and connection values from explicit inputs or the selected profile.
The installed launcher must reject a key type or node ID that conflicts with its frozen options.
Profile-dependent connection properties must use effective client ports, including tunnel offsets.
The design requires checking both JVM arguments and environment values.
Checking the exported ports alone cannot establish correct JVM properties.

## Compatibility

Two named readers own the alias rules at different migration states.
The temporary reader is demo-chat `shell-scripts/legacy-node-inputs.sh`.
The final reader is node-9 `bin/compat-inputs.sh`.
These components remain unimplemented.
The destination repository was empty at design approval.
General tool bodies consume neutral inputs.
Temporary compatibility entry points invoke the temporary reader before those tool bodies.
Final node-9 adapters invoke the final reader before the neutral demo-chat tools.

Each active reader owns these rules:

1. The reader distinguishes absent variables from explicitly empty variables.
2. The reader returns status 2 when corresponding legacy and neutral inputs have unequal values.
3. The reader accepts equal values.
4. The reader maps a legacy value when the neutral input is absent.
5. The reader applies profile defaults only after alias resolution.
6. The reader translates old profile keys as literal data before the neutral loader reads them.

The derived deployment root remains non-overridable.
The reader must not turn `NODE9_DIR` into a caller-controlled root.
The table below classifies aliases and derived values separately.

## Compatibility transition constraints

These constraints define compatibility states. They do not authorize implementation or file-move planning.

1. Demo-chat must provide the temporary reader before any neutral rename removes an existing legacy reader.
2. Every changed public entry point must invoke that reader while node-9 adapters are unavailable.
3. Installed compatibility entry points must include that reader in their copied dependency set.
4. Node-9 must provide its final reader and adapters before demo-chat removes temporary compatibility.
5. Installation must make those adapters available before it exposes neutral-only general executables through existing public paths.
6. A failed update must leave public paths connected to a compatible reader and tool set.

The first change therefore adds compatibility before removing old reads.
The second change supplies node-9 adapters before removing temporary demo-chat compatibility.
No supported intermediate state leaves legacy inputs without a reader.
Existing untouched installations retain their earlier scripts and require no automatic update.
Direct legacy use of demo-chat source entry points remains supported until the owner approves that entry-point transition.
Legacy inputs remain accepted through node-9 adapters after that transition.

The final demo-chat scan may pass only after temporary legacy readers and their deployment-specific fixtures leave demo-chat.
Intermediate scan matches must remain visible and must not receive a permanent exception.
The extraction cannot claim completion during that intermediate state.

| Legacy names | Neutral input or classification |
| --- | --- |
| `NODE9_PROJECT`, `NODE9_VECTOR_PERSIST` | `NODE_PROJECT`, `NODE_VECTOR_PERSIST`. |
| `NODE9_RSOCKET_PORT`, `NODE9_MGMT_PORT`, `NODE9_REGISTRY_PORT` | Corresponding `NODE_` port keys. |
| `NODE9_IMAGE_REPO` | `NODE_IMAGE_REPO`. This key occurs in the deployment profile. |
| `STUB_REQUIRE_NODE9_ENV` | `STUB_REQUIRE_PROFILE_ENV`, a test-stub control that requires deployment environment values. |
| `NODE9_HOME`, `NODE_9_BIN` | `CHAT_DEPLOYMENT_HOME`, `CHAT_DEPLOYMENT_BIN`, operational paths. |
| `NODE9_DIR` | Wrapper-derived deployment root. It remains non-overridable. |
| `NODE9_SECRETS_FILE`, `NODE9_SECRETS_DIR`, `NODE9_ANSIBLE_DIR` | Corresponding `CHAT_DEPLOYMENT_` paths. They are not profile values. |
| `NODE9_JAVA`, `NODE9_MVN` | `CHAT_BUILD_JAVA`, `CHAT_BUILD_MVN`, tool selection inputs. |
| `NODE9_JAVA_LIMIT` | `CHAT_JAVA_PROBE_LIMIT_S`, a probe timeout with test use. |
| `NODE9_ALLOW_SOURCE_TREE` | `CHAT_ALLOW_SOURCE_TREE`, an explicit source-use control with test use. |
| `NODE9_CHAT_BUILD` | `CHAT_BUILD_COMMAND`, a builder substitution used by tests. |
| `NODE9_INSTALL_FAIL_LIB_MOVE` | `CHAT_TEST_INSTALL_FAIL_LIB_MOVE`, a failure injection hook. |
| `NODE9_INSTALL_LEGACY_SHA256`, `NODE9_INSTALL_LEGACY_SIZE` | `CHAT_TEST_INSTALL_LEGACY_SHA256`, `CHAT_TEST_INSTALL_LEGACY_SIZE`, fixture overrides. |
| `NODE_9_REGISTER_TOPIC_NAME`, `NODE_9_REGISTER_TOPIC_ID` | `CHAT_REGISTER_TOPIC_NAME`, `CHAT_REGISTER_TOPIC_ID`, register selectors. |

Register selectors are operation inputs, not deployment profile keys.
This proposal does not add a second register implementation or a direct-backend workaround.
Credentials stay outside profiles and keep their existing explicit inputs.
Legacy aliases remain until the owner approves their removal.
Existing homes, paths, credentials, state directories, and MCP registrations require no automatic migration during design.
Public node-9 command adapters select the neutral profile and translate legacy inputs.
They invoke distinct general executables beneath `$prefix/tools/demo-chat/bin`.
General tools must not call the public adapter to locate themselves.

## Migration inventory correction

The historical count of 19 distinct names in 103 places is superseded.
Whole variable names comprise 18 `NODE9_` names in 103 occurrences and three `NODE_9_` names in 46 occurrences.
That total is 21 names and 149 occurrences across 16 files at `1a8fd033`.
The review treats this as a record correction, not a design defect.

A separate full-token scan of tracked `shell-scripts` files and `justfile` found 19 `NODE9_` names in 106 occurrences.
It also found three `NODE_9_` names in 46 occurrences.
That scan totals 22 names and 152 occurrences across 16 files.
Its pattern is `NODE9_[A-Z0-9_]+|NODE_9_[A-Z0-9_]+`.
It counts every token, including multiple tokens on one line and tokens in comments.
The scan uses Git revision `1a8fd033`, so nested worktrees and the node-9 subtree cannot enter its scope.
The additional substring is inside `STUB_REQUIRE_NODE9_ENV` at lines 76, 689, and 1131 of `shell-scripts/test-register-bootstrap.sh`.
It is not a variable named `NODE9_ENV`.
No tracked demo-chat line contains that whole variable name.
The revised table classifies the actual stub as `STUB_REQUIRE_PROFILE_ENV`.
The final case-insensitive scan must remove the old stub name without a permanent exception.
The two measurements count different things. Neither measurement replaces an inventory of changed responsibilities.
`NODE9_IMAGE_REPO` is an additional deployment-profile key outside that scan scope.

## Acceptance contract

- Two profile fixtures use the same general executable without deployment-specific code branches.
- The second fixture uses project `chat-node1001`, node ID `1001`, and ports `16890`, `16891`, and `16892`.
- Both fixtures exercise staging, direct client options, tunnel offsets, and dry-run image commands where applicable.
- Invalid profiles and conflicting aliases cause failure before an external operation.
- A profile containing shell substitution text executes no shell command.
- Node-9 isolation rejects changed fixed deployment values.
- Installed execution needs no checkout or Maven.
- Legacy wrapper inputs retain their previous meaning.
- Existing chat-build refusals retain their measured statuses when no profile-loader error occurs.
- Both alias readers pass the same absent, empty, equal, conflicting, and legacy-only input cases.
- Each supported migration state accepts legacy public invocations before and after a neutral rename.
- A failed installation preserves a public entry point with a compatible alias reader.
- A new profile key fails with the older pin and succeeds only with a compatible newer pin.

These are acceptance conditions, not test results.
Design approval permits implementation planning only.
Extraction changes and file moves require separate plan approval.
