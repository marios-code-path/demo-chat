# Build Health

Known build-time deficiencies, what causes them, and what they take down with them.

**Verified against `master` `98e9cad9` on 2026-09-17** by three verifier modes — default, `--install` and `--integration` — each reporting no drift, against Docker Engine 29.7.2.

**The `--ci` mode was measured on 2026-09-20** at `chat-bahmtzut-verifier-image`, against Docker Engine 29.7.2. It exits 0 and reports no drift. 27 modules run 1075 tests, with 0 failures, 0 errors and 54 skipped.

**`chat-index-elastic` is gone.** It left the module list under
`CHAT-gdtktbfh` so the Boot 4 work could land with a green CI, and
`CHAT-zdqubyue` then removed it. The B11 row in Resolved carries the reason.
Measured on 2026-09-20 at `chat-zdqubyue-dropelastic`.

Do not trust this file on its own — run the verifier:

```bash
./shell-scripts/build-health.sh            # test phase, offline
./shell-scripts/build-health.sh --install  # includes package/install
./shell-scripts/build-health.sh --integration # also runs the container-backed tests
./shell-scripts/build-health.sh --ci          # the CI command, which builds the image first
```

It runs the build, diffs the failing modules against the list below, and exits non-zero when the two disagree — reporting anything **NEW** (failing but undocumented), **RESOLVED** (documented but passing), or **SKIPPED** (never built, so unknown). When it complains, update this file; that is the maintenance loop.

## Current state

`mvn clean test -fae` — **BUILD SUCCESS**. No module fails and nothing is
skipped. The reactor holds 36 modules and reports 844 tests, 0 failures, 0
errors and 30 skipped.

Plain `mvn -B clean test`, which is the command CI runs, also reports
**BUILD SUCCESS**. That matters: CI does not read `KNOWN_FAILING`, so a
module that the verifier tolerates would still hold CI red.

The install phase was not measured at this branch head. The `test-build` profile controls image builds. Ordinary builds do not need a
Docker daemon. The integration verifier runs container tests and checks that
the known-failure list matches the measured reactor.

Measured on 2026-09-19 at `chat-mumfjoau-pubsubdelivery` `091dbbea`, default
mode reported 832 tests, 0 failures, 0 errors and 30 skipped. It ran 802 tests.
Integration mode reported 1056 tests, 0 failures, 0 errors and 55 skipped. It
ran 1001 tests. Both gate runs reported no failure-list drift.

`CHAT-rmfuqcwi` adds two default tests that boot an rsocket composition root.
`CHAT-czmjffen` added eight integration tests before them, four per Redis
backend. Both gates ran on 2026-09-20 at
`chat-rmfuqcwi-rsocketmodule`. Default mode reports 836
tests, 0 failures, 0 errors and 30 skipped, so it runs 806. Integration mode
reports 1069 tests, 0 failures, 0 errors and 55 skipped, so it runs 1014. Both
exit 0 and report no failure-list drift. These are measured counts, not counts
derived from an earlier run.

A test moves the default count only when its class carries no `integration`
tag. The eight Redis tests of `CHAT-czmjffen` did not. The rsocket and shell
tests do.

The earlier measurement on 2026-09-17 reported 818 tests with 30 skipped in
default mode and 1037 tests with 55 skipped in integration mode. That default
count moved from 808 after `KafkaSenderContractTests` added six tests and
`KafkaReceiverContractTests` added four. `CHAT-hazcatpc` carries the reason.
The earlier `--install` mode reported the same counts as default mode.
`build-health.sh` prints these counts on every run, so a later reader measures
them rather than trusts this paragraph.

The reactor holds 36 modules and 27 of them run tests. It held 37 until
`chat-index-elastic` was removed. See B11. `chat-embedding-openai`
and `chat-embedding-local` are the two newest. Each supplies one
`EmbeddingModel` behind one value of `app.service.core.embedding`. See
`docs/EMBEDDING-PROVIDERS.md`.

These two modules appear here as prose and not as a row in the table below.
That table records a deficiency, and it carries the columns Deficiency, Blocks,
and Status. Neither module is a deficiency, and a row would have to leave all
three columns empty or false. Task 10 of the embedding provider plan asked for
a row, and this is the deliberate departure from it.

Note what the default run no longer covers. Since #32 the container-backed tests are tagged `integration` and excluded unless `-Pintegration` is passed. The measured baseline shows 207 more tests run in integration mode than in default mode. `chat-shell` passing by default means its tests did not run — not that B2 is fixed. Since #54 a local plain build still has that gap, but CI no longer does: the integration job runs `mvn -B clean verify -Ptest-build,integration` on every pull request and every master push, so the container half is checked per commit. The job is informational until 10 runs are recorded. See CHAT-uortzsbx for the baseline.

The integration gate runs the container tests. It reports no failing module
and no skipped module. Its exit status still means that the result matches
this file, rather than that every module passes, and that distinction stays
worth keeping even while every list is empty. `chat-shell` tests run and
pass. All three failure lists, `KNOWN_FAILING`, `KNOWN_FAILING_INSTALL` and
`KNOWN_FAILING_INTEGRATION`, are empty and measured.

Read the `chat-shell` skip count with care. A `-Pintegration` run of that module reports 56 tests with 22 skipped, which looks like absent coverage and is not. Each `@Disabled` sits on a generic base class, and surefire discovers those as test classes in their own right and reports them skipped. Measured on 2026-09-21 at 56 with 22 skipped, the skipped classes are `ShellUserCommandsTests` with 8, `ShellPubSubCommandsTests` with 5, `ShellTopicCommandsTests` with 4, `ShellLoginCommandsTests` with 4, and `ShellContextTests` with 1. JUnit does not inherit `@Disabled`, so the concrete `Long*` subclass runs. The 34 that do run include every container-backed one, against the singleton container `ShellIntegrationTestBase` starts from the `chat-deploy-memory-integration-test` image, and the ten command surface tests that `CHAT-fxrwtvef` added, which need no container.

`ShellContextTests` is the one disabled class that is not a generic base. Enabling it fails on a missing `CompositeServiceBeans` bean, measured on 2026-09-21, which is a gap in that test's own property set rather than anything about the shell commands.

| ID | Deficiency | Blocks | Status |
|----|-----------|--------|--------|
| B6 | Stale `target/` across branch switches produces phantom results | correctness of any non-clean run | Workaround only |
| B10 | A bare `-pl` run reads a changed upstream module from `~/.m2` | correctness of a scoped run that omits a changed module | Workaround only |

---

### B6 — a stale `target/` directory after a branch change gives false results

**Symptom.** `mvn test` without `clean` runs the compiled test classes in `target/test-classes`. A different branch put those classes there. They do not agree with the production code in the tree.

This occurred during the selector work. Three tests failed. No source file in the tree contained those tests.

**Mitigation.**

1. Use `mvn clean test` after you change branches.
2. The verifier script always cleans. It is not affected.

---

### B10 — a bare scoped run reads a changed upstream module from the local repository

**Scope.** This is a bare `-pl` run that omits a module the branch changed.
`-pl <module> -am` builds the upstream modules from source and is not affected.
A scoped run that names every changed module is not affected either.

**Symptom.** `mvn -o -pl <module> test` resolves every module it does not name
from `~/.m2`. A jar there can predate the tree. The run then reports a failure
that the current source does not have, and it names a bean or a symbol that the
tree defines.

This occurred twice during the vector index job record work. A run of
`-pl chat-core,chat-deploy,chat-deploy-memory` reported
`No qualifying bean of type MessageReindexService`. The bean existed in
`chat-service-composite`, which the run did not name, and the installed jar was
two days old. A second run of `-pl chat-deploy-memory` alone reported
`Unresolved reference VectorIndexEndpoint` for the same reason.

This is the mirror of B6. B6 is a stale build output inside the tree. B10 is a
stale build output outside it.

**Mitigation.**

1. Run the full reactor for any test that starts a deployment context.
2. Add `-am`, or name every changed module, when a scoped run is unavoidable.
   `mvn -o -B -DskipTests install` first has the same effect.
3. **Measure before classifying.** A failure that names a symbol the tree
   defines is a candidate for B10, and not a diagnosis. Repeat it in a clean
   full-reactor run. A failure that survives that run is real.

---

## Resolved

Kept so the list can be trusted — an entry disappearing without explanation is indistinguishable from an entry being forgotten.

| ID | Deficiency | Fixed by |
|----|-----------|----------|
| B1 | Two defects stacked. `spring-boot-maven-plugin` 3.3.x hardcodes Docker API v1.24 and Docker Engine 29 requires v1.40, so the image could not be built; and the image it *would* have built did not boot, because the module's pom baked bare `app.service.core.*` selectors that activate nothing. Plugin pinned to 3.5.12 for that module, selectors given values, and image building moved behind `-Ptest-build` so no ordinary build needs Docker. The pin is removed on 2026-09-17 (`CHAT-cwsuybox`). The parent manages Spring Boot 3.5.16, which negotiates the API version, so the module takes the managed plugin. | #39 |
| Cycle | `chat-deploy`'s `<backend>-backend` profiles pulled `chat-deploy-<backend>`, which depends on `chat-deploy` — a reactor cycle for memory, cassandra and kafka. Two mechanisms composed the same thing in opposite directions. The profiles are removed and `chat-build` targets the `chat-deploy-<backend>` module directly. | #42 |
| B2 | `ShellIntegrationTestBase` called `setWaitStrategy(...)` *after* `start()` had returned, so it never applied and `start()` fell back to a port check — satisfied when the port binds, not when the app can serve. The root-key fetch then raced startup and failed with `Connection reset`, intermittently and by test order. The pattern it would have used, `"*Netty RSocket started*"`, was shell globbing rather than a regex, so each defect hid the other. Now `waitingFor(...)` before `start()`, with a valid pattern and a realistic timeout. | #40 |
| B3 | `joinRestRoom` and `leaveRestRoom` declared `id: T` with no annotation, so Spring treated it as a model attribute and failed to instantiate the erased type variable — `IllegalStateException: Insufficient type information to create instance of ?`. Adding `@PathVariable` binds from the URI template instead. Not an authentication problem, which was the first hypothesis. | #34 |
| B4 | `AuthorizationServerDeployTests` failed on a missing `server_keycert.jwk`. The fixture is generated by `gen-dckeys.sh` and copied into test resources, but nothing in the build runs that script, so the test passed only where someone had run it by hand. The key is now generated per run into a temp file, with `app.oauth2.jwk.path` pointed at it — EC P-256, because the token customizer signs with ES256. | #36 |
| B5 | CI ran `mvn clean test-compile` and never executed a test, so a green check meant only that the code compiled. It now runs `mvn -B clean test`, which was safe once B4 made the default reactor green. A second job adds the container half: `mvn -B clean verify -Ptest-build,integration`, with the image built in the same reactor before chat-shell runs. | #38, #54 |
| B7 | `chat-deploy-memory` declared `chat-service-controller` at `test` scope, since 2023-10-12. `spring-boot:run` uses the runtime classpath, so the controllers and the `chat-security` password encoder config were absent. Direct launch failed on a missing `PasswordEncoder` bean. Tests hid this because surefire uses the test classpath. The image module, cassandra, and kafka all used compile scope; memory was the outlier. | #57 |
| B8 | `send --topicName` in `PubSubCommands` looked the room up by name, discarded the result, and sent with the `topicId` option — which still held its default `_`. Parsing `_` as a key threw `NumberFormatException` client-side. No test covered `send` at all, so it survived untouched. | #59 |
| B9 | All four pubsub provider beans (memory, redis-pubsub, redis-xstream, kafka) constructed a **new** `TopicPubSubService` on every `pubSubService()` call. The composite topic service, the composite message service, and the pubsub controller each got a different instance. `MemoryTopicPubSubService` keeps sinks and membership in instance maps, so a room opened in one instance was invisible to a send on another — every send failed server-side with `Object not Found`. The providers are now `@Configuration` with `@Bean` on `pubSubService()`, matching `MemoryPersistenceServices`. Proven by the new `LongPubSubCommandsTests`, 2/2 green. | #59 |
| B11 | `chat-index-elastic` did not compile under Boot 4. It left the module list first, because CI runs plain maven and never reads `KNOWN_FAILING`, so a module the verifier tolerates still holds both CI jobs red. The module is now removed. It had no dependents, no pom declared it, and no deployment ever selected it. The repair that was never taken sat on `chat-urhjrwbt-indexelastic`. | #103, then CHAT-zdqubyue |
| R1 | `KotlinModule` named-constructor form is a compile error under the jackson version Spring Boot 3.3.13 manages. `chat-client-rsocket` failing test-compile stopped the reactor and took `chat-deploy-redis`, `chat-shell` and `chat-authorization-server` down as SKIPPED. | #13, #22 |
| R2 | Modules declared `org.testcontainers:cassandra` at 1.21.4 but Spring Boot's BOM pinned the core `testcontainers` artifact at 1.19.8, whose `docker-java` 3.3.6 cannot negotiate with Docker Engine 29.x — reported as the misleading "Could not find a valid Docker environment". | #23 |

R2 moved `chat-persistence-cassandra` from 41 tests with 15 errors to 71 passing, and `chat-index-cassandra` from 8 tests with 4 errors to 26 passing.

## One-time notes

- **Only `--ci` builds the container image.** `build-health.sh --integration`
  runs `mvn clean test -fae -Pintegration`, which stops at the `test` phase.
  The image is built in `package` by `chat-deploy-memory-integration-test`
  under `-Ptest-build`. So `--integration` runs the `chat-shell` tests against
  whatever image the machine already holds, and it prints a note that says so.
  `--ci` runs `mvn clean verify -fae -Ptest-build,integration`, which is the
  command in `.github/workflows/maven.yml`. It builds the image in the same
  reactor before `chat-shell` runs. Measured on 2026-09-20: the image took a new
  tag at 22:10:45, and the `chat-shell` surefire reports were written at
  22:10:56. So the image build precedes the container tests inside one reactor,
  which is the thing no earlier mode could show. `chat-shell` reported 56 tests
  with 22 skipped, and the four `Long*` classes ran. See CHAT-bahmtzut.
- **Boot 4 reads `~/.docker/config.json` before it pulls the builder image.**
  `DockerRegistryConfigAuthentication` is new in the Boot 4 line. A config that
  holds a `credsStore` together with empty `auths` entries makes the build fail
  with `'username' must not be null`, and the message names no registry. Boot
  3.5.x did not read the file this way. This is a property of the developer
  machine and not of this repository. Measured on 2026-09-19: the same build
  succeeds with `DOCKER_CONFIG` pointed at a directory holding `{}`.
  `DOCKER_HOST` has nothing to do with it.

- The first build after R2 needs network access: `org.testcontainers:database-commons:1.21.4` is not in a local repository that predates the bump, so `mvn -o` fails until it is fetched once.
