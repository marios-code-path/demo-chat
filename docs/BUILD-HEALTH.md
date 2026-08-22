# Build Health

Known build-time deficiencies, what causes them, and what they take down with them.

**Verified against master `66d6a85d` on 2026-08-21** by a full `mvn clean test -fae` plus targeted per-module runs.

Do not trust this file on its own — run the verifier:

```bash
./shell-scripts/build-health.sh            # test phase, offline
./shell-scripts/build-health.sh --install  # includes package/install, covers B1
```

It runs the build, diffs the failing modules against the list below, and exits non-zero when the two disagree — reporting anything **NEW** (failing but undocumented), **RESOLVED** (documented but passing), or **SKIPPED** (never built, so unknown). When it complains, update this file; that is the maintenance loop.

## Current state

`mvn clean test -fae` — three modules fail, nothing is skipped.
`mvn clean install` — additionally fails at `chat-deploy-memory-integration-tests`, which stops the build before packaging.

| ID | Deficiency | Blocks | Status |
|----|-----------|--------|--------|
| B1 | `spring-boot:build-image` cannot talk to Docker 29.x | `mvn install` for the whole reactor | Open |
| B2 | `chat-shell` integration tests need an image B1 never builds | 4 test classes | Open, blocked on B1 |
| B3 | `chat-webflux` `LongTopicRestTests` returns 500 where 200 is expected | 2 tests | Open, uninvestigated |
| B4 | `chat-authorization-server` missing `server_keycert.jwk` fixture | 1 test | Open, needs a decision |
| B5 | CI compiles but never runs a test | all of B2–B4 invisible to CI | Open, may be deliberate |
| B6 | Stale `target/` across branch switches produces phantom results | correctness of any non-clean run | Workaround only |

---

### B1 — `spring-boot:build-image` cannot talk to Docker 29.x

**Symptom**

```
Docker API call to '.../v1.24/images/create?fromImage=docker.io%2Fpaketobuildpacks%2Fbuilder-jammy-base%3Alatest'
failed with status code 400 "Bad Request" and message
"client version 1.24 is too old. Minimum supported API version is 1.40"
```

**Cause.** `chat-deploy-memory-integration-tests` binds `spring-boot:build-image` to the default lifecycle, so it runs on every `install`. The plugin's own Docker client negotiates API 1.24; Docker Engine 29.x requires 1.40 or newer. This is a different Docker client from the one testcontainers uses — the testcontainers half of this problem was fixed separately (see R2), and that fix does not help here.

**Blast radius.** Stops `mvn install` for the whole reactor, and starves B2 of its image.

**Reproduce.** `mvn -pl chat-deploy-memory-integration-tests install`

**Worth considering.** Whether that goal should be bound to the default lifecycle at all, rather than a profile invoked when an image is actually wanted. A plugin version bump may also resolve the API negotiation.

---

### B2 — `chat-shell` integration tests need an image B1 never builds

**Symptom**

```
ContainerFetchException: Can't get Docker image:
  RemoteDockerImage(imageName=chat-deploy-long-memory-integration-test:0.0.1, ...)
NotFoundException: Status 404: pull access denied for chat-deploy-long-memory-integration-test
```

**Cause.** Downstream of B1. These tests expect a locally built image; testcontainers cannot find it locally, falls back to pulling from Docker Hub, and gets a 404 because no such public image exists.

**Blast radius.** `ShellRequesterTests`, `LongShellTopicCommandsTests`, `LongUserCommandsTests`, `LongLoginCommandsTests` error out. A further 17 tests in the module are `@Disabled`.

**Note.** These failures were invisible until recently: `chat-shell` was being SKIPPED because `chat-client-rsocket` failed to compile (R1), so nobody saw them.

---

### B3 — `chat-webflux` `LongTopicRestTests` returns 500 where 200 is expected

**Symptom**

```
LongTopicRestTests > TopicRestTestBase.join a room:199   expected <200 OK> but was <500 INTERNAL_SERVER_ERROR>
LongTopicRestTests > TopicRestTestBase.leave topic:224   expected <200 OK> but was <500 INTERNAL_SERVER_ERROR>
```

**Cause.** Unknown — not investigated. Long-standing; predates the selector and messaging work of August 2026. The module depends only on `chat-core` and `chat-security`, so it is independent of the persistence, index and messaging backends.

**Blast radius.** 2 tests of 77 in the module.

**Reproduce.** `mvn -pl chat-webflux test`

---

### B4 — `chat-authorization-server` missing `server_keycert.jwk` fixture

**Symptom**

```
FileNotFoundException: class path resource [server_keycert.jwk] cannot be opened because it does not exist
  ... creating bean 'jwkSetSource' in AuthorizationServerConfig
```

**Cause.** The test context builds a `JWKSource` from a classpath resource that is not in the repository.

**Blast radius.** 1 error of 3 tests in the module.

**Needs a decision.** Either commit a test-only key, or generate one in test setup. Committing a real signing key is the wrong answer; a throwaway generated per run is likely right, but that is a call for whoever owns the auth server.

**Note.** Like B2, this was hidden behind R1 — the module's tests had not compiled for some time.

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
| R1 | `KotlinModule` named-constructor form is a compile error under the jackson version Spring Boot 3.3.13 manages. `chat-client-rsocket` failing test-compile stopped the reactor and took `chat-deploy-redis`, `chat-shell` and `chat-authorization-server` down as SKIPPED. | #13, #22 |
| R2 | Modules declared `org.testcontainers:cassandra` at 1.21.4 but Spring Boot's BOM pinned the core `testcontainers` artifact at 1.19.8, whose `docker-java` 3.3.6 cannot negotiate with Docker Engine 29.x — reported as the misleading "Could not find a valid Docker environment". | #23 |

R2 moved `chat-persistence-cassandra` from 41 tests with 15 errors to 71 passing, and `chat-index-cassandra` from 8 tests with 4 errors to 26 passing.

## One-time notes

- The first build after R2 needs network access: `org.testcontainers:database-commons:1.21.4` is not in a local repository that predates the bump, so `mvn -o` fails until it is fetched once.
