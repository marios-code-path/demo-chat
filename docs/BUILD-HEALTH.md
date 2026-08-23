# Build Health

Known build-time deficiencies, what causes them, and what they take down with them.

**Verified against master `c4dc9a57` on 2026-08-22** by a full `mvn clean test -fae` plus targeted per-module runs.

Do not trust this file on its own — run the verifier:

```bash
./shell-scripts/build-health.sh            # test phase, offline
./shell-scripts/build-health.sh --install  # includes package/install, covers B1
```

It runs the build, diffs the failing modules against the list below, and exits non-zero when the two disagree — reporting anything **NEW** (failing but undocumented), **RESOLVED** (documented but passing), or **SKIPPED** (never built, so unknown). When it complains, update this file; that is the maintenance loop.

## Current state

`mvn clean test -fae` — one module fails, nothing is skipped.
`mvn clean install` — **BUILD SUCCESS**. Image building moved behind `-Ptest-build`, so no build needs a Docker daemon.

Note what the default run no longer covers. Since #32 the container-backed tests are tagged `integration` and excluded unless `-Pintegration` is passed, so roughly 160 tests are not exercised by a plain build. `chat-shell` passing by default means its tests did not run — not that B2 is fixed.

| ID | Deficiency | Blocks | Status |
|----|-----------|--------|--------|
| B2 | `chat-shell` shell commands fail on uninitialized root keys | 3 test classes, only under `-Pintegration` | Open — cause changed, see below |
| B4 | `chat-authorization-server` missing `server_keycert.jwk` fixture | 1 test | Open, needs a decision |
| B5 | CI compiles but never runs a test | all of B2–B4 invisible to CI | Open, may be deliberate |
| B6 | Stale `target/` across branch switches produces phantom results | correctness of any non-clean run | Workaround only |

---

### B2 — `chat-shell` shell commands fail on uninitialized root keys

**This entry changed shape once B1 landed.** It used to be "the image cannot be pulled". With a working image the container starts, `ShellRequesterTests` passes 2/2, and what remains is a different bug:

```
java.lang.NullPointerException
  at com.demo.chat.domain.knownkey.RootKeys.getRootKey(RootKeys.kt:20)
  at com.demo.chat.shell.commands.CommandsUtil.identity(CommandsUtil.kt:19)
  at com.demo.chat.shell.commands.LoginCommands.whoami(LoginCommands.kt:45)
```

**Blast radius.** `LongShellTopicCommandsTests` (3 errors), `LongUserCommandsTests` (1), `LongLoginCommandsTests` (1). Only under `-Pintegration`.

**Reproduce.** `mvn -Ptest-build install` then `mvn -pl chat-shell -Pintegration test`

**Also here, and still unfixed:** `ShellIntegrationTestBase` builds its container with

```kotlin
.apply { start(); setWaitStrategy(...) }
```

The wait strategy is set *after* `start()` has already returned, so it never applies. The pattern it would have used, `withRegEx("*Netty RSocket started*")`, is not a valid regular expression either — a leading `*` has no operand. Both are currently harmless because `start()` blocks on the default strategy, which is why this was invisible.

---

### B5 — CI compiles but never runs a test

`.github/workflows/maven.yml` runs:

```yaml
run: mvn clean test-compile
```

**Consequence.** A green check mark means every module compiles, including test sources. It does not mean any test passed. B3 and B4 are invisible to CI, and always have been.

This may well be deliberate — the container-backed suites need a Docker daemon, and B1/B2 would fail a CI run today regardless. Recorded so that nobody reads a green tick as more than it is.

---

### B6 — stale `target/` across branch switches produces phantom results

**Symptom.** `mvn test` without `clean` runs compiled test classes left in `target/test-classes` by a previously checked-out branch, against production code that no longer matches. Observed during the selector work: three failures that did not correspond to any source file present in the tree.

**Mitigation.** Use `mvn clean test` when switching branches. The verifier script always cleans.

---

## Resolved

Kept so the list can be trusted — an entry disappearing without explanation is indistinguishable from an entry being forgotten.

| ID | Deficiency | Fixed by |
|----|-----------|----------|
| B1 | Two defects stacked. `spring-boot-maven-plugin` 3.3.x hardcodes Docker API v1.24 and Docker Engine 29 requires v1.40, so the image could not be built; and the image it *would* have built did not boot, because the module's pom baked bare `app.service.core.*` selectors that activate nothing. Plugin pinned to 3.5.12 for that module, selectors given values, and image building moved behind `-Ptest-build` so no ordinary build needs Docker. | #39 |
| B3 | `joinRestRoom` and `leaveRestRoom` declared `id: T` with no annotation, so Spring treated it as a model attribute and failed to instantiate the erased type variable — `IllegalStateException: Insufficient type information to create instance of ?`. Adding `@PathVariable` binds from the URI template instead. Not an authentication problem, which was the first hypothesis. | #34 |
| R1 | `KotlinModule` named-constructor form is a compile error under the jackson version Spring Boot 3.3.13 manages. `chat-client-rsocket` failing test-compile stopped the reactor and took `chat-deploy-redis`, `chat-shell` and `chat-authorization-server` down as SKIPPED. | #13, #22 |
| R2 | Modules declared `org.testcontainers:cassandra` at 1.21.4 but Spring Boot's BOM pinned the core `testcontainers` artifact at 1.19.8, whose `docker-java` 3.3.6 cannot negotiate with Docker Engine 29.x — reported as the misleading "Could not find a valid Docker environment". | #23 |

R2 moved `chat-persistence-cassandra` from 41 tests with 15 errors to 71 passing, and `chat-index-cassandra` from 8 tests with 4 errors to 26 passing.

## One-time notes

- The first build after R2 needs network access: `org.testcontainers:database-commons:1.21.4` is not in a local repository that predates the bump, so `mvn -o` fails until it is fetched once.
