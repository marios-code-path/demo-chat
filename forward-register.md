# Forward Register

What is carried forward out of the 2026-08-23/24 session: decisions made, work
landed, work planned but not started, things deferred to an issue, and claims that
are load-bearing but unverified.

Written at the handoff point after the capability spec and its first plan. Nothing
in this file is authoritative on its own — each row points at the artifact that is.

## Where things stand

| | |
|---|---|
| Checkout | `origin/master` head `934a3287`. The main checkout is **not** clean. It sits at `af30dfd2`, it holds uncommitted files, and it holds three unpushed documentation commits. See the vector reindex section. |
| Register state | Updated 2026-09-11, after PR #86 |
| Last merged PR | #86, merge commit `934a3287`. #83 `f9129f28`, #84 `b3d98cb2`, and #85 `6e3c4e36` came before it. #85 and #86 were squash merged. |
| Merged feature branches | None remain. **The earlier claim that this repository has no auto-delete is wrong as of 2026-09-11.** A merge now removes the remote branch. The seven older refs that this row used to list are gone, and so are `origin/chat-kv-index`, `origin/ci-setup-java-v5`, `origin/docs-agents-import`, and `origin/docs-register-refresh`. `git ls-remote --heads origin` returned `master`, `chat-eroapfub-vector-runtime-flags`, and two dependabot refs, and nothing else. |
| Worktrees | Two. The main checkout, and `.worktrees/vector-reindex` (active). The finished worktrees were removed with their branches. |
| Open PRs | dependabot only (#8, #11). Nothing of ours is in flight. #10 is superseded by PR #73. |

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

`CHAT-pkolwuqm` Boot 4, `CHAT-ygllyglb` Spring AI 2.0, `CHAT-gidbchkx` Netty,
`CHAT-icgifzbv` audit triage, `CHAT-sgyaaivp` Cassandra CI flake, `CHAT-cikgeefc`
build health.

Open and not yet started, from the 2026-09-11 work: `CHAT-aedloxwd` index field
semantics, `CHAT-edzvpxil` message handling policy mask, `CHAT-tekzakdd` data stream
or data view for message scans. All three are deliberately deferred.

`CHAT-sgyaaivp` is worth doing before any Cassandra work. While the integration job
alternates red on unchanged code, every review has to re-derive whether a failure is
real.

The capability mechanism `CHAT-zqyrsrrg` still has six decomposed tasks and has not
been started since the original design sprint.
