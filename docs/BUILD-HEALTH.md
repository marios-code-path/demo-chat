# Build Health

Known build-time deficiencies, what causes them, and what they take down with them.

**Verified against master `d81f0859` on 2026-08-23** by all three verifier modes — default, `--install` and `--integration` — each reporting no drift.

Do not trust this file on its own — run the verifier:

```bash
./shell-scripts/build-health.sh            # test phase, offline
./shell-scripts/build-health.sh --install  # includes package/install
./shell-scripts/build-health.sh --integration # also runs the container-backed tests
```

It runs the build, diffs the failing modules against the list below, and exits non-zero when the two disagree — reporting anything **NEW** (failing but undocumented), **RESOLVED** (documented but passing), or **SKIPPED** (never built, so unknown). When it complains, update this file; that is the maintenance loop.

## Current state

`mvn clean test -fae` — **BUILD SUCCESS**. No module fails, nothing is skipped.
`mvn clean install` — **BUILD SUCCESS**. Image building moved behind `-Ptest-build`, so no build needs a Docker daemon.
`mvn clean test -fae -Pintegration` — **BUILD SUCCESS**, against Docker Engine 29.7.2.

Note what the default run no longer covers. Since #32 the container-backed tests are tagged `integration` and excluded unless `-Pintegration` is passed, so roughly 160 tests are not exercised by a plain build. `chat-shell` passing by default means its tests did not run — not that B2 is fixed. Since #54 a local plain build still has that gap, but CI no longer does: the integration job runs `mvn -B clean verify -Ptest-build,integration` on every pull request and every master push, so the container half is checked per commit. The job is informational until 10 runs are recorded. See CHAT-uortzsbx for the baseline.

The `--integration` run is what settles that question, and it now passes with no module failing and none skipped. So `chat-shell` passes on its own merits, not by exclusion, and B2 holds up with containers running. Both remaining lists in the verifier — `KNOWN_FAILING_INSTALL` and `KNOWN_FAILING_INTEGRATION` — are empty and measured, not assumed.

Read the `chat-shell` skip count with care. A `-Pintegration` run of that module reports 36 tests with 17 skipped, which looks like absent coverage and is not. Each `@Disabled` sits on a generic base class — `ShellUserCommandsTests`, `ShellLoginCommandsTests`, `ShellTopicCommandsTests` — and surefire discovers those as test classes in their own right and reports them skipped. JUnit does not inherit `@Disabled`, so the concrete `Long*` subclass runs. The 19 that do run include every container-backed one, against the singleton container `ShellIntegrationTestBase` starts from the `chat-deploy-memory-integration-test` image.

| ID | Deficiency | Blocks | Status |
|----|-----------|--------|--------|
| B6 | Stale `target/` across branch switches produces phantom results | correctness of any non-clean run | Workaround only |

---

### B6 — a stale `target/` directory after a branch change gives false results

**Symptom.** `mvn test` without `clean` runs the compiled test classes in `target/test-classes`. A different branch put those classes there. They do not agree with the production code in the tree.

This occurred during the selector work. Three tests failed. No source file in the tree contained those tests.

**Mitigation.**

1. Use `mvn clean test` after you change branches.
2. The verifier script always cleans. It is not affected.

---

## Resolved

Kept so the list can be trusted — an entry disappearing without explanation is indistinguishable from an entry being forgotten.

| ID | Deficiency | Fixed by |
|----|-----------|----------|
| B1 | Two defects stacked. `spring-boot-maven-plugin` 3.3.x hardcodes Docker API v1.24 and Docker Engine 29 requires v1.40, so the image could not be built; and the image it *would* have built did not boot, because the module's pom baked bare `app.service.core.*` selectors that activate nothing. Plugin pinned to 3.5.12 for that module, selectors given values, and image building moved behind `-Ptest-build` so no ordinary build needs Docker. | #39 |
| Cycle | `chat-deploy`'s `<backend>-backend` profiles pulled `chat-deploy-<backend>`, which depends on `chat-deploy` — a reactor cycle for memory, cassandra and kafka. Two mechanisms composed the same thing in opposite directions. The profiles are removed and `chat-build` targets the `chat-deploy-<backend>` module directly. | #42 |
| B2 | `ShellIntegrationTestBase` called `setWaitStrategy(...)` *after* `start()` had returned, so it never applied and `start()` fell back to a port check — satisfied when the port binds, not when the app can serve. The root-key fetch then raced startup and failed with `Connection reset`, intermittently and by test order. The pattern it would have used, `"*Netty RSocket started*"`, was shell globbing rather than a regex, so each defect hid the other. Now `waitingFor(...)` before `start()`, with a valid pattern and a realistic timeout. | #40 |
| B3 | `joinRestRoom` and `leaveRestRoom` declared `id: T` with no annotation, so Spring treated it as a model attribute and failed to instantiate the erased type variable — `IllegalStateException: Insufficient type information to create instance of ?`. Adding `@PathVariable` binds from the URI template instead. Not an authentication problem, which was the first hypothesis. | #34 |
| B4 | `AuthorizationServerDeployTests` failed on a missing `server_keycert.jwk`. The fixture is generated by `gen-dckeys.sh` and copied into test resources, but nothing in the build runs that script, so the test passed only where someone had run it by hand. The key is now generated per run into a temp file, with `app.oauth2.jwk.path` pointed at it — EC P-256, because the token customizer signs with ES256. | #36 |
| B5 | CI ran `mvn clean test-compile` and never executed a test, so a green check meant only that the code compiled. It now runs `mvn -B clean test`, which was safe once B4 made the default reactor green. A second job adds the container half: `mvn -B clean verify -Ptest-build,integration`, with the image built in the same reactor before chat-shell runs. | #38, #54 |
| B7 | `chat-deploy-memory` declared `chat-service-controller` at `test` scope, since 2023-10-12. `spring-boot:run` uses the runtime classpath, so the controllers and the `chat-security` password encoder config were absent. Direct launch failed on a missing `PasswordEncoder` bean. Tests hid this because surefire uses the test classpath. The image module, cassandra, and kafka all used compile scope; memory was the outlier. | #57 |
| R1 | `KotlinModule` named-constructor form is a compile error under the jackson version Spring Boot 3.3.13 manages. `chat-client-rsocket` failing test-compile stopped the reactor and took `chat-deploy-redis`, `chat-shell` and `chat-authorization-server` down as SKIPPED. | #13, #22 |
| R2 | Modules declared `org.testcontainers:cassandra` at 1.21.4 but Spring Boot's BOM pinned the core `testcontainers` artifact at 1.19.8, whose `docker-java` 3.3.6 cannot negotiate with Docker Engine 29.x — reported as the misleading "Could not find a valid Docker environment". | #23 |

R2 moved `chat-persistence-cassandra` from 41 tests with 15 errors to 71 passing, and `chat-index-cassandra` from 8 tests with 4 errors to 26 passing.

## One-time notes

- The first build after R2 needs network access: `org.testcontainers:database-commons:1.21.4` is not in a local repository that predates the bump, so `mvn -o` fails until it is fetched once.
