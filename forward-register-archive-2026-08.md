# Forward Register Archive — 2026-08

Sections of `forward-register.md` for 2026-08-25 to 2026-08-31, kept
verbatim. They moved here on 2026-10-15, when the live file gained a 64 KB
limit. Nothing imports this file.

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

