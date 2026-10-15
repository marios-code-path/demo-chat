# Forward Register

This file carries the current state, the decisions that stay true, and the log
of the current period. `AGENTS.md` imports it, so it loads in every session.
**Its size is a cost on every turn, and it must stay under 64 KB.**

The dated sections of earlier periods live in archive logs. Nothing imports
those files, so they cost no context until a reader opens one. See
`## The archive`.

Nothing in this file is authoritative on its own. Each row points at the
artifact that is.

## Where things stand

| | |
|---|---|
| Checkout | `master` at `08a71e81`, the merge commit of PR #226, in sync with `origin`. The working tree holds two untracked owner files, `conversations.json` and `conversations.jsonl`. |
| Register state | Restructured on 2026-10-15 into this file and three period logs. Measured on 2026-10-15. |
| Last merged PR | #226, merge commit `08a71e81`, merged on 2026-10-15. |
| Merge strategy | **Merge commits only, since 2026-09-17.** Squash and rebase are both disabled at the repository. A tip with one parent is now worth questioning. |
| Local branches | Twenty-two, measured on 2026-10-15, `master` among them. **Fourteen others hold no commit that master lacks**, so master already holds their work: `boot4-bump` at `179c2fd8`, `chat-bmmtojqm-chat-log-view` at `925a1db8`, `chat-chsvdqbi-springai` at `aeb579dc`, `chat-eoqkbqve-access-expressions` at `c22f9018`, `chat-ivtnvgyg-reach-check` at `c2b5fe5d`, `chat-kcccoifk-dead-auth-manager` at `99483bca`, `chat-nxbtadan-register-64kb`, which is this work, `chat-qwmjrixq-jackson3modules` at `8c3acfce`, `chat-register-checkout-clause` at `38063a53`, `chat-sgyaaivp-cassandra-timeout` at `a812cd0c`, `chat-znprrzhn-enforcement` at `6970a89a`, `chat-znprrzhn-register-refresh` at `e22486b3`, `register-refresh-2026-10-02` at `730b61f1`, and `review-pr150` at `a0065fcb`. **Every branch ref is kept.** Seven hold a commit that master lacks, and the next row names them. |
| Unmerged branches | Seven, measured on 2026-10-15: `backup/chat-bmmtojqm-before-review` at `93cada30` (ahead 1), `chat-hazcatpc-review-fix` at `606d251a` (ahead 1), `chat-lswjobhz-shared-store-index` at `863b6d00` (ahead 2), `chat-mcp-spec` at `d29bac4a` (ahead 1), `chat-urhjrwbt-indexelastic` at `d8d797b3` (ahead 1), `chat-xcmpudyb-message-id` at `20f3d014` (ahead 1), and `chat-znprrzhn-composite-enforcement` at `91af1c96` (ahead 1). **The branch refs are kept.** `chat-lswjobhz-shared-store-index` is live work. |
| Worktrees | Five, measured on 2026-10-15. The main checkout, on `master`. `.worktrees/chat-nxbtadan-register-64kb`, which is this work. `.worktrees/chat-lswjobhz-shared-store-index` at `863b6d00`, which is live work. `.worktrees/chat-bmmtojqm-chat-log-view` at `925a1db8`, whose branch is merged. **It is kept.** And `/Users/darkbit1001/.codex/worktrees/7768/demo-chat` at `3e1c5682`, detached, which holds the pre-merge tip of PR #225. |
| Open PRs | None, measured on 2026-10-15. |
| In-progress issues | Eight, measured on 2026-10-15: `CHAT-owiksxvz`, `CHAT-ivtnvgyg`, `CHAT-aqpcacwv`, `CHAT-emwhnlay`, `CHAT-lswjobhz`, `CHAT-uxlstrdy`, `CHAT-pphwooqm`, and `CHAT-nxbtadan`. |

**The `Checkout` row records the last substantive merge before the refresh.**
A register refresh is a narrow documentation update, so it does not move the
row. **That lag is not drift, and a reader must not correct it.** A row that
chased the true tip would go stale the moment it merged, and it would spawn a
refresh on every refresh. The owner set this rule on 2026-09-29.

**Two of the seven unmerged branches carry no lost work.**
`chat-xcmpudyb-message-id` at `20f3d014` holds a register correction that PR
#188 already carried as `0bdd8684`, and `git cherry master` marks it `-`.
`backup/chat-bmmtojqm-before-review` at `93cada30` holds a chat log commit
before its review repairs, and PR #186 merged the repaired version.

**Four belong to other work.** `chat-hazcatpc-review-fix`, `chat-mcp-spec` and
`chat-znprrzhn-composite-enforcement` each hold one commit that master lacks,
and `chat-urhjrwbt-indexelastic` holds the module repair that PR #106
superseded. **The `topic-by-name` branch is gone**, and PR #163 carried its
work into master.

**One branch on the remote has no local ref.** `origin` holds
`chat-eroapfub-vector-runtime-flags` at `3245d308`, titled "build: add Java 25
vector runtime flags". That work landed by another route, so only the branch
ref remains. `origin` also still holds `chat-xcmpudyb-message-id`, and PR #187
merged it on 2026-10-05.

## Landed

| PR | What |
|----|------|
| #44 | Key-type binding at the REST boundary, plus the `Key` equality contract it exposed |
| #45 | Build health re-verified against current master, all three verifier modes |
| #46 | Why `chat-shell` reports 17 skipped under `-Pintegration` |
| #47 | The redis backend made into a composition root that actually starts |
| #48 | The dead JSON wrapper dropped from `User`, `MessageTopic`, and `TopicMembership` |
| #49 | The dead JSON wrapper dropped from the seven E2EE types in `EncryptedEnvelope.kt` |
| #50 | The forward register was refreshed after PR #49 |
| #51 | `app.nodeid` became explicit, required, and validated |
| #52 | The GraalVM sandbox self-attach trap was recorded |
| #53 | Redis and Cassandra now enforce `app.nodeid` uniqueness with a store-side lease |
| #54 | CI integration job runs the container tests, node id for the test image, and the repackage default fix (B6 profile coupling) |
| #56 | BUILD-HEALTH doc records the CI integration job |
| #57 | B7: memory deployment compile scope fix, launch skips test compilation, BUILD.md records the installed-artifact rule |
| #58 | `just launch-shell` and `just dry-run-shell` recipes for the interactive client |
| #59 | B8: shell `send` by topic name uses the looked-up room id, plus the first end-to-end send tests. B9: pubsub provider beans made singletons |
| #60 | Shell `hangup` disposes the stored listener; `getRoomByName` and `leaveRoom` fail loudly with `NotFound`. Fallback placement before `single()` corrected. Six new tests, composite and container level |
| #61 | A topic name names one room. `addRoom` rejects an existing name with `DuplicateException` at the source. Composite and shell tests pin the rejection |

## Decisions carried forward

Nine, from the capability design. Full reasoning in
`docs/superpowers/specs/2026-08-24-capability-composition-design.md`.

1. One classpath carrying every provider; selection at launch, not at build
2. Strict validation — unset, unknown or uncovered fails at startup
3. Providers declare coverage by annotation, read from bytecode
4. `rsocket` is a capability value, not a second selector family
5. At most one provider per capability, enforced before the context starts
6. Compositions are partial and mix-and-match
7. Clients discover by structured query; derived names are the override path
8. A partition is a data domain; one partition per instance
9. The store carries a keyType and partition stamp, verified at startup

Two constraints that shaped these and are easy to lose:

- **`nodeId` is host identity, not partition.** It was briefly modelled as a shard
  identity and is not one.
- **One store can be reached by deployments that do not share a registry.** This is
  why decision 9 exists: any check living in consul is blind to the other side.

## Planned, not started

`docs/superpowers/plans/2026-08-24-capability-mechanism.md` — spec migration steps
1 to 4, seven tasks, each ending in a commit and a releasable tree. Nothing is
deleted, no image changes, no selector renamed.

Execution approach was not chosen. The offer on the table was subagent-driven
(fresh agent per task, review between) or inline via executing-plans.

**Spec steps 5 to 7 have no plan yet** and deliberately so: each breaks something
outside this repository's control — the per-backend images collapse, every launch
must name all five capabilities, and `app.client.rsocket.core.*` is a breaking
rename with no alias period.

## Deferred to issues

| Issue | State | What |
|-------|-------|------|
| `CHAT-cikgeefc` | todo | Build health verification. Standing tracker, deliberately left open — a cycle finding no drift is a clean reading, not a finished task. |

`CHAT-koufkrsl` and its child `CHAT-wyssrokr` are done. PR #51 removed
derivation and made `app.nodeid` explicit. PR #53 added the store-side claim
lease. No open child remains under `CHAT-koufkrsl`.

## Housekeeping

- **`CHAT-ubmrxyqo` is still `in-progress`.** Its last open item was closed in PR
  #47 and the evidence is logged on the issue, but the status was never moved to
  `done`. It should be.
- **`CHAT-uortzsbx` is done** as of 2026-08-29. B5 closed with PR #54 and #56.
  The one open thread it leaves is the 10-run gating decision, recorded on the
  issue and in the CI integration execution section.
- **The capability composition branch is merged.** The spec and first plan are on
  `master`. Capability steps 5 to 7 still have no implementation plan.

## Claims that are load-bearing and unverified

The point of this section: each of these is currently believed on the strength of
reading configuration or source, not on the strength of having run it. Anything
built on top of them inherits the risk.

1. **`instance-id` overwrite.** `shared-deploy-configuration/src/main/config/server-rsocket-consul.yml`
   sets `instance-id: "${spring.application.name}"`, a constant. The conclusion drawn
   is that a second process re-registers the same id and overwrites the first rather
   than joining as a second instance. This was read from config, never observed.
   Spec step 7 rests on it.
2. **Task 7 of the plan is unbounded.** Removing `matchIfMissing` breaks every test
   that relied on the implicit memory default. Which tests those are cannot be
   enumerated without running it; the plan says to let `build-health.sh` name them.
   The size of that task is genuinely unknown.
3. **`DOCKER_HOST` is unset on this machine** and `chat-deploy-memory-integration-test`
   passes it as the docker host, falling back to the default socket. A machine with a
   stale `DOCKER_HOST` set would behave differently. Recorded on `CHAT-wovtjjoq`.
4. **The capability matrix in the spec was assembled by grep**, not by running
   anything. Making it verifiable is Task 5 of the plan — the agreement test is what
   turns it from a reading into a checked fact.

## Things worth not relearning

- **Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone.** A
  single-module run resolves `chat-core` from `~/.m2` and reports failures that are
  not real. This cost a false debugging detour in the redis work.
- **`chat-shell` reporting 23 skipped under `-Pintegration` is not missing
  coverage.** `@Disabled` sits on the generic base classes, surefire counts them as
  test classes, and JUnit does not inherit `@Disabled`, so the concrete `Long*`
  subclasses run. **Measured on 2026-10-02: 64 tests with 25 skipped, so 39 run.**
  The count was 48 with 23 skipped on 2026-09-15, and 36 with 17 before the vector
  work. Documented in `docs/BUILD-HEALTH.md`.
- **A wire-format change makes the shell integration image stale.** The
  chat-shell tests run the client against the
  `chat-deploy-long-memory-integration-test` Docker image, not against the
  reactor. After a serialization change, run
  `build-health.sh --ci`, which builds the image and then runs the container
  tests in one reactor. `--integration` alone does not build the image, and it
  prints a note that says so. A stale image caused 8 decode errors that looked
  like a code regression.
- **A test server that binds the wildcard address can share a port with an
  IntelliJ listener, and IntelliJ then receives the connections.**
  `chat-client-rsocket/src/test/resources/application.properties` set
  `spring.rsocket.server.port=0`, so the kernel picked the port. Java sets
  `SO_REUSEADDR` on a server socket, and macOS allows a wildcard bind on a port
  that a `127.0.0.1` listener holds. The loopback listener wins that address.
  The server logs `started on port <p>`, and the client connects to
  `localhost`. The test then fails with `ClosedChannelException` after a 45 s
  wait, and `lsof` shows the test server's listener accepting nothing. Two
  occurrences: 63342 on 2026-10-07, and 64342 on 2026-10-15. **The test server
  binds loopback alone now, and that closes the trap.** A loopback bind on a
  held loopback port fails with `EADDRINUSE`, and a kernel pick avoids such a
  port. Run `lsof -nP -iTCP:<p> -sTCP:LISTEN` before you read such a failure as
  a regression. `CHAT-ivtnvgyg` holds it.
- **A stale compiled test class outlives its source across a branch switch.**
  `DomainWireShapeTests.class` stayed in `chat-core/target/test-classes` after a
  checkout that removed its source. Surefire runs compiled classes, not sources.
  So it ran the orphan class and reported 3 failures against source that was not
  on the branch. Run `mvn -o -pl chat-core clean test` after a branch switch. This
  is the same shape as the stale integration image trap above. The build artifact
  outlived the source.
- **`app.nodeid` has no default and no derivation.** It takes an integer in
  0..1023, and 0 is a legal explicit value. The MAC derivation is gone, because it
  collided for certain across containers that share an IP. `chat-build --node-id`
  is the normal launch path, and the argument is required. Do not add a shared
  `app.nodeid` value to deployment yml. A committed default would make every
  deployment the same node in silence, which is the failure this change removed.
  Tests can use `app.nodeid=1` where the deployment claims nothing. Uniqueness
  across deployments is now enforced by a store-side lease. See the section
  `Node id claim lease (2026-08-28)` in `forward-register-archive-2026-08.md`.
- **A sandboxed build run can fail on agent self-attach, not on the code.**
  Mockito loads the Byte Buddy agent into the running JVM. A sandbox can block that
  self-attach on a GraalVM JVM, and `build-health.sh` then fails for a reason that
  has nothing to do with the change under test. The same check passes unsandboxed.
  Confirm a failure outside the sandbox before you treat it as a regression. The
  build already prints the related warning on every run: "A Java agent has been
  loaded dynamically" and "Dynamic loading of agents will be disallowed by default
  in a future release".
- **A disabled boot test is how a backend rots unnoticed.** `RedisDeployBootTests`
  was `@Disabled` with an accurate comment explaining why, and the backend stayed
  broken behind it. Every composition gets a boot test in the plan, and they stay
  enabled.
- **Start with the global build surface.** `docs/BUILD.md` is the human entry
  point for repo-level build and launch commands. `just --list` shows the short
  command menu. Maven remains the build system. `shell-scripts/` remains the
  implementation layer and the advanced launch reference.

## The archive

The dated sections before 2026-10-08 live in period logs, verbatim:

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-07.

Nothing imports those files, so they cost no context until a reader opens one.

**A period closes when this file would pass 64 KB.** The oldest dated sections
then move to their period log, and this file keeps the rest. The log of the
current period stays here, because a reader needs recent context without
opening an archive.

**`shell-scripts/build-health.sh` fails when this file passes 65,536 bytes.**
A size rule with no check drifts back, so the limit is measured on every pull
request.

## The action grant policy model (2026-10-08)

`CHAT-xdsetfkf`. Branch `chat-xdsetfkf-action-grant-model`. **This work is not
merged.** Spec: `docs/superpowers/specs/2026-10-02-action-grant-policy-model-design.md`.
**Merged since: PR #199, `b8d14c71`, on 2026-10-09.**

### What exists

- A typed model in `chat-core`, package
  `com.demo.chat.service.security.policy`. `GrantPolicy.intents` returns
  `GrantIntent` values. It reads no store and writes no row.
- `ShippedGrantPolicies` states the shipped writers as three policies:
  `roomowner`, `roomjoin`, and `roomleave`.
- **No production code reads the model.** `ContextRoomOwnerGrant` and
  `MembershipGrant` did not change. `CHAT-qojwcatx` moves them onto it.

### Rules that are easy to lose

- **`NONE` and `NOW` are policy values, not stored values.** The shipped
  writers store `NONE` as `0` and `NOW` as the clock time of the leave.
- **`ACTIVE` and `ROOT` are runtime sources.** Configuration does not accept
  them.
- **The constructor refuses `*` outside the room owner rule.** So the draft
  close row is refused. A close policy needs an owner decision first.
- **A `GrantIntent` holds no key.** The writer selects the grant row key.

### Measured

- `GrantPolicyTests` 15 of 15 in `chat-core`.
- `ShippedGrantPolicyTests` 8 of 8 in `chat-security`. Each test runs the
  production writer against a map store, and compares the rows with the
  policy intents.
- `MembershipGrantTests` 9 and `RoomOwnerGrantTests` 8 pass with no change.
- Three mutations of `ShippedGrantPolicies`, each run alone. Removing the join
  `SUBSCRIBE` rule, giving the leave `NONE`, and granting the `User` root on a
  join each failed exactly its own comparison test.
- `build-health.sh` default mode: exit 0, 30 modules, 1839 tests, 0 failures,
  0 errors, 41 skipped, and no drift. `drift check` reports ok, and
  `git diff --check` exits 0.

## The action grant policy execution (2026-10-08)

`CHAT-qojwcatx`. Branch `chat-qojwcatx-policy-execution`, stacked on PR #199.
**This work is not merged.** Plan:
`docs/superpowers/plans/2026-10-08-action-grant-policy-execution.md`.
**Merged since: PR #200, `d0b5eee4`, on 2026-10-09.** PR #199 merged first,
so PR #200 merged against `master`.

### What changed

- `PolicyGrantWriter` in `chat-security` maps policy intents to `AuthMetadata`
  rows. It holds the storage rules that `MembershipGrant` held before.
- `ContextRoomOwnerGrant` applies `roomowner`. `MembershipGrant` applies
  `roomjoin` and `roomleave`. Both constructors keep their parameters.
- `GrantPolicyConfiguration` registers one writer, and it enables
  `ShippedGrantPolicies.ALL` alone. **A policy that is not in that list has no
  effect.**
- `TopicServiceImpl` did not change. It calls the two ports, as before.

### Rules that are easy to lose

- **The member of a join is the active principal, not the caller.** A caller
  that holds `JOIN` on another member can join that member.
- **Each new row mints its own `AUTH_METADATA` key.** A target key never
  becomes a grant row key.
- **A new `*` row still meets the owner guard.** The writer calls
  `AuthorizationService.authorize`.
- **One unreachable edge case changed.** An expired owner row of the same
  caller is now set to never expire in place. Before, that case wrote a second
  row. A new room key has no stored row, so `addRoom` never meets it.
- **No authorization topic exists.** The writer does not need one.

### Measured

- `PolicyGrantWriterTests` 11 of 11.
- The five named tests pass with no change to their assertions:
  `AnonymousAuthorizationMatrixTests` 40, `RoomOwnerGrantTests` 8,
  `MembershipGrantTests` 9, `TopicServiceMemberGrantTests` 6, and
  `StandardUserJoinSendTests` 3. The three grant wiring boot tests pass too.
- Three mutations of `PolicyGrantWriter`, each run alone. Removing the
  enablement check failed the two enablement tests. Using the target key as the
  row key failed the key root test and eight row tests. Writing `0` on a leave
  failed four leave tests.
- `build-health.sh --ci`: exit 0, 30 modules, 2193 tests, 0 failures,
  0 errors, 76 skipped, no drift, and `agent http gate: ok`. The run used an
  empty `DOCKER_CONFIG`. The image id moved from `36ac0b967c84` to
  `5d334eed0cfa`. `docker events` reported no OOM kill in the run window.
- `drift check` reports ok after a reviewed relink of
  `docs/ANONYMOUS-AUTHORIZATION.md`. Its bound seed passage did not change.

## The message import route (2026-10-09)

`CHAT-ugsreefu`. The implementation is in the worktree branch
`chat-ugsreefu-message-import`. It is not committed.
**Merged since: PR #202, `02baae05`, on 2026-10-09.** The merge has two
parents, and its tree equals the branch tip `e789acaa`. `CHAT-ugsreefu` is
done, and `CHAT-emwhnlay` is unblocked.

### Contract

- The composite RSocket route is `message.message-import`. It accepts only an
  Admin caller.
- The route preserves the supplied registered user and the supplied time.
  `Anon` is not a valid imported sender.
- The route uses `import:<source-message-id>` as the command request ID.
  History matching also prevents a repeat with a new request ID.
- The history match uses sender, millisecond time, and text. This remains a
  read-before-write check and does not prevent concurrent duplicate imports.
- Publication is disabled by default. The `publish` flag enables the `U`
  obligation. The caller does not report success for `PENDING` or `INCOMPLETE`.
- History is ordered by time and then by the required type-aware key
  comparison. Normal message times also truncate to milliseconds.

### Evidence and limits

- Memory route tests cover ordered import, restart-style history repeat,
  Admin access, anonymous access, `Anon` sender refusal, unknown keys, and
  future time.
- Cassandra ordering coverage is added to `CassandraMessageSendTests` for
  long and UUID deployments.
- The Cassandra test was not run in the local review. Container availability
  and the measured result remain pending.
  **CI measured it since.** Run `37976198743` on `e789acaa`: the
  `integration` job ran `CassandraMessageSendTests` with 4 tests and
  0 failures.
- `docs/MESSAGE-COMMANDS.md` and this register describe the new route.

## MCP Stage 1 messaging on the implementation branch, 2026-10-09

Issue: `CHAT-teujorxl`. Branch: `chat-teujorxl-mcp-messaging`.
This section records branch work, not a merge.
**Merged since: PR #205, `8965daa0`, on 2026-10-09.** `CHAT-teujorxl` is
done. The worktree and the branch are gone. PR #206 then corrected the MCP
records in `docs/BUILD-HEALTH.md` and the plan, under `CHAT-ppyxzpqo`.
The accepted messaging spec is `docs/superpowers/specs/2026-10-09-mcp-messaging-design.md`.
The execution plan is `docs/superpowers/plans/2026-10-09-mcp-messaging.md`.

The adapter exposes five read tools by default and adds sending with `enableSend=true`.
Message tools use the existing submit and status routes.
Request identity belongs to the authenticated agent across its adapters and rooms.
Stage 1 retry safety ends when the server process restarts.
The default `P,I` requirement does not prove vector completion or recipient delivery.

The adapter checks every returned message's room.
This check is necessary because the shipped message-by-ID route permits reads from any authenticated agent.
**Read that sentence as of 2026-10-09.** The server checks `SUBSCRIBE` on the
room of a message read by ID since `CHAT-erkoxybm`, so the adapter check is a
second guard now.
Room setup requires creation or `PUT /topic/join/{id}`, followed by successful history and submission probes.
The agent has no grants-read route.
Operator schemas, error metadata, and limits are in `docs/MCP-ADAPTER.md`.

Task 6 is committed at `1b5e73ff`.
Follow-up `e2c77a7d` also tests a changed room under the same request identity.
Its six authenticated MCP deployment tests pass without failures, errors, or skips.
The agent HTTP gate also passes two identity tests and seven relay tests.
The REST run emitted no Surefire shutdown warning.
`CHAT-gsddauhn` holds the separate full REST-profile failure.
The earlier full RSocket-profile shutdown warning has no measured cause.

The default gate ran 2,079 tests in 30 modules, with zero failures, zero errors, and 47 skips.
The `--ci` gate ran 2,450 tests in 30 modules, with zero failures, zero errors, and 82 skips.
Both gates exit 0 and report no drift.
The `--ci` run used an empty temporary `DOCKER_CONFIG`, after the first run failed in `build-image`.
The six `McpMessagingDeploymentTests` tests skip in the reactor, so the `--ci` skip count rose from 76 to 82.
Surefire printed its shutdown deadline in `chat-client-rsocket` and `chat-deploy-memory` during the default gate.

Final review found that the send tool declared `idempotentHint=true`, against the spec.
Task 7 sets it to `false`, because retry safety ends when the server process restarts.
The full gates measured the tree before that one-value change.
After it, the 237 `chat-mcp` tests and the 15 agent HTTP gate tests passed.

Kafka durability, replication, search, and live subscriptions remain outside this milestone.
The Admin-only import route of `CHAT-ugsreefu` merged separately as PR #202, as recorded above.
MCP exposes no import tool and no sender override.
This branch merged `origin/master` at `23f8f3a0` before its pull request.

## One Cassandra test container per JVM (2026-10-09)

`CHAT-znodyvcc`. Branch `chat-znodyvcc-cassandra-nonode`. **This work is not
merged.** `docs/BUILD-HEALTH.md` holds the measurements, under
`Cassandra test containers and Docker memory`.
**Merged since: PR #207, `867d614e`, on 2026-10-09.** `CHAT-znodyvcc` is done.

### The cause, reproduced

- `CassandraTestContainerConfiguration` started one container for each Spring
  test context. `chat-persistence-cassandra` has 10 contexts, so 10 containers
  ran at once, about 15 GiB on a 16 GiB Docker VM. `chat-index-cassandra`
  reuses that configuration through the test jar.
- Each container sized its heap from the VM: `-Xmx3930M`.
- Baseline master passed alone. With a 5 GiB ballast container on the VM, it
  failed with 5 `NoNodeAvailableException` errors, the signature of the issue,
  and Docker reported 3 out-of-memory events. **The issue recorded no
  out-of-memory event for its own failing run.** So the reproduction matches
  the error signature, and not that one detail.

### What changed

- `SharedCassandraContainer` starts one container for each test JVM, with both
  keyspaces. The `embeddedCassandra` bean returns it, with an empty destroy
  method, because Spring infers `close` on an `AutoCloseable` bean.
- Both Cassandra test containers set `MAX_HEAP_SIZE=1G` and
  `HEAP_NEWSIZE=256M`. `withReuse(true)` is removed. It had no effect, because
  `~/.testcontainers.properties` does not enable reuse.
- `NodeClaimTableProbeTests` claims node ids 210 to 212. It claimed 201 to 203
  before, and its live lease on 201 failed
  `CassandraNodeIdClaimStoreTests` once both shared one keyspace.

### Measured

- Under a 5 GiB ballast: baseline 5 errors, the change 0 errors, with 1
  container at peak and no out-of-memory event.
- Without ballast, the run-3 shape of the issue on the change: every module
  passed, with 1 Cassandra container at peak. `chat-persistence-cassandra`
  took 5:51, against 7:15 on baseline.
- `CassandraStoreShapeCheckTests` still takes about 285 s. `CHAT-pggtduxz`
  holds it. **Since `CHAT-pggtduxz`, the class takes 80.5 s.** See the next
  section.

## The shape check tests build small keyspaces (2026-10-10)

`CHAT-pggtduxz`. Branch `chat-pggtduxz-shape-check-speed`. **This work is not
merged.** Test code only.
**Merged since: PR #208, `dfdf7b2d`, on 2026-10-09.** **`CHAT-pggtduxz` is
still `todo`, measured on 2026-10-09.** Its close step did not run.

- `CassandraStoreShapeCheckTests` built a full keyspace from
  `keyspace-long.cql` in 11 of its 12 tests, about 26 schema statements each,
  and dropped none of them.
- **Only `a complete keyspace passes` builds the full keyspace now.** It proves
  that the real script satisfies the full required map.
- Each refusal case builds the tables it names, from the same script
  statements, and limits the required map to those tables. A table name that
  matches no statement fails the test.
- **Each refusal case first passes the check**, so the removed element alone
  causes the refusal. The `TIMESTAMP` case passes with the script type before
  it gets the old type.
- Each test drops its keyspace. The module shares one Cassandra container since
  `CHAT-znodyvcc`.

**The check evaluates each required table and column on its own.**
`ColumnShapeCheck` reads that way, so a smaller required map gives the same
verdict for the removed element.

### Measured

- The class alone, with `-Pintegration`: 296.6 s before, 80.5 s after.
  12 tests, 0 failures.
- Three mutations, each run alone and restored from git. The check ignores
  the `root` column: only the `keys.root` case fails. The small keyspaces get
  no table: the 10 cases with a pass check fail at it. The type check is off:
  only the `TIMESTAMP` case fails.
- `chat-persistence-cassandra` alone: 2:17, against 5:51 after `CHAT-znodyvcc`
  and 7:15 before it. 131 tests, 0 failures.
- `build-health.sh --ci` with an empty `DOCKER_CONFIG`: exit 0, 30 modules,
  2460 tests, 0 failures, 0 errors, 82 skipped, agent HTTP gate ok, and no
  drift. The class took 71.1 s in that run. The shell image moved from
  `033f847c8150` to `c15e101bac92`, and Docker reported no OOM event.

## Merges from PR #203 to PR #209 (2026-10-09)

Each merge has two parents. Measured on 2026-10-09 with
`git rev-list --parents` and `git diff <tip> <merge>`.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #203 | `23f8f3a0` | `3ada260a` | `CHAT-ukiwartp` | Refresh the forward register after PR #202 |
| #204 | `d8e8c8ee` | `c93e94a3` | `CHAT-udhhlrrb` | Wait for `P` to succeed before case 21 reads the message |
| #205 | `8965daa0` | `748a1348` | `CHAT-teujorxl` | MCP Stage 1 messaging: history, message read, submit, and command status |
| #206 | `4ad96a27` | `a2357141` | `CHAT-ppyxzpqo` | Correct the MCP completion and gate records |
| #207 | `867d614e` | `889cc05f` | `CHAT-znodyvcc` | One Cassandra test container per JVM, with a capped heap |
| #208 | `dfdf7b2d` | `3478bc91` | `CHAT-pggtduxz` | Small keyspaces in the store shape check tests |
| #209 | `69dbd2ee` | `94a280fd` | `CHAT-okcfryup` | The reindex test job list is safe for a concurrent reader |

**Five merge trees equal their tips. Two differ, and each difference is
expected.**

- The PR #205 tree differs from `748a1348` in two test files:
  `MessageCommandServiceTests.kt` and `MessagingStack.kt`. PR #204 changed
  exactly those two files, and the branch merged `origin/master` before #204.
- The PR #207 tree differs from `889cc05f` in two files:
  `docs/BUILD-HEALTH.md` and the MCP messaging plan. PR #206 changed exactly
  those two files, and the branch was cut before #206.

### The concurrent job list read (2026-10-09)

`CHAT-okcfryup`. PR #209. `FakeJobStore.written` in
`MessageReindexServiceImplTests` was an `ArrayList`. The reindex run appends to
it on its own thread, and `awaitDurableOutcome` iterates it on the
`Flux.interval` thread. An append during that iteration raised
`ConcurrentModificationException` in a default `build-health.sh` run on
2026-10-07.

**The reported line 758 is past the end of the 670-line file.** So it is the
Kotlin inline line of `lastOrNull`, and that agrees with the race. The list is
a `CopyOnWriteArrayList` now. Test code only.

**The failure was not reproduced before the change.** The class passed 11 runs
of 11 after it, with 23 tests each. Those passes do not prove the race is
closed. A later failure of this test reopens the issue.

### Open after these merges

- **`CHAT-pggtduxz` closed.** PR #208 merged its work, and the issue is `done`
  as of 2026-10-11. The owner closed it.
- **`CHAT-gsddauhn` is still `todo`.** It holds the full REST-profile failure
  that the MCP section names.

## Message pointers (2026-10-10)

`CHAT-erkoxybm`. Branch `chat-erkoxybm-message-pointer`. **This work is
merged.** PR #213 merged on 2026-10-11 as `dc116ea7`, a merge commit with two
parents, `3e8d8b03` and `5c24e6e9`. The merge tree is byte-identical to the
branch tip `5c24e6e9`, because `git diff 5c24e6e9 dc116ea7` was empty. The
branch is deleted, local and remote. Spec:
`docs/superpowers/specs/2026-10-10-message-pointer-design.md`. Plan:
`docs/superpowers/plans/2026-10-10-message-pointer.md`.

A pointer is a message that names one message or one topic. It carries no
text. It enters through a new `pointer` operation on `ChatMessageService`.

### The eleven owner decisions

1. A pointer lives in a host room. `key.from` is the creator and `key.dest` is
   the host room. The field `target` names the target.
2. `FORWARD` is a new permission on the host room. It is not a domain and it
   has no root key.
3. The creator must read the target. An unreadable target answers the same
   error as a missing target.
4. A pointer uses the backends `P`, `I` and `U`. It never uses `V`, so recall
   never returns it.
5. One nullable `kind` column marks a pointer row. It holds the target domain
   name.
6. A pointer to a pointer is refused at creation. No stored chain can exist.
7. No reader fetches the target. A reader shows the target domain and id.
8. `messageById` checks `SUBSCRIBE` on the `dest` of the returned message.
   **The rule applies to every message, not to pointers alone.**
9. A topic target uses `SUBSCRIBE`. It does not use `GET`.
10. Tests cover the persistence stores, the creation checks, the `messageById`
    rule, and one MCP decoder test. Routes, the index and the shell get no
    pointer test.
11. The message value type stays `String`. A pointer is `Message<T, String>`
    with `data` of `""` and a separate `target`.

**Decision 11 replaced a decision of the same morning.** That one bound the
value type to `Any`, and it needed 206 test sites in 96 test files.

### The `messageById` change

`messageById` reads the message, and then checks `SUBSCRIBE` on its room.
**This is a behavior change for every message.** The `{Anon, Message, GET}`
row left `userinit.yml`. A caller that reads a message by id from a room it
did not join answers 403.

The two `GET` sites moved together: the RSocket composite and the REST
mapping. `MessageByIdRoomRuleTests` and `MessageByIdRestRuleTests` pin them.

**One existing test asserted the old behavior.**
`McpMessagingDeploymentTests` read a message of another room and expected 200
with the text. It expects 403 now, and it asserts that the text is absent.
Commit `9be6e705`.

### Mutations, each measured

| Task | Mutation | Measured result |
|---|---|---|
| 3 | Drop the `kind` write | The four Cassandra pointer round trips failed alone |
| 7 | Permit every read (`permitAll`) | The REST deny, the RSocket outsider read and the expression pin failed |
| 8 | Drop the `FORWARD` check | Only its own creation cases failed |
| 8 | Drop the target read check | Only its own creation cases failed |
| 8 | Admit a pointer to a pointer | Only its own creation cases failed |
| 8 | Read the registry instead of the topic store | Only its own creation cases failed |
| 10 | Drop the pointer branch of the MCP decoder | Only the MCP decoder test failed |

### The gates

Measured on 2026-10-10 at the branch tip, before the documents commit.

- Default mode: exit 0, 30 modules, 2104 tests, 0 failures, 0 errors, 47
  skipped, and no drift.
- `--ci` mode: exit 0, 30 modules, 2486 tests, 0 failures, 0 errors, 82
  skipped, no drift, and `agent http gate: ok`. The run used an empty
  `DOCKER_CONFIG`.
- The shell test image is `sha256:f90664f4`. Its `LastTagTime` is
  `2026-10-11 03:23:57 UTC`, which is inside the run window, so the run
  rebuilt it. **The id before the run was not captured**, so this reading does
  not prove a change of id. It proves a rebuild.
- The agent HTTP gate ran three classes with 0 skipped:
  `RestAgentSelectionTests` 2, `McpMessagingDeploymentTests` 6, and
  `RestToCoreBearerDeploymentTests` 7.
- `docker events` reported no OOM kill in the run window.

**`McpMessagingDeploymentTests` runs only under `expose-webflux`**, so the
reactor skips all six of its tests. That is why the `--ci` skip count is 82
and not 76.

### Risks that stay open

- **The routes, the index and the shell have no pointer test.** A defect there
  reaches `master` unseen. The owner accepted this on 2026-10-10.
- **The Redis and Kafka pub/sub codecs have no pointer test.** A pointer that
  does not serialize there fails `U` after `P` stores it. The room history
  still shows it.
- **A reader that ignores the type shows a pointer as an empty message.** The
  pointer `data` is `""`. The shell, REST and MCP readers of this repository
  each branch on `PointerMessage`. An outside client may not.
- **The `messageById` change refuses a reader that worked before.** A client
  that reads a message by id from a room it did not join answers 403.

### The removed pointer row (2026-10-11)

The review of PR #213 found one defect. **No read of a live pointer changed.**

`MessagePersistenceCassandra.message` rebuilt every pointer with a live flag.
The Cassandra removal keeps the row and clears the flag, so a removed pointer
read back as a live record. `MessagingServiceImpl.pointerTarget` filters on
`record`, so its filter could not reject that row. The call then read a live
pointer and refused it with `a pointer cannot target a pointer` rather than
`no such message`.

`PointerMessage` carries `record` as a constructor field now, with a default of
`true`. The default keeps every existing construction site and the pinned wire
JSON unchanged. **Cassandra is the only backend with a soft removal**, so it is
the only backend that read a removed pointer back.

Three readings carry the evidence.

- `PointerStoreTestBase` gains `a removed pointer does not read back as a live
  record`. It runs on the memory, Redis, Cassandra long and Cassandra uuid
  stores.
- `CassandraMessagePointerTests` is new, at node id 29. It measures the refusal
  text through the composite service on a Cassandra deployment.
- **The mutation, run alone, reproduces the review text.** A mapper that drops
  `row.record` fails the store test in both Cassandra key types, and fails the
  composite test at its refusal assertion with the measured text
  `a pointer cannot target a pointer`.

**The composite test was reordered for that reading.** Its flag assertion ran
before the refusal, so the mutation tripped the flag and masked the consequence
the reviewer named. The refusal is asserted first now.

Both gates pass on the repaired tree. Default mode: 30 modules, 2105 tests, 0
failures, 0 errors, 47 skipped. `--ci` mode: 30 modules, 2491 tests, 0 failures,
0 errors, 82 skipped, `agent http gate: ok`, and no drift.

### The flag crosses the JSON boundary (2026-10-11)

A second review found the same flag lost at the wire. **The store fix alone did
not close the defect for a client.**

Both pointer deserializers built `PointerMessage(key, target)` and took the
constructor default of `true`. The sibling `MessageDeserializer` and
`Jackson3MessageDeserializer` already read `node.get("record")`, and the two
pointer ones did not. So a Cassandra read returned a removed pointer with
`record = false`, and a client that decoded that answer saw a live pointer.

Both deserializers read the flag now. **The serializer side needed no change**,
and the wire tests prove it: the flag is on the wire, and both `true` and
`false` survive a round trip.

`PointerWireShapeTests` is new, with six cases. Two decode a removed pointer,
two decode a live one, and two assert the wire carries the flag. **Both
generations need their own reading**, because each one holds its own pointer
deserializer. Its Jackson 2 mapper is built from `Jackson2ObjectMapperBuilder`,
as `Jackson2MapperConfiguration` builds the deployment mapper. **A plain
`ObjectMapper` cannot serialize a `MessageKey`**, because the timestamp needs
the JavaTime module that builder installs.

Four construction sites exist in main source, and each is now sound. Two
deserializers read the wire flag, the Cassandra mapper reads `row.record`, and
`MemoryDomainCommandBus` makes a new pointer, which is live by definition.

The mutation, run alone, fails exactly the two removed-pointer cases, in both
generations.

Both gates pass again. Default mode: 30 modules, 2111 tests, 0 failures, 0
errors, 47 skipped. `--ci` mode: 30 modules, 2497 tests, 0 failures, 0 errors,
82 skipped, `agent http gate: ok`, and no drift.

**The `Checkout` row of this file is not moved by this section.** See the rule
under `Where things stand`. **The refresh that carries this section moves the
row**, and it reads `dc116ea7`, the last substantive merge before the refresh.

## Merges from PR #210 to PR #213 (2026-10-10/11)

Each merge was measured on 2026-10-11 with `git rev-list --parents` and
`git diff <tip> <merge>`.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #210 | `13a3e56b` | `13a3e56b` | `CHAT-tatbvwjo` | Refresh the forward register after PR #209 |
| #211 | `f6639abc` | `17d388de` | `CHAT-flhgiule` | Remove the unused topic access root keys |
| #212 | `3e8d8b03` | `30a54db7` | `CHAT-ircckvrs` | Wait for user writes before returning the key |
| #213 | `dc116ea7` | `5c24e6e9` | `CHAT-erkoxybm` | Message pointer: a message that names a message or a topic |

Three merge trees equal their tips. **PR #210 has no tip of its own**, and the
reason follows.

### PR #210 landed without a merge commit

**`13a3e56b` has one parent, `69dbd2ee`.** The merge commit and the pull
request head share one sha, so the branch landed by fast-forward. That is not
a merge commit.

The repository takes merge commits only since 2026-09-17. Squash and rebase
are both disabled. So this tip is worth questioning, and it is recorded rather
than corrected. **Nothing is lost.** The tree that `13a3e56b` holds is the tree
the refresh commit wrote, and that content is on `master`.

**PRs #211, #212 and #213 each carry two parents.** So the rule holds for
every merge after #210.

### The stale issue that the sprint close found

**`CHAT-flhgiule` was `in-progress` while its work was merged.** PR #211
merged it as `f6639abc`, and the pull request body names the issue. The status
was never moved. It is `done` now, closed on 2026-10-11.

**This is the second occurrence of one class.** `CHAT-ubmrxyqo` carries the
first, recorded under `Housekeeping`. A merged issue that keeps an active
status makes the tracker lie about the sprint.

### The scheduled dependency audit is red, and the owner deferred it

**Five consecutive runs have failed**, and each one names the same finding.

| Run | Date |
|---|---|
| `37615558346` | 2026-10-07 |
| `37773162122` | 2026-10-08 |
| `37925766480` | 2026-10-09 |
| `38047078329` | 2026-10-10 |
| `38135195213` | 2026-10-11 |

Every failure reads one line:

    kotlin-sdk-server-jvm-0.15.0.jar
      (pkg:maven/io.modelcontextprotocol/kotlin-sdk-server-jvm@0.15.0,
       cpe:2.3:a:jetbrains:kotlin:0.15.0:*:*:*:*:*:*:*)
      : CVE-2026-53914(9.8)

**The CPE is probably wrong.** JetBrains Kotlin never published a 0.15.0
release. The run of 2026-10-07 names five older Kotlin CVEs on the same CPE,
such as CVE-2019-10101. Those CVEs describe the Kotlin language, and not this
adapter.

**The caution is the suppression.** CVE-2026-53914 is also recorded against
`kotlin-stdlib` 2.4.10, which this repository holds. So a suppression must
name the MCP SDK `packageUrl` alone. A suppression by CVE would hide a real
finding.

**The owner deferred the repair on 2026-10-11.** `CHAT-wgiwqodp` holds it.
The workflow runs on a schedule, so it never gates a pull request. **The three
pull-request jobs are green.**

### The pull-request workflow is green

Run `38126053450` tested the PR #213 head `5c24e6e9`. All three jobs passed:
`build` in 8m48s, `integration` in 17m4s, and `agent-http` in 2m32s.

**This is the first sprint close in this file with three green pull-request
jobs.** Every section from 2026-10-01 to 2026-10-03 records a red integration
job, under B12.

### Open after these merges

- **`CHAT-aysfqhnm` holds this refresh.** It follows PR #213.
- **`CHAT-mpfuifei` is done.** PR #215 merged on 2026-10-12 as `48e861ad`, a
  merge commit with two parents, `e8ece7fd` and `f414b0ff`. The branch is gone,
  local and remote, and no worktree remains. **This file recorded it as `todo`
  from 2026-10-11 until this correction.** That reading came from the file
  alone, and the issue had merged.
- **`CHAT-wgiwqodp` holds the red scheduled audit.** The owner deferred it.
- **`CHAT-pggtduxz` closed on 2026-10-11.** PR #208 merged its work, and the
  owner closed the issue.
- **Two live branches carry worktrees**, `chat-emwhnlay-import` and
  `chat-lswjobhz-shared-store-index`. Neither is part of this sprint.

## Lucene identity fields (CHAT-hajmhslp, 2026-10-13)

The `chat-hajmhslp-index-exact` branch changes Lucene identity fields to
whole-value fields. An `_x_` field name selects `StringField` and `TermQuery`.
Message `data` keeps text analysis. `IndexFieldKinds` owns the field rules and
the NFC normalization used by both index writes and lookups. User handles also
use `Locale.ROOT` lowercase. Topic names keep case.

Entry format 2 makes a file index with format 1 rebuild at its next start.
The request type is unchanged, and its `first` field carries the field name.
**An exact query must send the `_x_` prefix in that field.** `IndexFieldKinds`
refuses a plain name for an exact field and names the prefixed form.
**No alias period exists**, because the refusal is a `ChatException`.
The key-value field registration is unchanged.
The membership index uses one exact field for each member-room pair.

`CHAT-zxrgxdlx` still depends on this issue. It sets the imported message id to
`nameUUID("<topicId>:<messageUuid>")`. The operator supplies a generation in
the topic name only. The message gains no `source_id` or wire change. A
room-specific JSON source map lives beside the export. An incompatible long
key type must fail at startup. The importer remains separate work.

## Cassandra contact points (CHAT-fmvsylrv, 2026-10-14)

PR #225 merged on 2026-10-14 as `cc934030`. The merge has two parents,
`6a9b8b55` and `3e1c5682`, so it is a real merge commit. The merge tree is
byte-identical to the branch tip `3e1c5682`.

A Cassandra deployment carried a second contact point.
`ContactPointConfiguration` called `sessionBuilder.addContactPoint`, and it
never removed the point that `AbstractSessionConfiguration.getContactPoints()`
supplies, which is `localhost` with port `9042`. `SessionBuilder` has no clear
method.

**The harm appeared on this host, and one test found it.** A docker compose
stack of another session published `127.0.0.1:9042` and held the `chat_long`
keyspace. `CassandraClaimBootTests` seeded node 21 in the test container and
then booted the application. The claim store read the foreign cluster, which
held no node 21 row, so the claim was granted and `assertThrows` found no
exception. That is why the `--ci` gate of the `CHAT-hajmhslp` branch reported
`chat-deploy-cassandra` as a new failing module.

**The risk was wider than the test.** Any Cassandra deployment could bind to a
foreign cluster that answers on `localhost:9042` and holds the same keyspace
names. Reads and writes would then reach the wrong store.

`ContactPointConfiguration` overrides `getContactPoints()` and `getPort()` with
the configured values now. The session builder applies credentials alone.
`ContactPointConfigurationTests` pins both. `docs/BUILD-HEALTH.md` records the
failure signature and the check for a foreign listener.

`CHAT-axkakbhl` added the contact point fallback that caused this. The module
had this defect from that change until PR #225.

One limit stays open. No deployment ran against a remote cluster. Every reading
came from a test JVM.

## The Claude import topic scope (CHAT-emwhnlay, 2026-10-14)

`CHAT-emwhnlay`. Branch `chat-emwhnlay-import`, in the worktree
`/Users/darkbit1001/.codex/worktrees/f019/demo-chat`. **This work is not
merged.** The reviewed work order is
`docs/superpowers/specs/2026-10-13-claude-thread-topic-import-work-order.md`.

### The collision, and the fix

**One export record reaches two topics.** Rule 4 of the work order puts a
shared parent into every thread that uses it. A fork therefore imports one
record into two rooms.

**The route refused the second room.** `MemoryDomainCommandBus.admit` keys a
request on the owner and the request ID. Its fingerprint includes the room. A
repeat with a different room is a `RequestConflictException`, not a duplicate.
So one request ID names one message.

**`MessageImportRequest.messageId` has one consumer**, which is
`requestId = "import:${req.messageId}"` at `MessagingServiceImpl.kt:184`. The
route treats the value as opaque, so the caller owns its scope.

**The source ID is `<topicId>:<messageUuid>` since 2026-10-14.** It was
`<conversationUuid>:<messageUuid>`. The value is also the derivation input
that `CHAT-zxrgxdlx` names, so one spelling serves both.

### The evidence

- `ClaudeImportCommandsTests` 15 of 15. The new case `a shared root names one
  source ID per topic` failed at `:286` before the fix.
- `ClaudeImportDeploymentTests` 4 of 4 on the rebuilt image. The new case
  `a forked conversation imports each leaf into its own topic` covers rule 4
  on the real route. The second generation case failed with
  `RequestConflictException` before the fix.
- `build-health.sh --ci`: exit 0, 30 modules, 2548 tests, 0 failures,
  0 errors, 82 skipped, `agent http gate: ok`, and no drift. The shell image
  id moved from `f6d43011d980` to `9b02c7070e02`. `docker events` reported no
  OOM kill in the run window.
- Two unit tests cover acceptance rows that no test held before:
  `an added leaf renames no existing topic`, and `the largest room id fits
  the request limit`.

### The sentence the measurement refuted

**The work order said the source ID stays conversation-scoped.** That sentence
contradicted its own rule 4. It is corrected in place, and the design
document's corrections section records it.

### The owner settled the boundary on 2026-10-14

**The room-scoped source ID stays in the importer.** PR #226 keeps it. The
route does not change in that pull request.

**A route-scoped request ID remains defensible, and it waits.** The route owns
the request identity for every caller. A change there would move the identity
of imports that already exist. `CHAT-zxrgxdlx` settles that general contract
before it derives the message keys. It is a child of `CHAT-emwhnlay`.

**The owner measured the callers.** The shell is the only production caller.
The route tests use unscoped IDs, so they do not pin the contract either way.
The issue comment of `CHAT-zxrgxdlx` records both options now.

**`CHAT-emwhnlay` stays open for that child work.** The long-key startup
refusal and the derived-ID read are open with it.

### Open

- **PR #226 merged on 2026-10-15 as `08a71e81`.** Its CI run `38664537134`
  passed all three jobs: `build`, `integration`, and `agent-http`. The branch
  ref is gone, local and remote, and the worktree is removed.
- `CHAT-zxrgxdlx` owns the derived message key, the UUID-only import gate, and
  the source-to-chat map. It is `todo` and holds no code.
- The `Checkout` row of this file is not moved by this section.

## Merges from PR #214 to PR #226 (2026-10-12/15)

Each merge was measured on 2026-10-15 with `git rev-list --parents` and
`git diff <tip> <merge>`. **Every merge has two parents, and every merge tree
equals its branch tip.** So each one is a real merge commit, and none carried a
conflict repair.

| PR | Merge | Tip | Issue | What |
|----|-------|-----|-------|------|
| #214 | `e8ece7fd` | `edead755` | `CHAT-aysfqhnm` | Refresh the forward register after PR #213 |
| #215 | `48e861ad` | `f414b0ff` | `CHAT-mpfuifei` | Cassandra `topic byIds` |
| #216 | `d89809d7` | `81dd9100` | `CHAT-ivtnvgyg` | Name the half that fails in the key exists test |
| #217 | `baf11a90` | `e9fa6973` | `CHAT-liomkoer` | Give the key removal test a route and an assertion |
| #218 | `0552c532` | `c2b5fe5d` | `CHAT-ivtnvgyg` | Read the reach of the key read in two steps |
| #219 | `20a1df99` | `ed20052b` | `CHAT-dbltvvmw` | Deploy a Cassandra core with Lucene and Redis |
| #220 | `b8db69f7` | `480fd02f` | `CHAT-dbltvvmw` | Add authorization server Just recipes |
| #221 | `4ff78cc8` | `24201c92` | `CHAT-dbltvvmw` | Deploy the authorization server in Docker |
| #223 | `27ab4772` | `adb9efcd` | `CHAT-dbltvvmw` | Refresh the core launch goldens |
| #222 | `78bf538d` | `1c615226` | `CHAT-ybmlcdiw` | Close the shell context so the JVM exits |
| #224 | `6a9b8b55` | `256e1b41` | `CHAT-hajmhslp` | Query the Lucene identity fields through the shared builder |
| #225 | `cc934030` | `3e1c5682` | `CHAT-fmvsylrv` | Use the configured Cassandra contact points |
| #226 | `08a71e81` | `77e544ae` | `CHAT-emwhnlay` | Import every Claude conversation thread into its own topic |

**PR #223 merged before PR #222, and the rows show it.** The first parent of
`78bf538d` is `27ab4772`, the merge commit of #223. So a pull request number is
not a merge order.

**Three merges in this range are branch syncs, not pull requests.** `1c615226`,
`709ad7d0` and `94957eb7` each merge `master` into a live branch. `94957eb7` is
the last one, and it brought `master` to the tip of #226 before that merge.

**`CHAT-ybmlcdiw` is done.** PR #222 closes the shell context, so the JVM
exits. The issue holds the measured cause: the web server thread held the
actuator port, and `spring.shell.context.close` now defaults true for the
shell launch.

**`CHAT-dbltvvmw` is done**, and it carries four of these merges: #219, #220,
#221 and #223.

## The register split (CHAT-nxbtadan, 2026-10-15)

`CHAT-nxbtadan`. Branch `chat-nxbtadan-register-64kb`. **This work is not
merged.** `docs/BUILD-HEALTH.md` holds the check, under
`The forward register size check`.

### The problem

**`forward-register.md` was 280,889 bytes, and `AGENTS.md` imports it.** So the
file loaded in every session, and its size was a cost on every turn. The owner
set a limit of 64 KB on 2026-10-15.

### The split

- Every section dated before 2026-10-08 moved to a period log, verbatim:
  `forward-register-archive-2026-08.md` with 4 sections,
  `forward-register-archive-2026-09.md` with 41, and
  `forward-register-archive-2026-10.md` with 25.
- The live file keeps the durable core and every section dated on or after
  2026-10-08. It holds 23 sections: 14 dated ones, the eight durable blocks,
  and `## The archive`.
- **A period closes when the live file would pass 64 KB.**
- `build-health.sh` measures the file in every mode. It fails above 65,536
  bytes and prints `REGISTER TOO LARGE`.

### What the split corrected

- **The preamble described the 2026-08-23/24 handoff**, and it was replaced.
- **One pointer was dangling.** `app.nodeid` pointed at a lease section
  "below", and that section moved to the 2026-08 log. The pointer names the
  file now.
- **Two state facts were stale.** The state table read 2026-10-11, and the
  merge record stopped at PR #213. This file holds a table for PRs #214 to
  #226 now.

### The proof

- **Each of the 90 `## ` headings of the source appears exactly once** across
  the four files.
- **The split outputs held the source byte count exactly**: 280,889 bytes, over
  the preamble and the three logs.
- **Three bodies changed, and each is intended.** The state table is a
  rewrite. `Things worth not relearning` gained the repaired pointer. The
  import section records the merge of PR #226.
- One assembly defect was found and repaired. The 2026-10 log first carried a
  3,352-byte copy of the state table's tail. The rebuilt file is the split
  output alone, under a header.
- **`build-health.sh --ci` exited 1, and `chat-client-rsocket` caused it.**
  `UserClientTests` failed 4 of 5 with `ClosedChannelException` and two 45.01 s
  waits. `chat-authorization-server` and `chat-shell` then reported SKIPPED.
  **This work changes no Kotlin and no pom.** The class passes alone, 5 of 5 in
  2.1 s.
- **The failing context bound port 64342, and IntelliJ held
  `127.0.0.1:64342`.** The client connects to `localhost`, so its frames
  reached IntelliJ. **A first reading exonerated the port trap and it was
  wrong.** It read the ports of other contexts, and missed the failing one.
  Forcing the server port to 64342 fails the class the same way in 137 s.
- **The second `--ci` run passed, on the same tree.** Exit 0, 30 modules, 2548
  tests, 0 failures, 0 errors, 82 skipped, `agent http gate: ok`, and no drift.
  **So the failure above is intermittent**, because the kernel picks the port.
  `CHAT-ivtnvgyg` holds it.
- The register size check printed nothing in either run, because the live file
  is under the limit.
