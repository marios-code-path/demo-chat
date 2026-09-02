# Forward Register

What is carried forward out of the 2026-08-23/24 session: decisions made, work
landed, work planned but not started, things deferred to an issue, and claims that
are load-bearing but unverified.

Written at the handoff point after the capability spec and its first plan. Nothing
in this file is authoritative on its own — each row points at the artifact that is.

## Where things stand

| | |
|---|---|
| Checkout | `master` |
| Register state | Updated after PR #61 merge (duplicate topic name rejected) |
| Last merged PR | #61, merge commit `5c7fd056` |
| Merged feature branches | All local refs removed. Remote refs survive every merge — this repo has no auto-delete. `origin/{b5-docs, b5-red-proof, ci-integration-execution, b7-launch-fix, b8-send-fix, shell-recipe, hangup-tests}` await the owner's word; all except `b5-red-proof` are merged. |
| Worktrees | main checkout only |
| Open PRs | dependabot only (#8, #10, #11). Nothing of ours is in flight. |

The stale locked worktree at `.claude/worktrees/domain-serialization` was clean
and is removed. The local and remote `nodeid-claim-lease` branches are removed.

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

## Domain serialization (2026-08-25/26)

`CHAT-gjggodpa` implementation is complete. The dead `@JsonTypeInfo(WRAPPER_OBJECT)` wrapper was
removed from `User`, `MessageTopic`, and `TopicMembership`. The redis `rebind`
workaround was deleted; the typed accessors use plain `convertValue`. The REST
contract test was updated to the new shapes. The webflux suite is fully green
(81 tests).

**`MessageTopic` inheritance outcome:** it extends `KeyValuePair`, which keeps
its own `@JsonTypeInfo`. Once `MessageTopic` lost its annotation, Jackson
annotation inheritance applied the `KeyValuePair` wrapper. So `MessageTopic` is
NOT flat. It carries the `keyValue` wrapper. The wire-shape test pins this.

**E2EE follow-up is complete.** `CHAT-zbjzbcoy` is done and merged as PR #49. The
seven E2EE types in `EncryptedEnvelope.kt` (`DeviceRegistration`, `PreKeyBundle`,
`EncryptedEnvelope`, `ConversationCursor`, `ConversationEpoch`, `FrankingTag`,
`Presence`) now serialize flat. The check for subtypes found none. No type had
`@JsonSubTypes`. No type had a custom serializer or deserializer. So the wrapper
was dead on all seven. `E2eeWireShapeTests` pins the flat shape, one test per type.

`Key<T>` keeps its own wrapper inside these seven types. Only the outer wrapper
went away.

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
- **`chat-shell` reporting 17 skipped under `-Pintegration` is not missing
  coverage.** `@Disabled` sits on the generic base classes, surefire counts them as
  test classes, and JUnit does not inherit `@Disabled`, so the concrete `Long*`
  subclasses run. Documented in `docs/BUILD-HEALTH.md`.
- **A wire-format change makes the shell integration image stale.** The
  chat-shell tests run the client against the
  `chat-deploy-long-memory-integration-test` Docker image, not against the
  reactor. After a serialization change, rebuild the image with
  `mvn -Ptest-build install`, then run `-Pintegration`. A stale image caused 8
  decode errors that looked like a code regression.
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
  across deployments is now enforced by a store-side lease. See the node id claim
  lease section below.
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

## Node id claim lease (2026-08-28)

`CHAT-wyssrokr` is done and merged through PR #53. Merge commit:
`ed7e18c4`. Branch head: `79944205`. The merge tree is byte-identical to the
branch head tree, so the merge added no content change beyond the merge commit.

Spec: `docs/superpowers/specs/2026-08-28-nodeid-claim-lease-design.md`. Plan:
`docs/superpowers/plans/2026-08-28-nodeid-claim-lease.md`. Operator document:
`docs/NODEID-CLAIM.md`.

The integration gate ran again after the merge. `build-health.sh --integration`
exited 0 and reported that reality matches `docs/BUILD-HEALTH.md`.

Four rules that are load bearing and easy to lose:

1. **The claim seam is either core selector.** A process claims when
   `app.service.core.key` or `app.service.core.persistence` names `redis` or
   `cassandra`. Generated ids reach the key store and the persistence store, so a
   condition on one selector alone would leave `key=memory` with
   `persistence=redis` unprotected. `@ConditionalOnProperty` cannot express that
   OR, so `ConditionalOnSharedBackend` in `chat-core` reads both properties.
2. **Uniqueness is per key type per store.** Cassandra separates key types by
   keyspace. Redis carries the key type in `chat:nodeclaim:<keyType>:<nodeId>`. A
   `long` deployment and a `uuid` deployment can both hold node id 7 on one redis,
   and that is correct. The failure message states the scope for this reason. A
   message that said "one store" would be false.
3. **`node_claim` is absent from the truncate scripts on purpose.** A live lease
   must expire. A test cleanup script must not delete it.
4. **Every container-backed test that claims owns a distinct `app.nodeid`.** The
   allocation table is in `docs/NODEID-CLAIM.md`. Spring caches contexts, so two
   open contexts against one store would collide. That collision is correct
   behaviour and appears as a test failure, not as a flake.

Two claims from the spec were verified while building:

- **Cassandra treats a TTL expired row as absent for `IF NOT EXISTS`.**
  `NodeClaimTableProbeTests` proves it against `cassandra:4.1.3`. The `expires_at`
  fallback is not needed, and no application clock enters the decision.
- **The reactive store beans are present at the mixed seam.** A deployment with
  `key=memory` and `persistence=redis` supplies `ReactiveStringRedisTemplate` and
  registers one redis claim store. The cassandra equivalent also boots.

Traps found while building, each of which cost a debugging cycle:

- **`@Container` on a static field stops the container after the first test
  class.** `CassandraContainerBase` applies `keyspace-long.cql` once per JVM in an
  `init` block, so a second test class met a fresh container with no `chat_long`
  keyspace and `CassandraDeployTest` failed with `Invalid keyspace chat_long`. The
  annotation is gone. The `init` block starts the container, which is how
  `RedisTestContainer` already worked.
- **`SpringApplicationBuilder.properties()` is the lowest precedence source.** It
  writes to `defaultProperties`, so `application.yml` overrode the test container
  address with `localhost`. Pass a launch surface as command line arguments
  instead.
- **A lightweight transaction that did not apply because no row exists returns a
  row with only the `[applied]` column.** Asking that row for `owner_id` throws
  `IllegalArgumentException`. Test for the column, not for null.
- **Spring Data wraps the driver `InvalidQueryException`.** `onErrorMap` on the
  driver type never matches. Walk the cause chain.
- **`mvn -pl <module> -am -Dtest=X` fails on every upstream module** with "No tests
  matching pattern". Add `-Dsurefire.failIfNoSpecifiedTests=false`. Surefire joins
  several test names with a comma, not a plus sign.

One item recorded as unmeasured, not as a result:

- **Whether the web server port binds before the claim fails is not measured.**
  The boot tests assert the requirement, which is that `ApplicationReadyEvent` is
  never published for a duplicate. The stronger statement, that the port never
  binds, rests on bean instantiation running before `WebServerStartStopLifecycle`,
  and nothing in the suite asserts it. Do not quote it as verified.

## CI integration execution (2026-08-29)

`CHAT-uortzsbx` (B5) is done and closed. PR #54 merged the workflow and the
build fix, merge commit `cc93a946`. PR #56 merged the BUILD-HEALTH record,
merge commit `107c187e`. Spec and plan:
`docs/superpowers/specs/2026-08-28-ci-integration-execution-design.md` and
`docs/superpowers/plans/2026-08-28-ci-integration-execution.md`.

The workflow now carries two jobs. `build` runs `mvn -B clean test`.
`integration` runs `mvn -B clean verify -Ptest-build,integration` with a
30 minute timeout. Both fire on every pull request and every master push.
Nothing gates on them yet.

Three facts that are expensive to relearn:

1. **The job must use `verify`, not `test`.** The chat-shell test image is
   built in the `package` phase by `chat-deploy-memory-integration-test`.
   That module precedes `chat-shell` in reactor order (`pom.xml` lines 83
   and 84), so `verify` produces the image before the container tests run.
   `test` never reaches `package`.
2. **Do not gate a default behind `activeByDefault`.** The removed
   `noartifact` profile set repackage skip through that mechanism. Maven
   deactivates an `activeByDefault` profile whenever any profile in the
   same POM activates, so `-Pintegration` silently repackaged every module
   and broke library test compile (fp issue `CHAT-kaaupcvu`, distinct from
   the `B6` stale-target row in BUILD-HEALTH). The root POM now sets
   `skip=true` directly, and only the `deploy` profile or a module
   image-build profile enables repackage.
3. **`chat-shell` 36 run with 17 skipped is the green shape.** The 17 are
   `@Disabled` generic base classes. The 19 that run include every
   container-backed test. See BUILD-HEALTH for the mechanism.

The failure proof ran on throwaway PR #55, closed without merging. Run
`33274410463` shows both jobs red: the unit job failed in
`ClientInitializerTest` with no container started; the integration job
booted the test image and failed in `chat-shell` at module 28 of 32.

Flakiness baseline for the gating decision, integration job:
`33271411684` pass 8m31s, `33274062218` pass 8m16s, `33274410463` designed
failure, `33275208499` pass 9m11s, plus `33273399921` and `33277954119`
pass. Six runs, five green, one designed red. Four more green runs complete
the 10-run record. The decision (required check or status quo) is open and
tracked on `CHAT-uortzsbx`.

## Launch classpath and B7 (2026-08-31)

`CHAT-uxmjaebs` (B7) is done and merged through PR #57, merge commit
`0c5378c2`. `chat-deploy-memory` carried `chat-service-controller` at
`test` scope since 2023-10-12. The first direct launch through
`just launch-memory` failed on a missing `PasswordEncoder` bean.

Three facts that are expensive to relearn:

1. **A deploy module has three classpaths, not one.** `spring-boot:run`
   uses the runtime classpath. Surefire uses the test classpath. The
   Docker image is assembled by `chat-deploy-memory-integration-test`,
   which declares its own scopes. A wrong scope is invisible in two of
   the three, which is how a 2023 defect survived to 2026.
2. **`-DskipTests` does not stop test compilation.** `spring-boot:run`
   forks the `test-compile` lifecycle. Test classes then resolve upstream
   modules from the local repository, and a stale repository breaks a
   launch before the app starts. The launch path now passes
   `-Dmaven.test.skip=true`.
3. **Local repository policy, set by the owner.** Resolve a stale local
   repository by removing `~/.m2` and building fresh. Use `mvn clean`
   whenever possible. `docs/BUILD.md` carries the same rule.

## Send path and B8/B9 (2026-09-01)

`CHAT-qonhhtuq` (B8) and `CHAT-ouzjdxun` (B9) are done and merged through
PR #59, merge commit `83eaa2d1`. The first interactive shell `send`
attempt failed twice: `For input string: "_"` client-side, then
`Object not Found` server-side. Each error was a separate defect.

Three facts that are expensive to relearn:

1. **A provider factory method is not a bean.** The pubsub providers were
   `@Component` classes whose `pubSubService()` constructed a new object
   per call. Every Spring call site got its own instance. `@Configuration`
   plus `@Bean` is what gives shared state, and the memory persistence
   providers already used that pattern. Lite `@Bean` on a `@Component`
   does not intercept calls; full-mode `@Configuration` does.
2. **The memory backend keeps all pubsub state in instance maps.** With
   split instances, `open()` and `sendMessage()` disagree on whether a
   topic exists, and the error surfaced as the generic
   `Object not Found`. The redis and kafka providers keep part of their
   state externally, so the same split degrades quietly there.
3. **`send` had zero test coverage, in any era.** That is why B8 and B9
   coexisted for years. `LongPubSubCommandsTests` now pins both paths:
   send by name, send by id. A server-code change requires an image
   rebuild (`-Ptest-build`) before `-Pintegration` can see it.

The sender identity in `PubSubCommands.send` is still hardcoded
(`identity("_")`), and the `TODO` comments in that method stand. Not
filed; owner judgment.

## Topic name uniqueness (2026-09-01)

`CHAT-qktlglfa` is done and merged through PR #61, merge commit
`5c7fd056`. The decision is the owner's: a topic name names one room,
and a second add with the same name fails with `DuplicateException`.

Two facts that are expensive to relearn:

1. **The index schemas never enforced uniqueness.** Cassandra's
   `chat_room_name` primary key is `(name, room_id)`, so a duplicate
   name inserts a second row. `findByKeyName` returns a `Mono`, so the
   lookup took the newest row and silently orphaned the older room.
   The memory deployment uses the Lucene index, where `findBy` returns
   every match and `getRoomByName`'s `single()` threw a raw
   `IllegalStateException`. Same action, two undesigned outcomes.
2. **Enforcement sits in the composite, not the stores.** `addRoom`
   checks `findBy(...).hasElements()` before creating. No backend ever
   holds a duplicate, so `single()` in `getRoomByName` stays safe. The
   check is check-then-act, not atomic. Two concurrent adds of one name
   can both pass it. The stores offer no conditional write on the
   index, so closing that race needs a design decision, not a patch.

Not filed, recorded here so nobody rederives them: the cassandra index
`rem` deletes `ChatTopicName` with the name `""`, so it never matches
the real row and index removal is broken on that backend.
`MemoryTopicPubSubService.listenTo` uses `getOrPut` and silently opens a
sink for a topic that was never opened.

## Message vector recall (2026-09-01)

- New modules `chat-vector-simple` and `chat-vector-redis` provide the
  `VectorStore` bean. They are gated on `app.service.core.vector`. No deploy
  yml sets the selector yet: the vector and embedding selectors and the
  `app.controller.recall` flag are test-only until the gateway embedding
  integration lands.
- Interim capability wiring: `VectorSelectorValidationConfiguration`
  (chat-core) fails startup when `app.service.core.vector` and
  `app.service.core.embedding` are set as an incomplete or illegal pair.
  `@ConditionalOnProperty` stands in for `@ProvidesCapability` until the
  capability mechanism lands.
- The Redis vector path is Jedis-backed (Spring AI `RedisVectorStore`). The
  repo data path stays Lettuce. Redis Stack is required for the Redis vector
  tests.
- Vector tests claim no node id: they activate memory key and persistence.
  See docs/NODEID-CLAIM.md.
- Two facts that cost time, recorded so nobody rederives them:
  1. **`chat-core` does not enable the Kotlin all-open compiler plugin.** The
     module declares the `kotlin-maven-allopen` dependency, but it has no
     `<compilerPlugins><plugin>spring</plugin></compilerPlugins>` block. A
     `@Configuration` class in `chat-core` must be `open`, and its `@Bean`
     methods must be `open`. `chat-service-composite`,
     `chat-service-controller`, and both vector modules do enable the plugin.
  2. **`RSocketServerTestConfiguration` carries a bare `@ComponentScan`.** It
     roots at `com.demo.chat.test.rsocket`, so every `@Controller` under that
     package enters every RSocket test context. Put a new test controller
     outside that package.
