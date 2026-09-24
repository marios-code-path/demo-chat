# Build Health

Known build-time deficiencies, what causes them, and what they take down with them.

**Verified against `master` `98e9cad9` on 2026-09-17** by three verifier modes — default, `--install` and `--integration` — each reporting no drift, against Docker Engine 29.7.2.

**The `--ci` mode was measured on 2026-09-22** at master `67762d51`, against Docker Engine 29.7.2. It exits 0 and reports no drift. 27 modules run 1135 tests, with 0 failures, 0 errors and 54 skipped. The same mode reported 1080 at master `c6c21f12` and 1082 at master `67762d51`. The count was 1075 on 2026-09-20. `CHAT-hazcatpc` added three tests, and the default count records those three. `CHAT-sgyaaivp` added two tests that carry the `integration` tag, so only `--ci` runs them. `CHAT-cophllrg` added two tests. `CHAT-ltvfmcvh` replaced its eight diagnosis tests with seven policy tests, `CHAT-gtebuipo` added one, `CHAT-qucgqaye` added six, `CHAT-vehpbvzn` added seven, `CHAT-zhjltbky` added three, and the grant order clock added twenty nine. `--ci` resolves artifacts online, so the measured command is what `just check-ci` runs.

**`chat-index-elastic` is gone.** It left the module list under
`CHAT-gdtktbfh` so the Boot 4 work could land with a green CI, and
`CHAT-zdqubyue` then removed it. The B11 row in Resolved carries the reason.
Measured on 2026-09-20 at `chat-zdqubyue-dropelastic`.

Do not trust this file on its own — run the verifier:

```bash
./shell-scripts/build-health.sh            # test phase, offline
./shell-scripts/build-health.sh --install  # includes package/install
./shell-scripts/build-health.sh --integration # also runs the container-backed tests
./shell-scripts/build-health.sh --ci          # builds the image first, then runs every test
```

It runs the build, diffs the failing modules against the list below, and exits non-zero when the two disagree — reporting anything **NEW** (failing but undocumented), **RESOLVED** (documented but passing), or **SKIPPED** (never built, so unknown). When it complains, update this file; that is the maintenance loop.

## Current state

`mvn clean test -fae` — **BUILD SUCCESS**. No module fails and nothing is
skipped. The reactor holds 36 modules and reports 902 tests, 0 failures, 0
errors and 30 skipped. The count moved from 844 when `CHAT-hazcatpc` added
two consumer group tests to `chat-messaging-kafka` and one deployment test to
`chat-deploy-kafka`. It moved to 849 when `CHAT-cophllrg` added two password
tests to `chat-authorization-server`, and to 856 when `CHAT-ltvfmcvh`
replaced its diagnosis tests with the identity policy tests. It moved to
857 when `CHAT-gtebuipo` added the webflux anonymous test, and to 863
when `CHAT-qucgqaye` added the cassandra composite identity tests, and to
870 when `CHAT-vehpbvzn` added the anonymous authorization matrix, and to
873 when `CHAT-zhjltbky` added the configuration binding guard, and to 902
when the grant order clock landed.

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

### Cassandra integration timeout

`CHAT-sgyaaivp` recorded intermittent `CassandraDriverTimeoutException` failures
after the container accepted connections. The driver request timeout was unset,
so its two-second default could expire during schema setup and the first queries
on a slower CI runner. Both Cassandra test configurations now set
`spring.cassandra.request.timeout=10s`. `TypedKeyValueStoreTests` and
`UserIndexRepositoryTests` check the bound value. The Cassandra modules passed
locally after this change. A `--ci` verifier run at master `c6c21f12` reported
1080 tests, 0 failures, 0 errors and 54 skipped, with no failure-list drift.
The ten-run CI record that `CHAT-sgyaaivp` asks for is not complete.

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
  `--ci` runs `mvn clean verify -fae -Ptest-build,integration`, so it builds the
  image in the same reactor before `chat-shell` runs. **It is not the CI
  command.** It takes the phase and the profiles of the workflow integration
  job, and it resolves online as that job does, but it adds `-fae`, because
  this verifier must see the result of every module. A build that stops at the
  first failure reports the rest as skipped, and the drift diff then reads
  nothing.

  Measured on 2026-09-20: the image took a new tag at 22:10:45, and the
  `chat-shell` surefire reports were written at 22:10:56. So the image build
  precedes the container tests inside one reactor, which is the thing no
  earlier mode could show. `chat-shell` reported 56 tests with 22 skipped, and
  the four `Long*` classes ran. See CHAT-bahmtzut.
- **This repository has two image paths, and CI builds one of them.** The
  first is the `chat-deploy-memory-integration-test` image, which the
  `chat-shell` container tests run against. CI builds it, and so does `--ci`.
  The second is a deployment image, which `spring-boot:build-image` writes for
  a deploy module through `chat-build core <backend> --build`. **No CI job and
  no verifier mode builds a deployment image.**

  So a defect that reaches only the deployment image stays invisible to every
  automated check. **Two did.** The second was a malformed JVM option that
  killed the cassandra deployment container before any application code ran,
  while the image build reported success. `CHAT-vcmlztpd` records it. Both are
  worth stating precisely.

  **Read the second one with its limit.** The repair is a delimiter in the
  parent pom, so the mechanism reaches every deploy module that inherits that
  configuration. **Only the cassandra image was built and run.** That is a
  reading of shared configuration, not a measurement of four other images. A
  start on one backend is not evidence that another backend starts.

  **A deploy module does not ship a main class. It inherits one.**
  `com.demo.chat.ChatApp` lives in `chat-deploy`, and each backend module
  depends on that artifact. `chat-deploy-memory` and `chat-deploy-kafka`
  declare no `mainClass` at all, and both launch. So the defect in
  `chat-deploy-cassandra` was **an override that named the wrong class**, not
  a missing main. Its pom pointed at `CassandraAppConfiguration`, which is a
  `@Configuration` class with one `@Bean`, until 2026-09-21.

  Measured on 2026-09-22, with the pre-repair value restored: the image builds
  and its jar manifest reads
  `Start-Class: com.demo.chat.config.deploy.cassandra.CassandraAppConfiguration`.
  **The build reports success and bakes an entry point that has no main
  method.** `spring-boot:run` reported `Main method not found in class` for
  that same value, so the class is wrong in both paths.

  A container run of that image is **not** the evidence here. It failed before
  the entry point, on a malformed JVM option, which is a separate matter under
  `CHAT-vcmlztpd`. `CHAT-ombbesyh` found the defect by launching the root, and
  `CHAT-byinjjah` holds the check that would catch the next one.

  Measured on 2026-09-22 at master `80434823`: CI run `35679897748` built
  `chat-deploy-long-memory-integration-test:0.0.1` and no other image. A local
  `chat-build core --cassandra --build` then produced
  `docker.io/library/cassandra-core-service-rsocket:0.0.1`, whose jar manifest
  reads `Start-Class: com.demo.chat.ChatApp`. That build needed
  `IMAGE_REPO_PREFIX` in the environment until 2026-09-22, and only
  `shell-scripts/build.sh` defaulted it. `CHAT-gkwqnnxn` moved the default into
  the parent pom, so every entry point carries it now.
- **Boot 4 reads `~/.docker/config.json` before it pulls the builder image.**
  `DockerRegistryConfigAuthentication` is new in the Boot 4 line. A config that
  holds a `credsStore` together with empty `auths` entries makes the build fail
  with `'username' must not be null`, and the message names no registry. Boot
  3.5.x did not read the file this way. This is a property of the developer
  machine and not of this repository. Measured on 2026-09-19: the same build
  succeeds with `DOCKER_CONFIG` pointed at a directory holding `{}`.
  `DOCKER_HOST` has nothing to do with it.

  **A directory holding `{}` alone is not enough when the config names a
  context.** Measured on 2026-09-22: a `config.json` that names
  `desktop-linux` fails with `Docker context 'desktop-linux' does not exist`,
  because the isolated directory holds no `contexts` tree. Copy
  `~/.docker/contexts` into the directory beside a `config.json` that holds no
  `auths` key. **Do not set `DOCKER_HOST` instead.** A run that set it failed
  five modules rather than one, because this machine relies on that variable
  staying unset.

- The first build after R2 needs network access: `org.testcontainers:database-commons:1.21.4` is not in a local repository that predates the bump, so `mvn -o` fails until it is fetched once.
