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
| Checkout | `master` at `04dbe77a`, the merge commit of PR #228, in sync with `origin`. The working tree holds three untracked owner files, `conversations.json`, `conversations.jsonl`, and `forward-register-archive.md`. |
| Register state | Restructured on 2026-10-15 into this file and three period logs. Refreshed on 2026-10-15, after PR #229. The period from 2026-10-08 to 2026-10-15 closed on 2026-10-16, under `CHAT-jgegpcoi`. |
| Last merged PR | #229, merge commit `f63cd3a4`, merged on 2026-10-15. |
| Merge strategy | **Merge commits only, since 2026-09-17.** Squash and rebase are both disabled at the repository. A tip with one parent is now worth questioning. |
| Local branches | Twenty-two, measured on 2026-10-15 at `master` at `f63cd3a4`. **This row records commit ancestry, and it states its snapshot point.** **Thirteen others hold no commit that master lacks**, so master holds their work by ancestry: `boot4-bump` at `179c2fd8`, `chat-bmmtojqm-chat-log-view` at `925a1db8`, `chat-chsvdqbi-springai` at `aeb579dc`, `chat-eoqkbqve-access-expressions` at `c22f9018`, `chat-ivtnvgyg-reach-check` at `c2b5fe5d`, `chat-kcccoifk-dead-auth-manager` at `99483bca`, `chat-qwmjrixq-jackson3modules` at `8c3acfce`, `chat-register-checkout-clause` at `38063a53`, `chat-sgyaaivp-cassandra-timeout` at `a812cd0c`, `chat-znprrzhn-enforcement` at `6970a89a`, `chat-znprrzhn-register-refresh` at `e22486b3`, `register-refresh-2026-10-02` at `730b61f1`, and `review-pr150` at `a0065fcb`. **Four hold one commit that master lacks, and master holds an equivalent change**: `chat-hazcatpc-review-fix` at `606d251a`, `chat-mcp-spec` at `d29bac4a`, `chat-xcmpudyb-message-id` at `20f3d014`, and `chat-znprrzhn-composite-enforcement` at `91af1c96`. **Three hold commits that master lacks, and master holds no equivalent change.** The next row names them. **One is this work**, `chat-cdutiiyg-ancestry-fix`, cut from the snapshot tip. It holds the refresh commit alone and no other work. It joins the ancestry group once its pull request merges. **Every branch ref is kept.** |
| Unmerged branches | Three, measured on 2026-10-15 at `master` at `f63cd3a4`. Each holds commits that master lacks, and master holds no equivalent patch: `backup/chat-bmmtojqm-before-review` at `93cada30`, with 1 commit absent, `chat-lswjobhz-shared-store-index` at `863b6d00`, with 2, and `chat-urhjrwbt-indexelastic` at `d8d797b3`, with 1. **The branch refs are kept.** `chat-lswjobhz-shared-store-index` is live work. |
| Worktrees | Five, measured on 2026-10-15. The main checkout, on `master`. `.worktrees/chat-cdutiiyg-ancestry-fix`, which is this work. `.worktrees/chat-lswjobhz-shared-store-index` at `863b6d00`, which is live work. `.worktrees/chat-bmmtojqm-chat-log-view` at `925a1db8`, whose branch is merged. **It is kept.** And `/Users/darkbit1001/.codex/worktrees/7768/demo-chat` at `3e1c5682`, detached, which holds the pre-merge tip of PR #225. |
| Open PRs | None, measured on 2026-10-15, after PR #229 merged. |
| In-progress issues | Eight, measured on 2026-10-15: `CHAT-owiksxvz`, `CHAT-ivtnvgyg`, `CHAT-aqpcacwv`, `CHAT-emwhnlay`, `CHAT-lswjobhz`, `CHAT-uxlstrdy`, `CHAT-pphwooqm`, and `CHAT-cdutiiyg`, which is this work. |

**The `Checkout` row records the last substantive merge before the refresh.**
A register refresh is a narrow documentation update, so it does not move the
row. **That lag is not drift, and a reader must not correct it.** A row that
chased the true tip would go stale the moment it merged, and it would spawn a
refresh on every refresh. The owner set this rule on 2026-09-29.

**Ancestry and equivalent work are two questions.** A branch that holds no
commit absent from the snapshot is landed by ancestry. A branch that holds one
absent needs the second question. `git cherry <snapshot> <branch>` answers it,
and a minus sign means master holds an equivalent patch.

**Four branches hold one commit that the snapshot lacks, and master holds
their change.** Three print a minus sign at `f63cd3a4`:
`chat-hazcatpc-review-fix` at `606d251a`, `chat-mcp-spec` at `d29bac4a`, and
`chat-xcmpudyb-message-id` at `20f3d014`.

**The fourth needs separate evidence, because it is a merge commit.**
`git cherry` prints no line for a merge commit, so it cannot prove
`chat-znprrzhn-composite-enforcement` at `91af1c96`. Its parents are
`b60a9df4` and `d560c986`, and both are ancestors of the snapshot.
`git diff d560c986 91af1c96` is empty. Its tree `3c9d0fd2` equals the tree of
`d560c986`. So the snapshot holds that content.

**One of the three unmerged branches carries no lost work, and its evidence
is the pull-request record.** `backup/chat-bmmtojqm-before-review` at
`93cada30` holds one commit that master lacks, and `git cherry` prints a plus
sign for it. The commit is the chat log before its review repairs, and PR #186
merged the repaired version.

**One belongs to other work.** `chat-urhjrwbt-indexelastic` at `d8d797b3`
holds the module repair that PR #106 superseded. **The `topic-by-name` branch
is gone**, and PR #163 carried its work into master.

**Two merged branch refs are removed, local and remote.**
`chat-nxbtadan-register-64kb` and `chat-ivtnvgyg-rsocket-loopback` are gone,
and their worktrees with them.

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

The dated sections before 2026-10-16 live in period logs, verbatim:

- `forward-register-archive-2026-08.md`, for 2026-08-25 to 2026-08-31.
- `forward-register-archive-2026-09.md`, for 2026-09-01 to 2026-09-30.
- `forward-register-archive-2026-10.md`, for 2026-10-01 to 2026-10-15.

Nothing imports those files, so they cost no context until a reader opens one.

**A period closes when this file would pass 64 KB.** The oldest dated sections
then move to their period log, and this file keeps the rest. The log of the
current period stays here, because a reader needs recent context without
opening an archive.

**`shell-scripts/build-health.sh` fails when this file passes 65,536 bytes.**
A size rule with no check drifts back, so the limit is measured on every pull
request.

## Imported message identity (CHAT-zxrgxdlx, 2026-10-16)

`CHAT-zxrgxdlx` holds the accepted design and plan in
`docs/superpowers/specs/2026-10-15-imported-message-id-design.md` and
`docs/superpowers/plans/2026-10-15-imported-message-id.md`.
T1 through T7 merged in PRs #232 to #241. `CHAT-jorwpgjb` holds this document
pass on branch `chat-jorwpgjb-docs`, cut from master `fb20334b`.
The `Checkout` row above keeps its snapshot rule and does not move here.

### The owner decisions and the code

- The shell sends `<roomId>:<messageUuid>` as the source ID. The route uses
  that value for `import:` request identity and the name-based UUID hash.
  The route does not add another room prefix.
- The operator supplies the generation. It enters the topic name alone.
  `MessageImportRequest.messageId` stays on the wire.
- Claude import supports the `uuid` key type. The general
  `KeyTypeValidation` table refuses `app.message.import` on a `long` launch
  before singleton creation. The route also refuses a Long import when the
  switch is unset. The switch declares use. It does not disable the route.
- `ImportedMessageId` hashes the room-scoped source ID. The bus admits the
  supplied message ID on import alone. A rerun reads that derived key and
  returns a live row. The sender, time and text history scan is gone.
- The four `msg_id` columns in `keyspace-uuid.cql` moved from `TIMEUUID` to
  `UUID`: `chat_message_id`, `chat_message_user`, `chat_message_topic`, and
  `chat_message_index_by_id`. Both `CassandraStoreShapeCheck.idType` and
  `IndexServiceConfiguration.idType` now require `uuid`. `keyspace-long.cql`
  stays `BIGINT`. An old UUID keyspace must be recreated.
- The shell writes `claude-import-map-<conversationUuid>-<generation>.json`
  beside the export after each thread. It maps leaf UUIDs to topics and
  room-scoped source IDs to the returned message IDs. A rerun merges entries.
  An unreadable or conflicting map stops the run. A temporary file and atomic
  move preserve the earlier map on a failed write.

### Earlier verification and mutations

The task comments hold the measured runs. This section reports their results.
T8 did not rerun those mutations.

- T1: 14 of 14 validator tests passed. Three isolated mutations caught an
  empty enabled switch (3 failures), a missing key type (1), and a bypassed
  post-processor (1).
- T2: six derivation tests passed after a red compile without the helper.
- T3: 12 fingerprint and 15 admission tests passed. Four mutations caught a
  removed operation refusal, an omitted ID in the version 4 hash, a version 2
  write, and a minted key in place of the supplied ID.
- T4: the direct-read mutation failed two rerun cases and one route repeat
  case. Removing the bus ID check failed 2 of 15 admission cases. The Long
  fallback mutation failed the Long refusal cases. An unrelated wait timed
  out once during that mutation, then passed on repeated restored runs.
- T5: 14 store-shape and 2 index-shape tests passed. Four mutations exposed
  old `TIMEUUID` columns or either stale `idType` rule. The topic-column case
  needed the UUID deployment test. The index module alone passed 69 of 69.
- T6: the initial map tests passed 7 of 7, then the review repair passed 13
  of 13. Its three initial mutations caught a late map write, an ignored old
  map, and a swallowed write error. Three review mutations each failed only
  its related refusal case. The UUID deployment tests passed 4 of 4.
- T7: the shell refusal test passed 1 of 1. Removing the Claude requirement
  let startup reach a missing service host, so the refusal test failed.

The T4 `--ci` run built both shell images: long `sha256:e87172fd7637` and
UUID `sha256:8ecffee1c200`. Both ids were still present before T8 gates.
The first is for other shell container tests. `ClaudeImportDeploymentTests`
uses the second. `docs/BUILD-HEALTH.md` records their origin.

T8 ran both gates on 2026-10-16, before its commit. The default
`build-health.sh` exited 0: 30 modules, 2,234 tests, 0 failures, 0 errors,
47 skipped, and the BUILD-HEALTH baseline matched. `build-health.sh --ci`
used an empty `DOCKER_CONFIG` and exited 0: 30 modules, 2,631 tests,
0 failures, 0 errors, 82 skipped, `agent http gate: ok`, and the baseline
matched. The long image moved from `sha256:e87172fd7637` to
`sha256:b00d3f751ae3`. The UUID image moved from `sha256:8ecffee1c200`
to `sha256:6d10ddb90cf9`. Docker reported no OOM event in the CI window
from 03:20 to 03:46 PDT. `drift check` and `git diff --check` passed.

### Limits

No deployment enables `app.message.import` in this work. An import before the
derived-key change can duplicate on rerun. Two simultaneous imports can both
submit, because the read and write are not atomic. The map holds the latest
export path alone.
