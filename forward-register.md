# Forward Register

What is carried forward out of the 2026-08-23/24 session: decisions made, work
landed, work planned but not started, things deferred to an issue, and claims that
are load-bearing but unverified.

Written at the handoff point after the capability spec and its first plan. Nothing
in this file is authoritative on its own — each row points at the artifact that is.

## Where things stand

| | |
|---|---|
| Checkout | `master` at `7abe0af8`, clean, in sync with `origin`. |
| Register state | Updated 2026-09-21, after the Spring Boot 4 merge and the seven pull requests that followed it. |
| Last merged PR | #110, merge commit `7abe0af8`. Before it: #109 `0bdb0c4f`, #108 `d1dda1bc`, #107 `d09a2daf`, #106 `6aae087d`, #105 `8d7d9707`, #104 `ecfea4a1`, #103 `c4b63bfc`. |
| Merge strategy | **Merge commits only, since 2026-09-17.** Squash and rebase are both disabled at the repository. A tip with one parent is now worth questioning. |
| Merged feature branches | Four local branches remain, none with a remote, and none with a worktree. **Three hold no commit that master lacks**: `boot4-bump` at `179c2fd8`, `chat-qwmjrixq-jackson3modules` at `8c3acfce`, and `chat-chsvdqbi-springai` at `aeb579dc`. Their content reached master through #103 and the pull requests after it. The fourth, `chat-urhjrwbt-indexelastic`, holds one commit that master lacks, `d8d797b3`, and that commit repairs `chat-index-elastic`, which #106 removed from the reactor. So it is dead work. |
| Worktrees | The main checkout only. The owner removed `.worktrees/boot4`, `.worktrees/ctl` and `.worktrees/springai` on 2026-09-21, because each held a branch that master already contains. **The branch refs are kept.** |
| Open PRs | The owner dropped Dependabot #8 and #11 on 2026-09-21 as no longer relevant. Nothing else was open when this refresh was written, and #112 carries the refresh itself. |

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
- **`chat-shell` reporting 23 skipped under `-Pintegration` is not missing
  coverage.** `@Disabled` sits on the generic base classes, surefire counts them as
  test classes, and JUnit does not inherit `@Disabled`, so the concrete `Long*`
  subclasses run. Measured on 2026-09-15: 48 tests with 23 skipped, so 25 run. The
  count was 36 with 17 skipped until the vector work added tests. Documented in
  `docs/BUILD-HEALTH.md`.
- **A wire-format change makes the shell integration image stale.** The
  chat-shell tests run the client against the
  `chat-deploy-long-memory-integration-test` Docker image, not against the
  reactor. After a serialization change, run
  `build-health.sh --ci`, which builds the image and then runs the container
  tests in one reactor. `--integration` alone does not build the image, and it
  prints a note that says so. A stale image caused 8 decode errors that looked
  like a code regression.
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

## Toolchain modernization wave 1 (2026-09-03/05)

Parent issue `CHAT-owiksxvz`. Seven commits on `message-vector-recall`. None is
pushed. Head `b01d18e8`.

Why this work started: phase 2 of vector search needs the Vectors library at
https://integrallis.github.io/vectors/. That library needs JDK 25. The build
targeted JDK 17 and Kotlin 1.8.0.

### What landed

| Commit | Issue | What |
|--------|-------|------|
| `08f276e6` | `CHAT-onzqrqox` | Removed `spring.version`, `jackson.version`, `io-reactor.version`, `spring-data-elastic.version`, and `lognet.version`. All five sat below the version the Boot BOM manages. |
| `d9728bc8` | `CHAT-adezxbxs` | Removed the `spring-security.version` pin `6.1.0-M2` from `chat-shell` and `chat-authorization-server`. That value was a milestone build. |
| `086e7f13` | `CHAT-gvrvfchz` | Fixed two malformed Maven declarations. `mvn validate` now prints no model warning. |
| `fca1712c` | `CHAT-ornxhhcg` | Kotlin 1.8.0 to 1.9.25. kotlinx-serialization 1.2.2 to 1.6.3. `jvmTarget` stays 17. |
| `a9dd3026` | `CHAT-dzvncoco` | Patch-line refresh. Spring Shell 3.0.0 to 3.4.3. |
| `dda66dd3` | `CHAT-hduoesfr` | Spring Boot 3.3.13 to 3.5.16. Spring Cloud BOM 2023.0.6 to 2025.0.3. `java.version` stays 17. |
| `b01d18e8` | `CHAT-yjmpjigt` | Removed the redundant `testcontainers.version` override and its stale comment, in the root pom and in `chat-shell`. |

### The finding that reshaped the plan

Boot 3.3.13 supports Java 17 through 23. Boot 3.5.16 supports Java 17 through 25.
So JDK 25 does not need Spring Boot 4. A minor bump inside the 3.x line unlocks it.

Spring Boot 4 and Spring AI 2.0 are now a parallel track. Neither blocks vector
search. The critical path to phase 2 is now:

`CHAT-dixbadbc` Kotlin 2.x, then `CHAT-hczlsjqu` JDK 25, then `CHAT-feodvffh` Vectors.

### Verified facts, checked on 2026-09-03 and 2026-09-05

- Boot 3.5.16 supports Java 17 to 25. It needs Spring Framework 6.2.19 or above.
  Source: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Vectors needs JDK 25. It needs `--add-modules jdk.incubator.vector`. The Vector
  API is still an incubator module. Source: https://github.com/integrallis/vectors
- `com.integrallis:vectors-spring-ai:0.1.19` declares no Spring AI dependency. The
  module uses `compileOnly` for Spring AI. The consumer BOM selects the version.
  So the adapter works with the pinned Spring AI 1.0.3. Spike `CHAT-uevmymkw`.
- Boot 3.5.16 manages testcontainers at 1.21.4. The removed override set the same
  value. `chat-shell` still resolves 1.21.4 from the BOM.
- The `lognet` gRPC starter is gone from the repo. It was the first named blocking
  risk for Boot 4. That risk is closed. Spring Shell is the last starter risk.

### Build proof at `b01d18e8`

- Default mode passes. The full reactor reports 29 modules SUCCESS.
- Integration mode passes. `build-health.sh --integration --online` exits 0.
- The integration verify at `dda66dd3` started 28 containers. It ran 736 tests
  with zero failures and 52 skipped. Docker Engine reported 29.7.2.
- `mvn -o -B validate` prints zero Maven model warnings.

### Traps found in this work

- **An offline build fails at `chat-webflux` after the Boot 3.5 move.** Boot 3.5.16
  pulls `org.springframework.restdocs:spring-restdocs-asciidoctor:3.0.6`. Boot
  3.3.13 managed an older version. A repository cache from before `dda66dd3` does
  not hold 3.0.6. The asciidoctor goal then fails. The tests all pass first, so the
  failure looks unrelated to the build. Prime the CI cache once after this commit.
- **`drift check` and `git diff --check` cannot prove a removal is complete.** Both
  inspect what changed. Neither reports what a task missed. The first `CHAT-yjmpjigt`
  commit left `chat-shell/pom.xml` untouched, and all three reported checks passed.
  A text search for the stale comment also passed, because `chat-shell` never held
  that comment. Only `dependency:tree` and the container tests proved the state.

### Open, not started

- `CHAT-dixbadbc` Kotlin 2.x scope. Next on the critical path. Its first input is
  the deprecation list from `CHAT-ornxhhcg`. Its named risk is the all-open
  compiler plugin behaviour under K2. Every Spring `@Configuration` class in the
  repo depends on that plugin.
- `CHAT-incuynpc`. Both testcontainers dependencies in `chat-shell` resolve at
  compile scope, not test scope. The pom declares no scope element. This predates
  wave 1. Removing the version pins made it visible in the dependency tree.
- `CHAT-pkolwuqm` Boot 4 and `CHAT-ygllyglb` Spring AI 2.0. Parallel track.

## RSocket index query payload binding (2026-09-06)

Issue `CHAT-fjhlnakc`.

Spring 6.2 exposed an index-controller type erasure bug. The deployed RSocket
index controllers used a generic `Q` query parameter. The server decoded query
payloads as `LinkedHashMap`. Lucene index services require `IndexSearchRequest`.

The controller layer now binds query payloads explicitly:

- Lucene index controllers bind `IndexSearchRequest`.
- Cassandra index controllers bind `Map<String,String>`.
- The unused generic controller and mapping interface are removed.

Proof:

- `mvn -o -B -pl chat-client-rsocket -am -Dtest=MessageIndexRequesterTests,KeyValueIndexRequesterTests -Dsurefire.failIfNoSpecifiedTests=false test` passed.
- `git diff --check` passed.
- `drift check` returned ok.

## JDK 25 vector runtime flags (2026-09-06)

Issue `CHAT-eroapfub`.

### What changed

- Added `chat-vector-embedded` as the narrow compile and test ground for the
  JDK Vector API.
- Added `--add-modules jdk.incubator.vector` only to `chat-vector-embedded`.
- Added `--enable-native-access=ALL-UNNAMED` to generated deploy runtime flags.
- Added the native access flag to the static memory integration image flags.
- Added the native access flag to legacy Redis deploy startup.
- Added the native access flag to GraalVM native build arguments.

### Flag reach

Keep the incubator Vector API flag narrow. Only code that compiles or loads
`jdk.incubator.vector` receives `--add-modules jdk.incubator.vector`.

Keep native access broad for deploy runtimes. Netty uses native access on Java
25. Future JDKs may block that path without `--enable-native-access=ALL-UNNAMED`.

### Proof

- `mvn -o -B -pl chat-vector-embedded test` passed.
- `shell-scripts/test-flags.sh` passed all 15 golden cases.
- The user reported the stacked `mvn -B clean verify -Ptest-build,integration -fae`
  proof passed.

## Toolchain modernization wave 2 (2026-09-05/06)

Wave 1 ended at Spring Boot 3.5.16 on Java 17. Wave 2 finished the toolchain.

| PR | Issue | What |
|----|-------|------|
| #64 | `CHAT-dixbadbc` | Kotlin 1.9.25 to 2.4.10 |
| #65 | `CHAT-hczlsjqu` | `java.version` 17 to 25, and both CI jobs to JDK 25 |

### Kotlin 2.x was five deletions

The scope was measured before it was written. A probe with
`-Dkotlin.version=2.4.10` failed five modules, and every failure read
`Language version 1.9 is no longer supported`. No failure named a Kotlin source
file.

The cause was five hardcoded `<languageVersion>` elements. Delete them and the
whole reactor compiles and tests clean. **No Kotlin source needed a change.**

Kotlin warnings fell from 214 to 183. They did not reach zero. An earlier claim of
zero came from searching for the string `warning:`, which Kotlin does not use.

### JDK 25 needed Boot 3.5, not Boot 4

Boot 3.3.13 supports Java 17 through 23. Boot 3.5.16 supports Java 17 through 25.
That single fact removed Spring Boot 4 from the critical path and is why wave 1
ended where it did.

`java.version` drives two things: the compiler release and `BP_JVM_VERSION` for
the images. Both moved together.

The CI change had to ship in the same pull request. A JDK 17 toolchain cannot
compile at release 25, so master breaks if the pom change lands alone.

### Traps

- **Do not check `java.version` with `mvn help:evaluate`.** The name is also a JVM
  system property and it shadows the pom value. The goal reported `21.0.2` while
  the pom said 25 and the compiler emitted class version 69. Read the class file
  instead. Java 17 is major version 61, Java 21 is 65, Java 25 is 69.
- **The build JDK and the image JRE differ.** The buildpack supplies its own Java
  25 runtime. It does not copy the build JDK. Both are Java 25.

## Message vector recall, embedded provider (2026-09-06/08)

`CHAT-feodvffh` is done. All seven children are done. This is phase 2 of vector
search.

| PR | Issue | What |
|----|-------|------|
| #69 | `CHAT-ciuqgqge` | The Vectors adapter, pinned at 0.1.20, with a compile proof |
| #70 | `CHAT-zsnzesqp`, `CHAT-sbpqlmki`, `CHAT-imnzrkci` | The configuration behind the selector, the `embedded` pair, and a boot test |

### What exists

`chat-vector-embedded` supplies a `VectorStore` when `app.service.core.vector` is
`embedded`. It uses `com.integrallis:vectors-spring-ai`, an in-process store built
on the JDK Vector API.

The adapter declares Spring AI as `compileOnly`, so the consumer BOM picks the
version. It binds to the pinned Spring AI 1.0.3. Spring Boot 4 and Spring AI 2.0
are not needed for it.

### Two decisions, both measured or stated

**Index: FLAT, cosine, no quantization.** Measured on this hardware at 256
dimensions and topK 50:

| Vectors | Build | Query |
|---------|-------|-------|
| 1000 | 62 ms | 0.165 ms |
| 10000 | 96 ms | 0.937 ms |
| 100000 | 541 ms | 8.295 ms |

`RecallRequestValidation` rejects a limit above 50, so topK 50 is the worst case.
Revisit above 100000 vectors in one collection, where the linear cost stops being
comfortable. Do not choose `CUVS_BRUTEFORCE` or `CUVS_CAGRA`; those are GPU index
types.

**Storage: rebuild on failure.** The owner decided this. The store is a derived
cache and the persisted messages are the source of truth. So the default path is a
temporary directory, container storage is ephemeral, and no deployment mounts a
volume. That also closes the `app.nodeid` question, because per instance storage
cannot be shared.

### Three choices the tasks did not name

1. The dimension comes from `embeddingModel.dimensions()`, not a property. A
   property can disagree with the model, and the collection rejects a wrong width
   only at the first add.
2. Both beans declare `destroyMethod = "close"`. `VectorCollection` and
   `JavaVectorsVectorStore` are `AutoCloseable`.
3. `chat-vector-embedded` declares `kotlin-stdlib` itself. It has no `chat-core`
   dependency, so it does not inherit the Kotlin runtime.

### The Vector API flag is not optional and does not degrade

A boot test that only asserted the bean type **passed** without
`--add-modules jdk.incubator.vector`. It never touched the distance kernels, so it
proved nothing.

Adding a real `add` and `similaritySearch` made it fail:

```
java.lang.NoClassDefFoundError: jdk/incubator/vector/FloatVector
  at com.integrallis.vectors.core.PanamaConstants.<clinit>
```

The library logs `provider panama unavailable, trying next`, then dies inside its
own fallback logging, because that path reads `PanamaConstants`. **There is no
scalar fallback.** A missing flag is a hard failure.

`VectorSelectorValidation` now rejects `vector=embedded` at startup when
`jdk.incubator.vector` is absent, so the failure lands at startup rather than at
the first recall. The check runs after the legal pair check.

### What phase 2 did not deliver

- **No deployment sets `app.service.core.vector`.** Every proof is a test.
- **No rebuild path.** `CHAT-oghjsnad` holds it for a following sprint.
- **Embeddings are still mock only.** `DummyEmbeddingModel` makes character bigram
  vectors at 256 dimensions. It is substring matching, not semantics. `local` and
  `gateway` are reserved and fail at startup. **The store is real. The vectors are
  not.** No issue tracks the real embedding model.

## Dependency hygiene (2026-09-08/10)

Seven pull requests, #72 to #78. `CHAT-mgtbicsq` is done.

| PR | Issue | What |
|----|-------|------|
| #72 | `CHAT-ixazwico` | Removed the `jvmTarget` 1.8 pin in `chat-index-elastic` |
| #73 | `CHAT-hcgmcuxp` | Removed inline pins below the Boot BOM, across 19 poms |
| #74 | `CHAT-pjyvoeil` | Moved the Cassandra driver to the `org.apache.cassandra` groupId |
| #75 | `CHAT-mgtbicsq` | Central `dependencyManagement`, a guard script, and the OWASP workflow |
| #76 | `CHAT-mgtbicsq` | The audit gate at CVSS 9, plugin pinned |
| #77 | `CHAT-xojupyhn` | `docs/DEPENDENCY-AUDIT.md` |
| #78 | `CHAT-mgtbicsq` | The two enforcer rules |

### One defect class, found three times

A stale local override that the build accepts in silence.

- `languageVersion` pins blocked Kotlin 2.4. Found only when the compiler rejected
  them.
- A `jvmTarget` 1.8 pin made `chat-index-elastic` emit **class version 52, which is
  Java 8**, inside a Java 25 build. The issue called it latent and harmless. It was
  not. Only reading the class file shows it.
- Inline versions beat the managed ones. `jackson-dataformat-cbor` was pinned at
  2.9.5 in five modules including `chat-core`, against a managed 2.21.4.
  `reactor-core` was pinned at 3.3.0.RELEASE against a managed 3.7.19.

Wave 1 removed five downward **properties**. It never looked at version elements
inside dependency declarations, which is where these lived.

### The rule, and three checks that hold it

No module declares a third-party version. A BOM managed artifact is declared with
no version. An unmanaged artifact takes its version from the parent
`dependencyManagement`.

- `shell-scripts/check-dependency-versions.sh`, or `just check-deps`, fails when a
  module pom declares a third-party version.
- `requireUpperBoundDeps` fails when a resolved version is lower than another path
  requires.
- `dependencyConvergence` fails when one artifact resolves to two versions.

The first catches a pin being written. The other two catch the tree drifting
underneath it. Both enforcer rules run in the `validate` phase in all 35 modules.

**A future enforcer failure is a real tree change, not noise. Fix it with a parent
entry, never a module pin.**

### Two corrections of record

- **The 106 conflicts were not 106 problems.** That count came from every
  `omitted for conflict` line in a verbose tree. Most are benign, because Maven
  already resolves the higher version. Only five artifacts violated a rule:
  `org.jetbrains:annotations`, `HdrHistogram`, `snakeyaml`, `nimbus-jose-jwt`,
  `httpclient`. Run the enforcer rule to count violations. Do not grep the tree.
- **`spring-boot-starter-oauth2-client:2.5.0` and `spring-security-messaging:5.4.6`
  are commented out.** A survey script read XML comments as declarations and
  reported a Boot 2 starter in a Boot 3.5 build. `dependency:tree` showed neither,
  and it was right.

### Traps

- **`native-protocol` kept the `com.datastax.oss` groupId.** Only the
  `java-driver` artifacts moved to `org.apache.cassandra`. A blanket groupId change
  breaks the build.
- **A plugin dependency must carry its own version.** `pluginManagement` does not
  cover it. The guard script excludes anything inside `<build>` for that reason.
- **A scoped Maven run can cache a failed lookup of a `com.demo` artifact.** Later
  runs then fail with an error naming `repo.spring.io` rather than the real cause.
  Clear it with `find ~/.m2/repository/com/demo -name '*.lastUpdated' -delete` and
  use the full reactor.

## The first dependency audit (2026-09-10)

Run `34425935490`. It took 56 minutes on a cold NVD cache and **failed at the CVSS
9 gate, which is the intended behaviour**. Eleven artifacts carry a finding at or
above 9.0. `CHAT-icgifzbv` holds the triage.

### This changes the reason for Spring Boot 4

Five of the worst findings sit inside versions that **Boot 3.5.16 chooses**, not
versions this project picks:

- `spring-core` 6.2.19: four findings at 9.8, one at 9.1.
- `tomcat-embed-core` 10.1.55: eight findings, two at 9.8.
- `spring-security-core` and `spring-security-oauth2-resource-server` 6.5.11:
  CVE-2026-59270 at 9.1.

**3.5.16 is the last release of the 3.5 line.** Maven Central lists no 3.5.17. So
there is no patch to take, and the only route to a fixed Spring Framework, Tomcat
and Spring Security is Spring Boot 4. Boot 4.1.1 is the current release.

`CHAT-pkolwuqm` was written as a currency upgrade. It is now security driven, and
its priority is high.

`CHAT-ygllyglb` is the same. Spring AI 1.0.3 carries CVE-2026-22738 at 9.8, and the
reason for that pin has expired. `CHAT-gidbchkx` is the same. Netty 4.1.135.Final
carries CVE-2026-56820 at 9.1 beside the `Unsafe` deprecation already recorded
there.

### One large false positive

`chat-persistence-xstream` reported nine XStream CVEs, including CVE-2021-21345 at
9.9. **The module has no XStream dependency.** dependency-check matched the module
artifactId against the XStream product CPE and read our version `0.0.1` as XStream
`0.0.1`.

Check `spring-cloud-consul-config` the same way. Its CPE reads
`spring_cloud_config`, a different product.

No suppression file exists. `CHAT-icgifzbv` adds one.

### Two smaller real findings

- `kotlin-stdlib-common` 1.9.22 is a stale transitive on a Kotlin 2.4.10 build.
  Find what drags it in.
- `kotlin-stdlib` 2.4.10 shows CVE-2026-53914 at 9.8 on the current release. Check
  whether a fixed version exists at all.

## The key-value index (2026-09-11)

PR #83, merge commit `f9129f28`. Issues `CHAT-sgdtqhof` and `CHAT-bgumjhdn`.

**Neither backend had a working key-value index.** The work started as a step
towards root-key discovery for the vector index job record. It stands on its own,
and the job design does not depend on it.

- The lucene index raised `ConverterNotFoundException` on its first write, in every
  composition. `IndexEntryEncoder.ofConversionService` converted the `KeyValuePair`
  wrapper, not the value. It also cast the field list to `List<Pair<String,
  String>>`. Neither mattered, because the only converter implements
  `com.demo.chat.convert.Converter`, and `Codecs.kt:12` shows that interface no
  longer extends the Spring one. The inheritance is commented out in place. A
  `ConversionService` never held that converter, and nothing referenced it.
- `CassandraIndexServices.KVPairIndex()` returned `DummyKeyValueIndexService`.
  Writes vanished and reads returned empty, with no error.

**The field contract is now shared.** `KeyValueIndexFields` in `chat-core` names the
index fields of one value type. `TypedKeyValueIndexFields` selects them by the
runtime class of a value. Both backends take the same instance, so one value type
indexes the same fields everywhere. A module registers its own type with a
`KeyValueIndexFieldsEntry` bean.

An unregistered type throws and names the registered types. It does not index
nothing. An index that silently stored no fields would answer every later query with
an empty result, and an empty result cannot be told apart from a real miss.

### Four facts that are expensive to relearn

1. **A key-value entry is mutable, so both indexes replace.** An insert alone leaves
   the entry of the earlier value, because a changed value writes a different
   document or a different primary key. A job that reached `SUCCEEDED` still
   answered a query for `RUNNING`. Both indexes now remove before they write.
2. **The cassandra index needs two tables.** `kv_pair_index` partitions on the field
   and the value. `kv_pair_index_by_id` partitions on the entity id, because `rem`
   receives only the entity key and cannot otherwise name the partitions that hold
   its rows. `TopicIndex.rem` is the cautionary case. It builds a row with an empty
   name, never matches, and index removal is quietly broken there.
3. **A lucene removal must match an exact term.** The `key` field is analyzed.
   StandardAnalyzer split a uuid into segments, and QueryParser joined them with OR
   inside one required group, measured on lucene 8.7 as `+(key:550e8400 key:e29b
   key:41d4 key:a716 key:446655440000)`. A document that shared one segment matched
   the group, so removing one uuid deleted every document that shared a segment.
   Every document now carries a `StringField` named `_key` that is not analyzed, and
   `rem` matches it with a `Term`. **A hyphen can mean NOT in other query positions.
   It created no prohibited clause for this input.**
4. **`_key` is reserved.** An encoder can name any field. The encoded field is
   analyzed and the internal field is not, so lucene refuses the document and
   reports `cannot change field "_key"`. A replacement removes before it writes, so
   the refusal arrives after the removal and the entry is lost.
   `requireNoReservedField` rejects it first. The key-value index checks before the
   removal, and `addEntry` checks again for a caller that adds without removing.

### Three smaller results

- **The fields are read before the removal, and once.** An unregistered type and a
  reserved name both fail without destroying the entry that the index holds.
  `LuceneIndex.addEntry` stores fields the caller already read, so the encoder runs
  once per add.
- **The authorization save path never stored anything.** It discarded both
  publishers inside `doOnNext`, so neither write received a subscription. It now
  subscribes both and blocks, like the two reads beside it.
- **The exact removal sits in the base index**, so every lucene index gained it. The
  blast radius is the user, message, topic, membership, and auth indexes, not the
  key-value one alone.

### Two test notes

- **`IndexTests.should save and find many` is now open.** A mutable index replaces
  the entry of a key, so two adds of one key give one result. The key-value subclass
  overrides it with two keys.
- **A random uuid pair cannot prove exact removal.** The tests use keys that share
  their first segment on purpose. A cassandra test that stores one value twice
  proves an upsert, not a replacement, so a changed-value test is a separate case.

### Deferred

`CHAT-aedloxwd` defines backend-neutral index field semantics. It is **not** a
request to remove the runtime check. A typed field name would move the validation,
not remove it. Only a closed and backend-neutral field namespace could remove it.
The current limits are recorded on that issue. Start it when one required semantic
exceeds the text pair model.

## CI action versions (2026-09-11)

PR #84, merge commit `b3d98cb2`. Issue `CHAT-xzhpuixd`.

`actions/setup-java` moved from v4 to v5 in three places. `maven.yml` has one per
job, and `dependency-audit.yml` has one.

The only breaking change in v5.0.0 is the node 24 runtime. A runner must be
v2.327.1 or newer. All three jobs run on `ubuntu-latest`, which meets that. v5
changes no input and no default.

**`dependency-audit.yml` is unproven.** It runs on a schedule, not on a pull
request, so PR #84 never exercised it. Its next scheduled run is the first proof.

v6 exists. It removes the legacy Adopt distributions, and it renames `jdkFile` to
`jdk-file` with an alias. Neither reaches this repository, because every job uses
`temurin` and no job uses `jdkFile`. A move to v6 is a separate decision.

## Cassandra CI flake, another occurrence (2026-09-11)

`CHAT-sgyaaivp`. Run `34647802262` on the PR #83 branch failed with the recorded
signature. `CassandraDriverTimeoutException: Query timed out after PT2S`, every
failure in `chat-persistence-cassandra`, and a failing class set that differs from
the one recorded on master. A rerun of the same commit passed.

Four runs on that branch, on code that only moves forward: three passed and one
failed.

**One caution for whoever fixes it.** PR #83 adds two tables to `keyspace-long.cql`
and `keyspace-uuid.cql`. Those scripts run in the test setup that times out. The
same schema was present in all four runs and three passed, so the added tables are
not sufficient to cause the failure. Four runs cannot show whether they raise its
probability. Do not read this as proof that schema size has no effect.

## Vector reindex, work in flight (2026-09-11)

`CHAT-oghjsnad`. Nothing of this is on `origin/master`.

- The branch is `chat-oghjsnad-vector-reindex`, in `.worktrees/vector-reindex`, head
  `cd993995`. It has no remote upstream.
- **The design documents are local only.** Three documentation commits sit on the
  main checkout's `master` at `af30dfd2` and were never pushed. `origin/master` does
  not hold the spec or the plan.
- The branch was rebased onto `f9129f28` after PR #83 merged. Two superseded
  key-value commits were removed, because they carried the defective versions of
  code that PR #83 fixed. The recovery branch is
  `recovery/vector-reindex-pre-pr83-rebase` at `74eaf254`.
- `CHAT-bvmevhpq`, `CHAT-mwvhqoyt`, and `CHAT-iqtgwcqa` are done on that branch.
- **One design decision is open and blocking.** The coverage rule compares a covered
  generation with a current generation, and two values carry that name. The
  in-process counter restarts at zero on every process start, so a clean earlier run
  and a fresh process both read zero. Under `trust=stored` that grants coverage with
  no evidence. Both values must come from the durable record, or the in-process
  counter must be seeded from the covering job.
- Two items were deferred out of that design. `CHAT-edzvpxil` holds the message
  handling policy mask. `CHAT-tekzakdd` holds a data stream or data view strategy,
  so a rebuild stops reading every stored message.

**Read every line above as of 2026-09-11.** Three of them no longer hold.
Measured on 2026-09-15: the branch has a remote upstream, and PR #87 carries it
against `master`. The blocking generation decision is closed, because the
durable job record supplies both values. The spec and the plan are on the
branch. See `Vector index job record` and `Embedding provider modules` below.

## The AGENTS.md imports (2026-09-11)

`AGENTS.md` imported `@FP_AGENTS.md` and `@continuity_brief.md`. Neither file
existed on `origin/master`. The repository holds `FP_CLAUDE.md` and
`forward-register.md`, so both imports were dangling.

`@continuity_brief.md` now reads `@forward-register.md`. That file exists, and it is
the register that `AGENTS.md` tells a reader to treat as live operational context.

**`@FP_AGENTS.md` still names a file that `origin/master` does not hold.** It is left
alone on purpose. The main checkout holds an uncommitted rename of `FP_CLAUDE.md` to
`FP_AGENTS.md`. That rename resolves this import, and it belongs to the owner. A
change from this branch would collide with it.

## Vector index job record (2026-09-11/13)

Issue `CHAT-fpwpfrfj`, under `CHAT-oghjsnad`. Fifteen tasks. Spec:
`docs/superpowers/specs/2026-09-11-vector-index-run-record-design.md`. Plan:
`docs/superpowers/plans/2026-09-11-vector-index-job-record.md`.

Recall now reports the coverage of the newest trusted successful rebuild job,
rather than the phase of the active job.

### What exists

- `IndexJob` and `JobRecord`, each with its own root key. A job owns one
  persisted topic, and the topic key is the job root key.
- `VectorIndexJobStore` writes the job through the key-value store, and it
  creates the topic through persistence, the topic index, and `pubsub.open()`.
- `ComposedJobRecordWriter` publishes progress messages. It never calls
  `MessagingServiceImpl.send()`, so a job record never enters vector recall.
- `VectorCoveragePolicy` selects the covering job. `app.vector.index.trust`
  takes `none` or `stored`, and `none` is the default.
- `MessageRecallResult` carries `indexComplete` beside the bounded hits. Both
  transports answer with one object.
- `VectorIndexStartupAction` releases stale jobs, selects coverage, adopts it,
  and only then starts a rebuild when `app.vector.index.startup` is `rebuild`.
- The `vectorindex` actuator endpoint returns the status and the recent jobs.

### Four production defects that the tests found

Three took their own issue. The fourth was repaired inside `CHAT-fpwpfrfj`, in
revision `c7dac773`, because the owner review found it before Task 8 started and
it sat inside the store that task built. None was repaired with a special case
in the test that found it.

1. `CHAT-jhfptxiw`. `topicIdToQuery` named `TopicIndexService.ID`, and the
   message index stores a destination under `topic`. **Persisted room history
   had never been returned from the index, on either backend.** Live delivery
   hid it, because `listenTopic` concatenates the pub/sub stream after the
   history.
2. `CHAT-muuaovqn`. The embedded provider refuses a repeated document id, so
   every rebuild after the first one failed in one process. `VectorWriteMode`
   now follows the provider. Only `embedded` removes before it writes, because
   a removal that removes nothing makes `RedisVectorStore` log an error.
3. `CHAT-auglbxrm`. `JsonNodeToAnyConverter` had no null branch, so a null node
   became the four character string `"null"`. Redis and RSocket both failed to
   read an `IndexJob` back. A `String?` field would have taken that text in
   silence.
4. The root key of a stored job was never checked. A record under key A holding
   key B would make the policy adopt B while A stayed clean. `readJob` compares
   the two keys now.

### Rules that are easy to lose

- **A repair keeps the coverage of the older successful job.** So the index
  reports complete while a repair runs, and the `SUCCEEDED` filter must run
  before the sort. Filtering after it would select the repair and report no
  coverage.
- **The write mode is per provider, and an unknown selector is refused.** A
  default would give a new provider a policy with no decision, and the wrong
  policy is silent on three of the four.
- **The remove and write pair is not atomic, and the interval has no duration
  bound.** It runs from the completion of the removal to the completion of the
  write, which includes the embedding call and the provider commit. The index
  reports complete throughout, so this is an accepted false positive.
- **The claim generation cannot identify a run.** An invalidation raises it
  during the same run, so `markActiveJob` guards on the running flag instead.
- **An actuator operation may answer with a publisher.**
  `ReactiveWebOperationAdapter` unwraps a `Mono`, so nothing blocks. A
  `block(Duration)` throws outside the chain, where `onErrorResume` cannot see
  it.
- **An operator must enable an actuator id and expose it.**
  `management-defaults.yml` disables endpoints by default, so exposure alone
  answers 404.
- **A scoped `-pl` run resolves upstream modules from `~/.m2`.** A stale jar
  there reports a missing bean that the current source defines. Boot tests need
  the full reactor.

### What this work did not deliver

- **No deployment sets `app.service.core.vector`.** Every proof is a test.
- **Embeddings are still the mock model.** `DummyEmbeddingModel` makes character
  bigram vectors. `local` and `gateway` still fail at startup.
- **No job topic retention.** The first version reads every matching job topic.
- **No deployment sets the actuator id or the exposure value.**
- `CHAT-edzvpxil` the handling policy mask, and `CHAT-tekzakdd` a data stream in
  place of the full scan, both stay out of scope.

### Open, low priority

- `CHAT-aoqghxmf`. An illegal selector pair fails with a missing provider bean
  rather than the validation message, because the validation runs after regular
  singleton creation.
- `CHAT-cxduiwjj`. One reindex test is timing sensitive. It failed once under
  load and passed every later run.

### Gate at the end of the work

- Default mode: 750 tests, 0 failures, 30 skipped.
- Integration mode: 964 tests, 0 failures, 52 skipped.
- `drift check` and `git diff --check` pass.

## Where the next session starts

The owner's direction on 2026-09-10: **move on to features. Make a security pass
after vector lands.**

### Vector is a provider, not yet a feature

Three things stand between what exists and something usable. The first has no
issue.

1. **A real embedding model.** No issue tracks it. `local` and `gateway` are
   reserved and fail at startup. The open decision is `local`, meaning in process
   with a large model dependency and CPU cost, against `gateway`, meaning a network
   hop, credentials, and a new failure mode on the recall path. **That decision is
   the owner's.**
2. **A deployment that sets the selector.** No yml sets
   `app.service.core.vector`.
3. **The rebuild path.** `CHAT-oghjsnad`.

### Are the embedding model and the reindex parallel

Mostly yes. The file overlap is one line in `VectorSelectorValidation.legalPairs`.

**They collide on a decision, not on code.** The reindex trigger, meaning a startup
check against an operator command against a lazy rebuild, depends on how long a
rebuild takes. The benchmark measured 541 ms for 100000 vectors **excluding
embedding time**. Embedding is free with the mock model and will dominate with a
real one. A `local` model and a `gateway` round trip give very different answers,
and one of them makes rebuild at startup untenable.

So the reindex **mechanism** is parallel safe and can be built against the mock
model. The reindex **trigger** is not, and should be a separate task that lands
after the embedding model reports a throughput number.

### Open issues at high priority

**This list was written on 2026-09-11 and it is stale.** Four of its six
entries are done: `CHAT-pkolwuqm` Boot 4, `CHAT-gidbchkx` Netty,
`CHAT-icgifzbv` audit triage, and `CHAT-ygllyglb` Spring AI 2.0.
See the ordered list at the end of this file, written on 2026-09-21.

`CHAT-sgyaaivp` Cassandra CI flake and `CHAT-cikgeefc` build health are still
open.

Open and not yet started, from the 2026-09-11 work: `CHAT-aedloxwd` index field
semantics, `CHAT-edzvpxil` message handling policy mask, `CHAT-tekzakdd` data stream
or data view for message scans. All three are deliberately deferred.

`CHAT-sgyaaivp` is worth doing before any Cassandra work. While the integration job
alternates red on unchanged code, every review has to re-derive whether a failure is
real.

The capability mechanism `CHAT-zqyrsrrg` still has six decomposed tasks and has not
been started since the original design sprint.

## Embedding provider modules (2026-09-14/15)

`CHAT-etfnihnu`. Spec:
`docs/superpowers/specs/2026-09-13-embedding-provider-modules-design.md`. Plan:
`docs/superpowers/plans/2026-09-14-embedding-provider-modules.md`. Operator
document: `docs/EMBEDDING-PROVIDERS.md`.

### The defect this work closes

`chat-deploy-memory` declared the `chat-core` test jar with no scope element,
so it resolved at compile. The only `EmbeddingModel` in this repository lived
in that jar, which means the recall feature ran on a mock in every test and
would have failed at a launch.

**No test could detect it.** A Spring Boot test puts test jars on its classpath
by construction, so every test saw a model that no deployment would have.

### What exists now

- `chat-embedding-openai` supplies an `EmbeddingModel` when
  `app.service.core.embedding` is `openai`. It reaches any OpenAI-compatible
  endpoint through a base URL.
- `chat-embedding-local` supplies one when the value is `local`. It loads an
  ONNX model in the process.
- Neither module depends on a Spring AI starter. A starter carries
  auto-configuration, both modules sit on one classpath, and two starters would
  let Spring AI build models that no selector asked for.
- Both deployments declare both modules. `chat-deploy-memory` moved its test
  jar to test scope.
- The legal selector pair set grew from four to ten. A mock vector store
  refuses a production model.

### The identity

`app.service.core.embedding.identity` names the model that wrote a corpus, and
**the operator names it.** The code never derives one, because a base URL and a
model name do not identify the output of a remote service. A compatible service
can change its model behind both values and return different vectors for the
same text.

The value reaches five call sites: the redis index name, the embedded
collection directory, the local resource cache directory, every new `IndexJob`,
and the coverage filter.

**A record written before this change carries null, and null never covers.** So
the first start after this change reports an incomplete index and waits for a
rebuild. Every existing redis index and every existing embedded directory
orphans, because neither name carries an identity segment.

### Two gates, and why each exists

`just check-production-classpath` holds two rules. Rule one reads the poms and
requires test scope on every test-jar dependency. Rule two resolves the runtime
classpath of each module and refuses a known test library there.

**Neither rule replaces the other.** A transitive leak never appears in a pom.
An ordinary jar such as `testcontainers` is not a test jar, so rule one cannot
see it either.

`shell-scripts/vector/gate-embedding-launch.sh` runs the feature outside a test
classpath. It packages a deployment, asserts that no test output is inside the
artifact, launches it against a synthetic OpenAI endpoint, seeds, rebuilds, and
searches. **Every test gate in this repository puts test outputs on the
classpath, which is what hid the original defect.** That is the whole reason
this gate exists, and it earned its place on its first run by finding a defect
that no test could see.

### Traps found, each of which cost a cycle

- **A `@JvmInline value class` cannot be a Spring bean.** Kotlin unboxes a value
  class at a return type, so the `@Bean` method compiled to
  `embeddingIdentity-Xzz6TTw()` returning `java.lang.String`. `EmbeddingIdentity`
  is a data class for that reason.
- **A `SmartInitializingSingleton` check runs too late.** It runs after every
  singleton exists, so an incomplete selector pair failed with
  `NoSuchBeanDefinitionException` and an illegal pair loaded an 86.2 MiB model
  before anything reported the pair. The selector check is a
  `BeanFactoryPostProcessor` now.
- **`TransformersEmbeddingModel` implements `InitializingBean`.** A factory
  method that also calls `afterPropertiesSet` loads the model twice and builds
  two ONNX sessions.
- **Kotlin nests a block comment.** An actuator path pattern inside a KDoc opens
  a nested comment, and the file fails to compile with `Unclosed comment` at the
  last line.
- **`conda activate base` does not put miniforge first on the PATH here.** A
  bare `python3` resolves to the homebrew interpreter. Both new scripts name
  `$CONDA_PREFIX/bin/python3` and refuse anything outside miniforge.
- **The `expose-webflux` profile cannot run in a reactor build.** It declares
  its dependencies on the parent, so `chat-webflux` reads itself as a dependency
  and maven stops before it builds. `-pl` does not avoid it. `CHAT-xwycjyla`
  holds it. **Fixed on 2026-09-16. See the section below.**
- **A Spring AI client sends a chunked request body.** A stub that reads only
  `Content-Length` sees no input, answers a vector for no text, and the caller
  fails inside `dimensions()`. Reactor Netty also pools connections, so a
  HTTP/1.0 stub breaks the next request on that connection.
- **The default Spring AI retry policy needs 19 minutes to give up.** Ten
  attempts make nine waits of 2, 10, 50, and then six of 180 seconds.
  `app.service.core.embedding.openai.max-attempts` exists so a test of a dead
  endpoint can set 1.
- **The REST recall route could not start beside the RSocket controllers.**
  `MessageRecallController` implements `MessageRecallService` by delegation, so
  two beans of that type existed on one classpath. `CoreRecallBeans` closes it,
  and every controller takes that interface now.

### What this work did not deliver

- **No deployment yml sets the vector or embedding selectors.** Every
  composition still starts with the feature off.
- **No automated run calls a paid service.** Both real model procedures are
  manual, and `docs/EMBEDDING-PROVIDERS.md` carries them.
- **The house endpoint serves 768 and only 768.** A request for a narrower
  width answered 768, and this provider does not truncate.

## Vector index status contract (2026-09-16)

`CHAT-cxduiwjj`. Spec:
`docs/superpowers/specs/2026-09-16-vector-index-status-contract-design.md`.
Plan: `docs/superpowers/plans/2026-09-16-vector-index-status-contract.md`.

### The defect

`MessageReindexServiceImpl.finishRun` clears the running flag with a
synchronous `state.finish`, then returns a chain that emits a record and only
then writes the durable job. So `running=false` arrives before the durable
record is terminal.

**Three readers assumed those were one fact.** The test helper
`awaitFinished`, both scenarios of `docs/VECTOR-RECALL-API.md`, and the
packaged launch gate. Two tests raced that window and failed three times in
eight runs, and once in CI on master.

The gate passed by luck rather than by correctness. It asserted that the newest
job carried the identity, and `createJob` stamps the identity on the RUNNING
record too, so it read the right value from the wrong record.

### The contract

`running` and `complete` report in-process state. `IndexJob.outcome` is the
durable fact after a restart. `SUCCEEDED`, `FAILED`, and `RELEASED` are
terminal, and **only `SUCCEEDED` proves that a rebuild did its work**.

The production ordering did not change. `state.finish` still runs first, so a
failed durable write cannot leave a run active forever.

### The one production change

`start()` answers with `VectorIndexTriggerResult`, which carries `accepted`
beside the status. The status could not carry that fact: a rejected trigger and
an accepted one both report `running=true`, because the running claim belongs to
another run in the first case. A reader that could not tell them apart waited
for a job that no call had created, and then reported a missing durable record.

The read operation keeps its status and jobs shape. A read never asks whether it
started anything, and a wire test pins that separation.

### Two bounds, and two reports

A reader waits under an outer bound for the run and an inner bound for the
durable write. An expired outer bound says the run did not finish. An expired
inner bound says the run ended with no durable record. **A reader that bounded
only the second one would hang on a run that never ends.**

### Traps found while building it

- **`Mono.block(Duration)` throws its own timeout error rather than returning
  null.** An elvis after it never runs, so a helper that used one could not name
  which bound expired. Use `timeout` with an explicit error.
- **A reactor stream carries no null**, so a poll of a nullable store reads
  through `Mono.justOrEmpty`.
- **The job store holds the RUNNING record from the start of a run.** A failed
  terminal write leaves that record in place, so an assertion reads the outcome
  rather than the emptiness of the store.
- **A trailing operator on `Mono.defer` removes the inferred type.** The lambda
  branches then need an explicit one.
- **A double trigger does not prove the rejection path.** A rebuild of three
  messages finishes before the second call arrives, so the second trigger is
  accepted. That mutation exited 0 three times out of three. The deterministic
  proof feeds the reader a rejected answer instead.

### Measured

- The class passes 12 times out of 12. The same loop failed 3 of 8 before.
- Default build: 808 tests, 0 failures, 0 errors, 30 skipped.
- Integration build: 1027 tests, 0 failures, 0 errors, 55 skipped.
- Five gates each exit 0.

## The expose profiles (2026-09-16)

`CHAT-xwycjyla`.

### The defect

Four profiles declared their dependencies on the parent pom: `expose-rsocket`,
`expose-webflux`, `expose-gateway`, and `e2ee`. Every module inherits a parent
dependency, so each profile made one module a dependency of itself, and maven
stopped the whole reactor before it built anything.

```
'dependencies.dependency.[com.demo:chat-webflux:0.0.1]' for
com.demo:chat-webflux:0.0.1 is referencing itself
```

Measured on 2026-09-16: all four failed, and each named its own artifact.
`-pl` does not avoid it, because maven reads every module of the reactor first.

**The defect stayed hidden because no launch path builds a reactor.**
`chat-build` runs maven with `cwd` set to one module directory, so the reactor
never forms and the self reference never appears.

### The fix

The four profile ids stay on the parent and carry no dependency. Each of the
five deploy modules declares the same ids with the dependencies it needs.

- `chat-deploy` declares all four in full. It holds none of those artifacts.
- The four backend modules already declare `chat-service-controller` and
  `shared-deploy-configuration` at compile scope, so their `expose-rsocket`
  profile is empty and their `expose-webflux` profile adds `chat-webflux`
  alone.

**An empty profile is deliberate.** The id must exist in the module that a
single module build names, or `-Pexpose-rsocket` prints an activation warning.

### Two facts that cost a measurement

- **A profile dependency overrides the scope of a base declaration.**
  `chat-deploy` declares `chat-webflux` at test scope for
  `SecurityChainOwnershipTests`. Its `expose-webflux` profile declares the same
  artifact with no scope, and the effective pom then reports compile. Maven
  prints no duplicate warning. That is what the profile is for, and the test
  scope still holds without it.
- **`-Pdeploy` with `-am` nested one fat jar inside another.** The deploy
  profile sets the Boot repackage skip to false for every module of the reactor
  it runs in. A run of `-pl chat-deploy-memory -am` repackaged `chat-deploy` as
  its own executable jar, and that 49 MiB file landed in `BOOT-INF/lib` of the
  artifact. **Fixed on 2026-09-17 by the exec classifier. See the section
  below.**

### One dependency pin

`chat-webflux` reaches `asm` 9.7.1 through `oauth2-oidc-sdk` and
`accessors-smart`. The cassandra driver reaches `asm` 9.2 through `jnr-ffi`,
and maven picks the nearer 9.2. `requireUpperBoundDeps` fails on that pair,
which meets only under `-Pexpose-webflux` on a cassandra deployment. The parent
pins 9.7.1, which is the rule this repository already follows for five other
artifacts.

### Measured

- Each of the four profiles passes `mvn -o -B -P<id> validate` over the
  reactor, and all four together pass.
- Each profile puts its artifact on the runtime classpath of the module that
  needs it. Without the profile, `chat-webflux` stays off the memory runtime.
- A single module build from the module directory still works, and it prints no
  activation warning.
- The launch gate drops its `-f` workaround for a scoped `-pl` command, and it
  exits 0.
- Default build: 808 tests, 0 failures, 0 errors, 30 skipped.
- Integration build: 1027 tests, 0 failures, 0 errors, 55 skipped.

## The exec classifier (2026-09-17)

`CHAT-dasmldiw`.

### The defect

The root `deploy` profile repackages every module of the reactor it runs in,
because a profile on the parent reaches them all. The repackaged jar replaced
the plain one, so a module that is both an application and a library shipped as
a fat jar to anything that depended on it.

`chat-deploy` is exactly that module. It declares a main class, and the four
backend deploy modules depend on it. A run of
`-pl chat-deploy-memory -am -Pdeploy` gave a 229 MiB artifact whose
`BOOT-INF/lib` held `chat-deploy-0.0.1.jar` at 49 MiB.

### Why the obvious fix does not work

`CHAT-dasmldiw` proposed gating the repackage on a property that only launchable
modules set. **That does not fix this case.** `chat-deploy` is launchable, so it
would set the property and repackage anyway, and the nesting would return.

The property that matters is not "does this module declare a main class". It is
"is this module a dependency of another module in this build", and maven offers
no way to express that in plugin configuration.

### The fix

The repackage takes `<classifier>exec</classifier>`. The executable artifact
becomes `<module>-0.0.1-exec.jar` and the plain `<module>-0.0.1.jar` stays the
library. An upstream module then reaches `chat-deploy` as a 71 KiB library,
whatever profile is active.

Measured on 2026-09-17: the same `-am` command now gives 180 MiB, and
`BOOT-INF/lib/chat-deploy-0.0.1.jar` is 71 KiB.

### What follows the classifier

- **`java -jar` names the `-exec` jar.** The launch gate and the operator
  procedure in `docs/EMBEDDING-PROVIDERS.md` both do.
- **`spring-boot:run` and `spring-boot:build-image` read the same plugin
  configuration**, so both follow the classifier without a second entry. The
  plugin descriptor carries the parameter on both goals.
- **`chat-build` needs no change.** It runs `spring-boot:run` and
  `spring-boot:build-image`, and it never names a jar.
- **The `test-build` profile of `chat-deploy-memory-integration-test` is
  untouched.** It configures the plugin inside its own module, so the root
  profile never reaches it. That module pinned the plugin at 3.5.12 until
  2026-09-17. `CHAT-cwsuybox` removed the pin, so the module now uses the
  managed 3.5.16.

### The payoff

The launch gate builds with one command again, `-pl chat-deploy-memory -am`,
because `-am` is safe now. It ran two commands before: an install of the whole
reactor, then a scoped package.

### Measured

- `-pl chat-deploy-memory -am -Pexpose-webflux,deploy` gives a 180 MiB
  executable holding a 71 KiB `chat-deploy`.
- The launch gate exits 0 with 3 hits and `indexComplete` true.
- Default build: 808 tests, 0 failures, 0 errors, 30 skipped.
- Integration build: 1027 tests, 0 failures, 0 errors, 55 skipped. That run
  builds the test image, which is the image path this change could have broken.
- Every expose profile and the deploy profile pass a reactor validate.

## The Spring Boot 4 prerequisites (2026-09-17)

`CHAT-pkolwuqm` is a scope issue and it is done. Four of its six children are
merged, and **every one of them landed on Spring Boot 3.5.16**. That is the
result the scope work bought.

| PR | Issue | What |
|----|-------|------|
| #94 | `CHAT-ndqihvzh` | Reactor 3.8.7 and the JSpecify non-null bounds, 61 source files |
| #95 | `CHAT-vjsbfecx` | Testcontainers 2 coordinates, 17 declarations in 9 modules |
| #96 | `CHAT-eaobicll` | `spring-boot-starter-aop` removed, which Boot 4 does not publish |
| #97 | `CHAT-jpvjwvje` | The gateway artifacts renamed to the webflux form |

### The scope was measured with three probe builds

1. Boot 4.0.8 with the Cloud train 2025.1.3: the reactor could not read 12
   projects, and every failure named an artifact the new BOMs no longer manage.
2. The same, coordinates repaired: the build reached the compiler and stopped in
   `chat-core` with 44 errors in 6 files, all one cause.
3. Boot 3.5.16 with `reactor-bom` 2025.0.7 alone: **the same 44 errors in the
   same 6 files.**

Probe 3 is why the work split the way it did. The largest part of the move was
separable, so it left the critical path.

### Reactor 3.8 is a Kotlin change, not a Reactor upgrade

Reactor 3.8 adopts JSpecify. `Mono` and `Flux` annotate their type parameter as
non-null, so Kotlin infers the bound `T : Any`. **Reactor requires no Kotlin
syntax.** Kotlin reads the annotations and applies the stricter bound.

61 source files took a bound and 64 lines added `: Any`. The cascade converged in
eight compile rounds, because a bound on a base interface reaches every
implementor.

**The route matters more than the version.** `reactor-bom` 2025.0.7 also carries
`reactor-netty` 1.3.7, which declares Netty 4.2.17.Final. Boot 3.5.16 manages
Netty 4.1.135.Final and the Boot management wins, so the BOM route would run
reactor-netty below the Netty line it requires. The parent names four artifacts
instead: `reactor-core`, `reactor-test`, `reactor-kotlin-extensions` and
`reactor-extra`. reactor-netty and Netty do not move.

### Two source changes were not bounds

- `MemoryTopicPubSubService.sendMessage` used `map` with a nullable sink lookup.
  **`Mono.map` rejects a null from its mapper at runtime**, so a missing sink
  would have thrown there. The call is `doOnNext` now, and nothing read the emit
  result.
- `Mono.toFuture()` completes with null for an empty Mono, so Reactor 3.8 types
  it nullable. `SerialWriterTests` asserts the signal is present.

### Testcontainers 2 renames every module artifact

`cassandra` becomes `testcontainers-cassandra`. The core artifact keeps its name.

**A clean compile was not evidence.** The first probe compiled with zero errors
while two generations sat on one test classpath.
`com.playtika.testcontainers:embedded-cassandra` names the old
`org.testcontainers:cassandra` coordinate at 1.17.6, which the 2.x BOM does not
manage. Both jars ship
`org/testcontainers/containers/CassandraContainer.class`, so the compiler read
whichever came first. Only `dependency:tree` showed it.

Removing playtika exposed two defects older than that task. `chat-index-cassandra`
had never declared Testcontainers and took the class transitively, and
`UserIndexTests` imported the legacy 3.x driver that PR #74 had migrated away
from.

### The AOP starter was dead weight

`spring-boot-starter-aop` carries `spring-boot-starter`, `spring-aop` and
`aspectjweaver`. No source in this repository uses AspectJ. `spring-aop` arrives
through `spring-context` anyway. **Every `@PreAuthorize` sits in `chat-security`,
which has never held `aspectjweaver`**, so the method security path proves the
weaver is not needed.

`AopAutoConfiguration$ClassProxyingConfiguration` is annotated
`@ConditionalOnMissingClass("org.aspectj.weaver.Advice")`, read from the compiled
class. Boot activates that branch without the weaver and still forces class
proxying, so the proxy strategy does not change.

### The gateway rename has a migration window

`spring-cloud-gateway-server` publishes 2.2.6.RELEASE through 4.3.5 and stops.
`spring-cloud-gateway-server-webflux` publishes 4.3.0 through 5.0.3. Both exist
between 4.3.0 and 4.3.5, and that window is what let the rename land early.

At 4.3.5 the new artifact is a 3456 byte forwarding jar that depends on
`spring-cloud-gateway-server`. At 5.0.x it becomes the real artifact.

### What stays for the parent bump

`CHAT-tvsgtjtm` carries the parent and two coordinate changes that cannot move
earlier.

- **The train.** `spring-cloud-commons` 5.0.3 requires `spring-security-crypto`
  7.0.7, and Boot 3.5.16 manages 6.5.11. `requireUpperBoundDeps` refuses it.
- **The contract BOM.** Spring Cloud Contract is still inside the 2025.0.3 train
  at 4.3.4. It leaves the train at 2025.1.x.
- **Spring AI 2.0**, `CHAT-ygllyglb`. `spring-ai-model` 2.0.1 depends on
  `spring-messaging` 7.0.9, so it cannot ship separately.

`CHAT-gidbchkx` is closed by measurement rather than by silence. Netty 4.2.2 adds
the fall-back and 4.2.3 the preferred path. Boot 3.5.16 manages 4.1.135.Final and
is the last 3.5 release, and Boot 4.0.0 manages 4.2.7.Final. **The
`sun.misc.Unsafe::allocateMemory` warning still prints today.**

### What is still unmeasured

**Everything past `chat-core`.** All three scope probes stopped there on the
reactor generics. So no module after it has compiled against Spring Framework 7
or Spring Security 7. Do not read the absence of a finding as a clean module.

### A deeper probe, run on 2026-09-17 after the four prerequisites merged

Measured at master `d4349022`, with Boot 4.0.8, Cloud 2025.1.3, contract 5.0.3,
Spring AI 2.0.1 and Spring Shell 4.0.3. Throwaway branch, nothing committed.

The reactor **reads completely** now. The first scope probe could not read 12
projects. This one could not read 1, `reactor-kafka`, which neither new BOM
manages. One parent entry at 1.3.25 closed it.

Two results follow.

1. **`chat-core` main compiles against Spring Framework 7.** No module had
   reached that point before. The failures are all in `chat-core` **test**
   sources, so 32 modules still skip and Spring Security 7 stays unmeasured.
2. **Boot 4.0.8 manages JUnit Jupiter 6.0.3**, up from 5.12.2, and JUnit 6
   adopts JSpecify. Five `Mock*Resolver` classes in `chat-core` declare
   `supportsParameter(param: ParameterContext?, ext: ExtensionContext?)` with
   nullable platform types, and those no longer override. `resolveParameter`
   must also return `Any?`. That is 30 of the 32 compile errors.
   **This is the same shape as the Reactor 3.8 work**: a library adopts JSpecify
   and Kotlin infers stricter types. The repair is mechanical.

The other two errors sit in `SpringAiApiProbe`, where Spring AI 2.0 expects
`Map<String, Any>` and the probe passes `Map<String, Any?>`.

One enforcer violation appeared before the compiler: Spring AI 2.0.1 requires
micrometer 1.17.1 and Boot 4.0.8 manages 1.16.7. A `micrometer-bom` import at
1.17.1 in the parent closes it, which is the rule this repository already
follows.

Jackson is the smaller risk than it first appeared. Boot 4.0.8 manages both
lines, `jackson-bom` 3.1.5 and `jackson-2-bom` 2.21.5, so the 69 files that
import `com.fasterxml.jackson` may still compile. The risk moves to the
auto-configuration default, and `DomainWireShapeTests` and `E2eeWireShapeTests`
are the readers that matter.

## The Spring Boot 4 stack, state on 2026-09-19

This section describes the stacked Boot 4 branches, not master. The combined
branch is `boot4-gate-probe`. `boot4-bump` stays paused at `179c2fd8`.

### The gate reading

`build-health.sh` exits 0 and reports that reality matches
`docs/BUILD-HEALTH.md`. 36 modules succeed, 1 fails and 0 are skipped, of 37.
27 modules run tests, giving 829 tests with 0 failures, 0 errors and 30
skipped.

The one failure is `chat-index-elastic`, which B11 records and which the owner
parked. **An exit code of 0 here does not mean every module builds.** It means
the failures match the document.

The owner ran the integration profile on 2026-09-19. 26 modules ran 1043 tests
with zero failures and zero errors. `chat-persistence-redis` passed 57 tests
against a Redis container, `chat-vector-redis` passed 4 Redis Stack tests, and
`chat-deploy-redis` passed 6.

### A client contract changed, and it is breaking

**Every OAuth client of `chat-authorization-server` must now implement PKCE.**

`ClientSettings.builder()` calls `requireProofKey(false)` at Authorization
Server 1.5.8 and `requireProofKey(true)` at 7.0.7, read from the bytecode of
each. `RegisteredClientFactory` never sets the value, so every registered
client takes the default.

A client that sends no `code_challenge` gets 302 to its redirect uri with
`error=invalid_request` and `OAuth 2.0 Parameter: code_challenge`.

**The owner kept the new default on 2026-09-19.** This is the OAuth 2.1
direction and the stronger posture. It is recorded here because no code change
marks it, and a reader of `RegisteredClientFactory` sees no mention of PKCE.
`CHAT-qvyptrcv` holds the decision.

### Three claims that have no direct evidence

Each is open on purpose. Do not quote any of them as verified.

1. **reactor-kafka against kafka-clients 4.1.2.** `CHAT-ajehabnw` repaired a
   Kafka failure, and that repair was `AdminClient` alone. It measured nothing
   about reactor-kafka. Neither enforcer rule fires on the pair, because one
   version resolves and no path asks for more. `CHAT-hazcatpc` still owns this.
2. **Deployment injection of the Jackson 3 domain module.**
   `RSocketServerStrategiesTests` drives the production customizer and passes
   the module list by hand. No test shows that a deployment supplies it. The
   component scan reaches `ChatJackson3Modules`, and that is read from source
   and never run. `CHAT-rmfuqcwi`.
3. **The RSocket CBOR path.** It has no test and it has not been read. The
   Jackson 3 repair reached the JSON path only. `CHAT-bgsqwjph`.

### One production behaviour that stays unmeasured

`AuthorizationCodeFlowTests` gives its principal a `FactorGrantedAuthority`,
because `user(TEST_USER)` never runs the login filter and Security 7 reads
`auth_time` from the authorities. That repair proves the fixture path. **The
production `formLogin` path is not measured by it.**


## Spring Boot 4 landed, and the seven pull requests after it (2026-09-19/21)

The stack described in the section above merged. Everything below is on
`master`.

| PR | Merge | What |
|----|-------|------|
| #103 | `c4b63bfc` | Spring Boot 4.0.8, the whole stack |
| #104 | `ecfea4a1` | Pub/sub reader failure cleanup made race safe and visible |
| #105 | `8d7d9707` | Jackson 3 module injection proved in the RSocket deployment |
| #106 | `6aae087d` | `chat-index-elastic` removed |
| #107 | `d09a2daf` | Tomcat 11.0.26, Kotlin 2.4.20, the stale `kotlin-stdlib-common` dropped |
| #108 | `d1dda1bc` | The two audit false positives suppressed. First green dependency audit |
| #109 | `0bdb0c4f` | The Spring Shell 4 command surface verified |
| #110 | `7abe0af8` | `build-health.sh --ci`, a verifier mode that builds the image |

### The build surface now

- Default: 36 modules, 849 tests, 0 failures, 0 errors, 30 skipped.
  The count moved from 844 when `CHAT-hazcatpc` added three tests, and to 849
  when `CHAT-cophllrg` added two.
- `--ci`: 27 modules run tests, 1082 tests, 0 failures, 0 errors, 54 skipped.
  Measured on 2026-09-22 against Docker Engine 29.7.2. The count moved from
  1078 when PR #126 added two Cassandra tests, and to 1082 when
  `CHAT-cophllrg` added two.
- The reactor holds 36 modules. `chat-index-elastic` left it under #106.

### The verifier reaches the image now

`build-health.sh --ci` runs `mvn clean verify -fae -Ptest-build,integration`.
The package phase builds the `chat-deploy-memory-integration-test` image, so
`chat-shell` tests the current source rather than whatever image the machine
holds.

**It is not the CI command, and the document says so.** It takes the phase,
the profiles and the image path of the workflow integration job. It adds
`-fae`, because the verifier must see the result of every module to diff it
against `docs/BUILD-HEALTH.md`. It resolves online, as that job does and as no
other mode does.

### Two B4 children were already closed by other work, and one was not

Measured on 2026-09-21 at `7abe0af8`. The first two looked open but were done.
`CHAT-cophllrg` remains open.

Closed:

- `CHAT-aazmjsws`, the Kafka topic existence test: `KafkaPubSubTests` runs 9
  tests with 0 failures. Kafka 4 is KRaft only, and
  `createTopics().all().get()` no longer proves broker metadata visibility.
  `KafkaTopicAdmin.create` ends with `awaitVisible`, which polls 10 seconds at
  50 ms.
- `CHAT-cjqzmiiq`, the Redis pub/sub selector tests:
  `RedisPubSubBeansSelectorTests` runs 4 tests with 0 failures.

**Still open, and it stays open:**

- `CHAT-cophllrg` is half done, and the open half is the half it was written
  for. `CoreUserDetailsService.updatePassword` refuses a null password and the
  KDoc records the owner decision, but **no test pins the refusal**.
  `UserDetailsServiceTests` has no null case. The implementation reached
  master from `boot4-bump`, whose tip commit names this issue.

One more closed on the same day, outside the B4 tree: `CHAT-ygllyglb`, the
Spring AI 2.0 scope. The parent pom imports `spring-ai-bom` 2.0.1, so the
upgrade shipped inside #103 and the scope question is moot.

**A closed parent does not close its children.** `CHAT-tvsgtjtm` is done while
five of its children stayed open, and two of those were already satisfied by
code that merged under a different issue.

## Three build traps, each of which cost a cycle (2026-09-17)

These are tooling traps, not code defects. Each one reports success, or reports a
failure that names the wrong thing.

1. **`-Dmaven.test.skip=true` also skips test jar creation.** An image rebuild
   then fails with `Could not find artifact com.demo:chat-core:jar:tests`, and a
   following `-Pintegration` run passes **against the previous image**. The green
   test count says nothing about which image it used. Use `-DskipTests`, which
   compiles the tests and produces the test jars, and **read the exit code of the
   image build before you report an integration result.**
   **Met a second time on 2026-09-21.** `check-production-classpath.sh` installed
   the reactor with the same flag, so the install could not resolve
   `chat-persistence-cassandra:jar:tests` for `chat-index-cassandra`. The gate
   passed for as long as a stale test jar sat in `~/.m2`, and it failed the first
   time that jar was absent. The script uses `-DskipTests` now. A gate that
   depends on the local repository holding an artifact its own run does not
   produce is not a gate.
2. **Surefire hides the cause of a discovery failure.** It prints `TestEngine
   with ID 'junit-jupiter' failed to discover tests` and nothing else, in the
   maven log and in `target/surefire-reports/*.dumpstream`. The real cause, a
   `NoClassDefFoundError` in this case, sits in
   `target/surefire-reports/*.dump`. Read that file.
3. **A local repository is not evidence about a remote one.** An offline build
   failed to resolve a jar, and the incomplete `~/.m2` directory beside a
   `.lastUpdated` marker was read as proof that the artifact ships no jar. Maven
   Central served it. A `.lastUpdated` file records a failed fetch, not an absent
   artifact. Check the remote repository before you conclude anything about an
   artifact.

## The Boot 4 dependency entries (2026-09-17)

`CHAT-mndvipyh`, B4-S6. Measured on a throwaway branch at master `c1d98900`.
**Nothing merged**, because every entry binds only under Boot 4.

**The reactor validates on Boot 4.0.8 with zero enforcer violations.** Five
validate rounds reached it. The task was written expecting two entries. The
answer is thirteen, plus one correction.

| Entry | Version | Why |
|---|---|---|
| `reactor-kafka` | 1.3.25 | neither new BOM manages it |
| `micrometer-bom` import | 1.17.1 | spring-ai-model 2.0.1, over a managed 1.16.7 |
| `spring-data-redis` | 4.1.1 | spring-ai-redis-store 2.0.1, over a managed 4.0.7 |
| `spring-data-keyvalue` | 4.1.1 | cascade |
| `spring-data-commons` | 4.1.1 | cascade |
| `jedis` | 7.4.1 | spring-ai-redis-store, over a managed 7.0.0 |
| `httpclient5` | 5.6.3 | elasticsearch-rest5-client 9.2.9, over a managed 5.5.2 |
| `httpcore5`, `httpcore5-h2` | 5.4.3 | cascade |
| three `victools` artifacts | 5.0.0 | convergence inside Spring AI |
| `error_prone_annotations` | 2.41.0 | convergence under openai-java-core |

### The correction, and it is the fourth of its kind

**`nimbus-jose-jwt` moves from 10.0.2 to 10.4.** That property is an existing
parent pin, added under `CHAT-mgtbicsq` to settle three conflicting versions.
Under Boot 4 it becomes a **downgrade**, because spring-security-oauth2-jose
7.0.7 requires 10.4.

This repository has now met the stale local override four times: the
`languageVersion` pins, the `jvmTarget` 1.8 pin, nineteen inline versions, and
now a parent pin that a framework move overtook. **A parent entry is the right
mechanism and it is not permanent.** Every pin needs re-reading when the BOM
under it moves.

### Three findings

1. **A pin inside a family cascades.** `spring-data-redis` alone broke the
   Spring Data set, and two more entries followed. The cleaner route is a
   `spring-data-bom` import, but the train carrying redis 4.1.1 is later than
   the one Boot 4.0.8 manages, and a newer Spring Data train under Boot 4.0.8
   carries the same risk as the Cloud train. The three pins are measured. The
   BOM import is an alternative that needs its own measurement.
2. **Spring AI 2.0.1 ships an internally divergent tree.** `spring-ai-model`
   pulls victools jsonschema 5.0.0 while `openai-java-core` 4.49.0 pulls 4.38.0.
   Four of the thirteen entries exist only to converge Spring AI with itself.
   Most builds never see this. `dependencyConvergence` does.
3. **Neither enforcer rule fires for reactor-kafka against kafka-clients.** Boot
   4 manages kafka-clients 4.1.2 and reactor-kafka 1.3.25 declares 3.9.1. An
   upgrade keeps `requireUpperBoundDeps` quiet, and one resolved version keeps
   `dependencyConvergence` quiet. **A clean validate says nothing about that
   pair.** The kafka container tests are the only evidence, and reactor-kafka is
   discontinued at its 1.3 line. See `CHAT-hazcatpc`.

### What a clean validate does not prove

It runs the enforcer and compiles nothing. The five module repairs under B4-S1
to B4-S5 are untouched by this result, and the twelve modules that no probe has
reached stay unmeasured. See `CHAT-ombbesyh`.

## The null password refusal, and the defect beside it (2026-09-22)

`CHAT-cophllrg`. The test is
`chat-authorization-server/src/test/kotlin/com/demo/chat/BlockingUserDetailsPasswordServiceTests.kt`.
It reads the contract through the blocking adapter, which is where a
deployment reads it.

### The success path was broken in shipped code

`AuthenticationService.setAuthentication` answers `Mono<Void>`, which completes
empty. `CoreUserDetailsService.replaceCredential` used `map` on that signal, so
**the whole success path answered an empty Mono**. The blocking adapter reports
`IllegalStateException` for an empty signal, so every accepted password upgrade
failed there. `replaceCredential` uses `thenReturn` now.

The reactive path degraded more quietly. Spring reads an empty answer as no
upgrade, so a password upgrade was dropped with no error.

### The control test found it, and the control is the point

An assertion that no credential was written proves nothing on its own. A
fixture that can never record a write would satisfy it. The second test makes
the same fixture record a write, and that test is what failed.

### Two readings that were wrong

1. **`UserDetailsServiceTests` has never run.** It carries `@Disabled` and no
   class extends it. Item 3 of `CHAT-cophllrg` says the existing chat-security
   tests cover the non-null path. They cover nothing. The file is still in the
   tree, and it still reads as coverage. The owner decides whether it stays.
2. **A refusal can degrade into a different refusal.** Removing the
   `switchIfEmpty` branch still fails the null case, because the adapter
   reports `IllegalStateException` for an empty signal. The message then names
   no cause. The test asserts the exception type for that reason.

### Measured

- Default: 27 modules ran tests, 849 tests, 0 failures, 0 errors, 30 skipped.
- `--ci`: 27 modules ran tests, 1082 tests, 0 failures, 0 errors, 54 skipped.
- Both report no drift. Mutation proof: removing the refusal fails the null
  test, and restoring `map` fails the control test.

## The image repository prefix (2026-09-22)

`CHAT-gkwqnnxn`. The last open child of `CHAT-efzzemjx`.

`chat-build core --cassandra --build` could not build an image. The parent pom
read `${env.IMAGE_REPO_PREFIX}` with no default, so the image name held an
unresolved property and the goal refused it.

**One launch path defaulted the value and the other did not.**
`shell-scripts/build.sh` exports `docker.io/library`. `chat-build` exports
nothing, and `chat-build` is the documented launch path.

### The repair sits in the pom, not in the launch script

The parent defaults `image.repo` to `docker.io/library`. The
`image-repo-from-env` profile activates on the environment variable and takes
that value instead.

A second export in `chat-build` would have repaired one path. The pom default
repairs every entry point, including a bare maven command. Four modules
already name `docker.io/library` directly, so the default agrees with them.

The profile is not `activeByDefault`, so it does not meet the rule that
removed `noartifact`. It sets one property and changes no packaging.
`chat-gateway` carries an `activeByDefault` profile in its own pom, and a
parent profile does not reach it, because that rule is per pom.

### Measured on 2026-09-22

- With the variable unset: `docker.io/library/cassandra-core-service-rsocket:0.0.1`.
- With `IMAGE_REPO_PREFIX=harbor.lan/chat`: `harbor.lan/chat/cassandra-core-service-rsocket:0.0.1`.
- `chat-build core --cassandra --build --node-id 1 --notls` exits 0 with the
  variable unset, and the image manifest reads
  `Start-Class: com.demo.chat.ChatApp`.
- `shell-scripts/test-flags.sh` matches all 15 golden cases. The emitted maven
  command did not change, because the repair is in the pom.

### One trap found while reproducing it

**`mvn spring-boot:build-image` reports BUILD SUCCESS and builds nothing.**
The parent sets `<skip>true</skip>` in the plugin level configuration, which
reaches every goal of that plugin rather than repackage alone. A run on
`chat-deploy-redis` finished in 3.6 seconds and produced no image, with no
skip message. Every documented launch path passes `-Pdeploy`, which sets
`skip=false`. `CHAT-xtnrgvpi` holds it.

## The work queue, ordered on 2026-09-21

26 issues are `todo` and 2 are `in-progress`. The order below is a
recommendation, not a decision. The owner sets the order.

Three issues closed while this list was written, each because merged work had
already satisfied it: `CHAT-aazmjsws`, `CHAT-cjqzmiiq` and `CHAT-ygllyglb`.
`CHAT-cophllrg` was read the same way and it stays open, because its test is
missing. **Read the tree before you start an issue.** A closed parent does not
close its children.

### Tier 1: open risk sitting in merged code

| # | Issue | Why it is first |
|---|-------|-----------------|
| 1 | `CHAT-hazcatpc` | reactor-kafka 1.3.25 declares kafka-clients 3.9.1 and master runs 4.1.2. reactor-kafka is discontinued at its 1.3 line. Neither enforcer rule fires on the pair. Only the narrow protocol path is measured, and that measurement is deliberately scoped. This is unmeasured risk in shipped code. |
| 2 | `CHAT-ombbesyh` | The Boot 4 evidence boundary. **Most of it is now satisfied**: the `--ci` run compiles the full reactor, builds the image, and passes every container test. What stays open is starting the five composition roots, `just check-production-classpath`, and the packaged launch gate. Cheap now, and it removes the caveat that no deployment claim about Boot 4 is valid. |
| 3 | `CHAT-cophllrg` | One test. `CoreUserDetailsService.updatePassword` refuses a null password, and nothing pins that refusal. A credential contract with no regression guard. |

**`CHAT-vgujmnol` duplicates `CHAT-ombbesyh`.** Both ask for the composition
roots to start and the images to rebuild on Boot 4. Merge them or close one.

### Tier 2: trust in the signal

| # | Issue | Why |
|---|-------|-----|
| 4 | `CHAT-sgyaaivp` | The Cassandra container flake. While the integration job alternates red on unchanged code, every review has to re-derive whether a failure is real. Do it before any Cassandra work. |
| 5 | `CHAT-cikgeefc` | The standing build-health tracker. It does not close. A cycle that finds no drift is a clean reading. |

### Tier 3: correctness and design debt with a known defect behind it

| # | Issue | Why |
|---|-------|-----|
| 6 | `CHAT-ltvfmcvh` | The anonymous identity is decided in at least three places, and one of them cannot tell an expired token from a caller who never authenticated. Security relevant. |
| 7 | `CHAT-cvdcfczj` | Define and verify the complete authorization surface. |
| 8 | `CHAT-avduuqwp` | A stable root identity on keys, replacing string matching between `IKeyService.kind` and `RootKeys`. Large and structural. Read the scope before committing to it. |
| 9 | `CHAT-ruduojeu` | Backend fanout semantics for messaging. |

### Tier 4: native image

`CHAT-arcqfjuc` blocks a native start and needs an owner decision about moving
`main` out of the Kotlin companion object. `CHAT-ulrkvfit` adds the missing
`chat-shell` native profile. Neither blocks anything else.

### Tier 5: deferred on purpose

The capability mechanism `CHAT-zqyrsrrg` and its seven tasks have not moved
since the design sprint. `CHAT-aedloxwd`, `CHAT-tekzakdd` and `CHAT-edzvpxil`
are deliberately deferred and each records why. `CHAT-qwjuwcdo`,
`CHAT-itvhzmvp`, `CHAT-btjtfwwr`, `CHAT-uwsmwcpj` and `CHAT-xojupyhn` are
housekeeping.

### One caution about this ordering

It ranks by risk to shipped code, then by trust in the signal, then by design
debt. It does **not** rank by feature value. The owner's direction on
2026-09-10 was to move on to features after the security pass. That pass is now
done, so the next feature decision, which is the real embedding model, still
has no issue and is still the owner's.
