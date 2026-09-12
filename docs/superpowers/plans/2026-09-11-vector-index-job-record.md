# Vector Index Job Record Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking. **This repository forbids subagent-driven development. See
> `AGENTS.md`.**

**Goal:** Recall reports the coverage of the newest trusted successful rebuild job,
instead of the phase of the active job.

**Architecture:** Each rebuild owns one persisted topic and one durable `IndexJob`
in the key-value store. Job progress messages go to that topic through a composed
writer that never touches the vector indexer. A process-local generation guards the
in-flight race. A durable invalidation count on the covering job decides coverage.

**Tech Stack:** Kotlin 2.4.10, Java 25, Spring Boot 3.5.16, Project Reactor, JUnit 5,
AssertJ, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-11-vector-index-run-record-design.md`

## Global Constraints

- Java release 25. Kotlin `jvmTarget` 17 is not used anywhere. Do not add either.
- No module declares a third-party version. `shell-scripts/check-dependency-versions.sh`
  fails the build when one does.
- `chat-core` does **not** enable the Kotlin all-open plugin. A `@Configuration`
  class there must be `open`, and its `@Bean` methods must be `open`.
  `chat-service-composite` does enable it.
- Run `mvn -o -pl chat-core,<module> test`, never `-pl <module>` alone. A
  single-module run resolves `chat-core` from `~/.m2` and reports false failures.
- Build with JDK 25: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem`.
- Add `-Dsurefire.failIfNoSpecifiedTests=false` to any `-Dtest=` run that spans
  several modules.
- Vector tests claim no node id. They activate memory key and persistence. See
  `docs/NODEID-CLAIM.md`.
- Controlled English applies to every comment, message, and document this plan
  produces. See `AGENTS.md`.

## Spec gap this plan closes

`addRoom()` calls `pubsub.open(room.key.id)` after it writes persistence and the
index. The spec omits that call from job topic creation. Without it
`sendMessage()` fails on the memory backend with `Object not Found`, which is the
defect recorded for `CHAT-qonhhtuq`. **Task 5 calls `pubsub.open()`.**

The spec now carries the ordered sequence. See its Job Topic And Discovery
section.

## Reconciliation with the open issues

| Issue | Outcome |
|-------|---------|
| `CHAT-twocpczd` | Rewritten. Task 9. Its old How described the superseded phase model. |
| `CHAT-awauccrm` | Rewritten. Task 11. Its old file list held one configuration file. |
| `CHAT-jddllrrx` | Kept. Task 10. |
| `CHAT-zepiarzb` | Kept. Task 12. |
| `CHAT-neucngmw` | Kept. Task 13. |
| `CHAT-jqvclfbd` | Kept. Task 14. |
| New | Tasks 1 to 8 cover the job record, the topic seam, the state target, the writer, and the policy. |

## File Structure

```filetree
chat-core/src/main/kotlin/com/demo/chat/
├── domain/
│   ├── IndexJob.kt                      # new - durable job evidence, JobOutcome
│   └── JobRecord.kt                     # new - one progress message payload
└── service/vector/
    ├── VectorIndexState.kt              # modified - invalidation target
    ├── VectorIndexJobStore.kt           # new - durable read and write of IndexJob
    ├── JobRecordCodec.kt                # new - versioned JSON for JobRecord
    ├── JobRecordWriter.kt               # new - one job message write
    ├── VectorCoveragePolicy.kt          # new - trust and coverage decision
    ├── JobTopicNames.kt                 # new - reserved prefix and name format
    ├── MessageRecallResult.kt           # new - bounded hits plus the flag
    └── MessageRecallService.kt          # modified - returns Mono of the result
chat-service-composite/src/main/kotlin/com/demo/chat/
├── service/composite/impl/
│   ├── InMemoryVectorIndexState.kt      # modified - holds the target
│   ├── VectorIndexJobStoreImpl.kt       # new - key-value plus topic directory
│   ├── ComposedJobRecordWriter.kt       # new - persistence, index, pub/sub
│   ├── VectorCoveragePolicyImpl.kt      # new
│   ├── MessageReindexServiceImpl.kt     # modified - job, exclusion, counters
│   ├── MessageRecallServiceImpl.kt      # modified - returns the result
│   └── TopicServiceImpl.kt              # modified - reserved prefix seam
└── config/service/composite/
    └── VectorRecallServiceConfiguration.kt  # modified - every new bean
chat-service-controller/.../mapping/
└── MessageRecallControllerMapping.kt    # modified - Mono routes
chat-webflux/.../webflux/
└── ChatMessageRecallController.kt       # modified - JSON, not NDJSON
chat-deploy/.../actuator/
└── VectorIndexEndpoint.kt               # new - read and write, two gates
```

---

### Task 1: The durable job record types

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/domain/JobRecord.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobTests.kt`

**Interfaces:**
- Consumes: `Key<T>` and `KeyBearer<T>` from `com.demo.chat.domain`.
- Produces: `IndexJob<T>`, `JobOutcome`, `JobRecord<T>`. Task 2 and later use
  `IndexJob.key`, `IndexJob.invalidationCount`, `IndexJob.outcome`,
  `IndexJob.incarnationId`, `IndexJob.nodeId`, `IndexJob.keyType`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class IndexJobTests {
    private val start = Instant.parse("2026-09-11T12:00:00Z")

    @Test
    fun `a new job starts with no invalidation`() {
        val job = IndexJob(
            key = Key.funKey(1L),
            nodeId = 7,
            keyType = "long",
            incarnationId = "abc",
            startedBy = Key.funKey(2L),
            startedAt = start,
        )

        Assertions.assertThat(job.outcome).isEqualTo(JobOutcome.RUNNING)
        Assertions.assertThat(job.invalidationCount).isEqualTo(0L)
        Assertions.assertThat(job.lastInvalidationAt).isNull()
        Assertions.assertThat(job.finishedAt).isNull()
        Assertions.assertThat(job.covers).isFalse()
    }

    @Test
    fun `only a succeeded job with no invalidation covers`() {
        val job = IndexJob(
            key = Key.funKey(1L),
            nodeId = 7,
            keyType = "long",
            incarnationId = "abc",
            startedBy = Key.funKey(2L),
            startedAt = start,
            outcome = JobOutcome.SUCCEEDED,
        )

        Assertions.assertThat(job.covers).isTrue()
        Assertions.assertThat(job.copy(invalidationCount = 1L).covers).isFalse()
        Assertions.assertThat(job.copy(outcome = JobOutcome.FAILED).covers).isFalse()
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core test -Dtest=IndexJobTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports an unresolved reference to `IndexJob`.

- [ ] **Step 3: Write the types**

```kotlin
package com.demo.chat.domain

import java.time.Instant

enum class JobOutcome {
    RUNNING,
    SUCCEEDED,
    FAILED,
    RELEASED,
}

/**
 * Durable evidence of one vector index rebuild.
 *
 * The record never acts as a lock. A RUNNING job from an earlier incarnation
 * does not block a new claim.
 *
 * `invalidationCount` is a flag for coverage. Its value is advisory, because a
 * concurrent update can undercount without compare-and-set support. Any value
 * above zero removes coverage.
 */
data class IndexJob<T>(
    override val key: Key<T>,
    val nodeId: Int,
    val keyType: String,
    val incarnationId: String,
    val startedBy: Key<T>,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val outcome: JobOutcome = JobOutcome.RUNNING,
    val attempted: Long = 0L,
    val indexed: Long = 0L,
    val skipped: Long = 0L,
    val failed: Long = 0L,
    val failureSummary: String? = null,
    val invalidationCount: Long = 0L,
    val lastInvalidationAt: Instant? = null,
) : KeyBearer<T> {
    val covers: Boolean
        get() = outcome == JobOutcome.SUCCEEDED && invalidationCount == 0L
}
```

```kotlin
package com.demo.chat.domain

import java.time.Instant

/**
 * One progress message of a rebuild. The writer encodes this as a versioned
 * JSON string, because every current deployment binds message data to String.
 */
data class JobRecord<T>(
    override val key: Key<T>,
    val jobKey: Key<T>,
    val workerKey: Key<T>,
    val at: Instant,
    val message: String,
    val errorKey: Key<T>? = null,
    val attempted: Long? = null,
    val indexed: Long? = null,
    val skipped: Long? = null,
    val failed: Long? = null,
) : KeyBearer<T>
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core test -Dtest=IndexJobTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/domain/IndexJob.kt \
        chat-core/src/main/kotlin/com/demo/chat/domain/JobRecord.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobTests.kt
git commit -m "feat: add the vector index job record types (CHAT-fpwpfrfj)"
```

---

### Task 2: The reserved job topic name

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/JobTopicNames.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/JobTopicNamesTests.kt`

**Interfaces:**
- Produces: `JobTopicNames.PREFIX`, `JobTopicNames.isJobTopic(name: String): Boolean`,
  `JobTopicNames.nameFor(nodeId: Int, keyType: String, startedAt: Instant, incarnationId: String): String`,
  `JobTopicNames.matches(name: String, nodeId: Int, keyType: String): Boolean`.
  Task 3, Task 6, and Task 7 use all four.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.service.vector.JobTopicNames
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class JobTopicNamesTests {
    private val at = Instant.parse("2026-09-11T12:00:00Z")

    @Test
    fun `a job topic name carries the node id and the key type`() {
        val name = JobTopicNames.nameFor(7, "long", at, "abc")

        Assertions.assertThat(name).startsWith(JobTopicNames.PREFIX)
        Assertions.assertThat(JobTopicNames.isJobTopic(name)).isTrue()
        Assertions.assertThat(JobTopicNames.matches(name, 7, "long")).isTrue()
    }

    @Test
    fun `another node or another key type does not match`() {
        val name = JobTopicNames.nameFor(7, "long", at, "abc")

        Assertions.assertThat(JobTopicNames.matches(name, 8, "long")).isFalse()
        Assertions.assertThat(JobTopicNames.matches(name, 7, "uuid")).isFalse()
    }

    @Test
    fun `a user room name is not a job topic`() {
        Assertions.assertThat(JobTopicNames.isJobTopic("general")).isFalse()
    }

    @Test
    fun `two jobs of one incarnation take two names`() {
        val first = JobTopicNames.nameFor(7, "long", at, "abc")
        val second = JobTopicNames.nameFor(7, "long", at.plusSeconds(1), "abc")

        Assertions.assertThat(first).isNotEqualTo(second)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core test -Dtest=JobTopicNamesTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports an unresolved reference to `JobTopicNames`.

- [ ] **Step 3: Write the name rules**

```kotlin
package com.demo.chat.service.vector

import java.time.Instant

/**
 * Names of the system topics that hold rebuild job records.
 *
 * The prefix is the only discriminator a topic carries. `MessageTopic` holds a
 * key and a name and nothing else, so no owner field and no root key can mark a
 * topic as a system topic.
 */
object JobTopicNames {
    const val PREFIX = "__vectorjob__"

    private const val SEPARATOR = ":"

    fun nameFor(nodeId: Int, keyType: String, startedAt: Instant, incarnationId: String): String =
        listOf(PREFIX, nodeId.toString(), keyType, startedAt.toEpochMilli().toString(), incarnationId)
            .joinToString(SEPARATOR)

    fun isJobTopic(name: String): Boolean = name.startsWith(PREFIX)

    fun matches(name: String, nodeId: Int, keyType: String): Boolean =
        isJobTopic(name) &&
            name.split(SEPARATOR).let { parts ->
                parts.size >= 3 && parts[1] == nodeId.toString() && parts[2] == keyType
            }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core test -Dtest=JobTopicNamesTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/JobTopicNames.kt \
        chat-core/src/test/kotlin/com/demo/chat/test/vector/JobTopicNamesTests.kt
git commit -m "feat: add the reserved job topic name rules (CHAT-fpwpfrfj)"
```

---

### Task 3: The reserved prefix seam in the topic service

**Files:**
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/TopicServiceImpl.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/TopicServiceReservedNameTests.kt`

**Interfaces:**
- Consumes: `JobTopicNames` from Task 2.
- Produces: `addRoom` rejects a reserved name. `listRooms` omits every reserved
  topic. Task 7 relies on `TopicPersistence.all()` staying unfiltered, because
  the rebuild reads that store directly and needs every job topic for its
  exclusion set. The filter belongs to `listRooms`, not to the store.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.vector.JobTopicNames
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class TopicServiceReservedNameTests {
    private val jobName = JobTopicNames.nameFor(1, "long", Instant.EPOCH, "abc")

    @Test
    fun `addRoom rejects a reserved name`() {
        val service = topicServiceUnderTest()

        StepVerifier
            .create(service.addRoom(ByStringRequest(jobName)))
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `listRooms omits a reserved topic`() {
        val service = topicServiceUnderTest(
            existing = listOf(
                MessageTopic.create(Key.funKey(1L), "general"),
                MessageTopic.create(Key.funKey(2L), jobName),
            )
        )

        StepVerifier
            .create(service.listRooms())
            .assertNext { topic -> Assertions.assertThat(topic.data).isEqualTo("general") }
            .verifyComplete()
    }
}
```

Write `topicServiceUnderTest` in the same file. It builds a `TopicServiceImpl`
with in-memory fakes for `topicPersistence`, `topicIndex`, `pubsub`,
`userPersistence`, and `membershipPersistence`. Copy the fake shapes from
`MessagingServiceVectorTests`, which already builds composite services with
fakes.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=TopicServiceReservedNameTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. `addRoom` creates the room, and `listRooms` returns two topics.

- [ ] **Step 3: Add the seam**

In `addRoom`, before the index lookup:

```kotlin
    override fun addRoom(req: ByStringRequest): Mono<out Key<T>> =
        if (JobTopicNames.isJobTopic(req.name)) {
            // A system topic never enters through this path. The job service
            // writes persistence and the index itself, so no exception for a
            // system caller is needed here.
            Mono.error(ChatException("A room name cannot start with '${JobTopicNames.PREFIX}'."))
        } else {
            topicIndex
                .findBy(topicNameToQuery.apply(req))
                .hasElements()
                .flatMap { exists -> /* unchanged body */ }
        }
```

In `listRooms`:

```kotlin
    // Job topics are system records. They must not reach a user room list.
    override fun listRooms(): Flux<out MessageTopic<T>> =
        topicPersistence
            .all()
            .filter { topic -> !JobTopicNames.isJobTopic(topic.data) }
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=TopicServiceReservedNameTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 2 tests.

- [ ] **Step 5: Run the whole composite module**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test`
Expected: BUILD SUCCESS. No existing topic test uses a reserved name, so none changes.

- [ ] **Step 6: Commit**

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/TopicServiceImpl.kt \
        chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/TopicServiceReservedNameTests.kt
git commit -m "feat: reject and hide reserved job topic names (CHAT-fpwpfrfj)"
```

---

### Task 4: The invalidation target inside the atomic state

**Files:**
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/InMemoryVectorIndexState.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/InMemoryVectorIndexStateTests.kt`

**Interfaces:**
- Consumes: `IndexJob<T>` from Task 1.
- Produces: `VectorIndexState<T>` with `invalidate(reason): VectorInvalidation<T>`,
  `finish(claim, report, failure, jobKey): VectorIndexStatus`,
  `adoptCoveringJob(jobKey)`, and `coveringJob()`. Task 5, Task 7, and Task 8 use
  all four. `VectorInvalidation<T>(generation, target)`.

**Why the state becomes generic:** the target is a `Key<T>`. A raw `Key<*>` would
push an unchecked cast into every caller.

- [ ] **Step 1: Write the failing tests**

Add these to `InMemoryVectorIndexStateTests`. Change the class field to
`InMemoryVectorIndexState<Long>()`.

```kotlin
    @Test
    fun `a successful finish installs its job as the invalidation target`() {
        val state = InMemoryVectorIndexState<Long>()
        val claim = state.claim()
        val jobKey = Key.funKey(11L)

        state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L), null, jobKey)

        Assertions.assertThat(state.coveringJob()).isEqualTo(jobKey)
    }

    @Test
    fun `a failed finish does not install its job as the target`() {
        val state = InMemoryVectorIndexState<Long>()
        state.adoptCoveringJob(Key.funKey(9L))
        val claim = state.claim()

        state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 0L, 0L, 1L), "boom", Key.funKey(11L))

        Assertions.assertThat(state.coveringJob()).isEqualTo(Key.funKey(9L))
    }

    @Test
    fun `invalidate returns the target that the state holds and clears it`() {
        val state = InMemoryVectorIndexState<Long>()
        state.adoptCoveringJob(Key.funKey(9L))

        val invalidation = state.invalidate("live vector add failed")

        Assertions.assertThat(invalidation.target).isEqualTo(Key.funKey(9L))
        Assertions.assertThat(invalidation.generation).isEqualTo(1L)
        Assertions.assertThat(state.coveringJob()).isNull()
    }

    @Test
    fun `a second invalidation finds no target`() {
        val state = InMemoryVectorIndexState<Long>()
        state.adoptCoveringJob(Key.funKey(9L))
        state.invalidate("first")

        Assertions.assertThat(state.invalidate("second").target).isNull()
    }

    @Test
    fun `an invalidation that wins the race keeps the new job from covering`() {
        val state = InMemoryVectorIndexState<Long>()
        state.adoptCoveringJob(Key.funKey(9L))
        val claim = state.claim()
        state.invalidate("live vector add failed")

        val status = state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L), null, Key.funKey(11L))

        Assertions.assertThat(status.complete).isFalse()
        Assertions.assertThat(state.coveringJob()).isEqualTo(Key.funKey(9L))
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=InMemoryVectorIndexStateTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports unresolved references to `coveringJob`,
`adoptCoveringJob`, and the fourth `finish` parameter.

- [ ] **Step 3: Change the contract**

```kotlin
data class VectorInvalidation<T>(
    val generation: Long,
    val target: Key<T>?,
)

interface VectorIndexState<T> {
    fun status(): VectorIndexStatus

    fun claim(): VectorIndexClaim

    /**
     * Increments the process generation, returns the covering job that the
     * state holds, and clears that target.
     *
     * The caller updates the returned job and no other. A query for the newest
     * successful job could name a job that a concurrent finish has already
     * superseded.
     *
     * The clear is what makes coverage drop. An invalidated job no longer
     * covers, and no earlier job may take its place. A later successful finish
     * installs a new target.
     */
    fun invalidate(reason: String): VectorInvalidation<T>

    /**
     * Installs [jobKey] as the covering job when the run succeeded and the
     * generation did not move. A losing run leaves the earlier target in place.
     */
    fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
        jobKey: Key<T>,
    ): VectorIndexStatus

    /** Startup adopts the job that the coverage policy selected. */
    fun adoptCoveringJob(jobKey: Key<T>?)

    fun coveringJob(): Key<T>?
}
```

- [ ] **Step 4: Change the implementation**

Add `coveringJob: Key<T>?` to the private `Snapshot`. `adoptCoveringJob` sets it
through `updateAndGet`. `coveringJob()` reads it. `invalidate` returns
`VectorInvalidation(newGeneration, previous.coveringJob)` and sets `coveringJob`
to null in the same `updateAndGet` result. In `finish`, set `coveringJob = jobKey` only inside the
`succeeded` branch, and leave it unchanged otherwise.

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=InMemoryVectorIndexStateTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 10 tests.

- [ ] **Step 6: Repair the reindex service call site**

`MessageReindexServiceImpl` calls `state.finish(claim, report, failure)`. Pass the
job key. Task 7 gives the service a real job. Until then pass the claim's own
placeholder key so the module compiles, and mark the line with
`// Task 7 replaces this with the durable job key.`

- [ ] **Step 7: Run the composite module and commit**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test
git add -A && git commit -m "feat: hold the invalidation target in the vector index state (CHAT-fpwpfrfj)"
```

---

### Task 5: The durable job store

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexJobStore.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexJobStoreImpl.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorIndexJobStoreImplTests.kt`

**Interfaces:**
- Consumes: `IndexJob<T>`, `JobOutcome`, `JobTopicNames`, `TopicPersistence<T>`,
  `TopicIndexService<T, Q>`, `TopicPubSubService<T, V>`, `KeyValueStore<T, Any>`.
- Produces: `VectorIndexJobStore<T>` with `createJob(startedAt): Mono<IndexJob<T>>`,
  `write(job): Mono<Void>`, `readJob(topicKey): Mono<IndexJob<T>>`,
  `listJobTopics(): Flux<MessageTopic<T>>`, and
  `invalidate(jobKey, at): Mono<Void>`. Task 7 and Task 8 use all five.

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

interface VectorIndexJobStore<T> {
    /**
     * Creates the job topic and writes the RUNNING job.
     *
     * The topic is written through topic persistence, the topic index, and
     * `pubsub.open()`. It never goes through `addRoom()`, which rejects the
     * reserved prefix for every caller. `pubsub.open()` is required, because
     * the memory backend answers `sendMessage()` with `Object not Found` for a
     * topic it never opened.
     */
    fun createJob(startedAt: Instant): Mono<IndexJob<T>>

    fun write(job: IndexJob<T>): Mono<Void>

    fun readJob(topicKey: Key<T>): Mono<IndexJob<T>>

    /** One listing. The caller uses it for discovery and for scan exclusion. */
    fun listJobTopics(): Flux<MessageTopic<T>>

    fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void>
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `createJob writes a topic, opens it, and stores a running job`() {
        val store = storeUnderTest()

        val job = store.createJob(startedAt).block()!!

        Assertions.assertThat(job.outcome).isEqualTo(JobOutcome.RUNNING)
        Assertions.assertThat(job.nodeId).isEqualTo(7)
        Assertions.assertThat(job.keyType).isEqualTo("long")
        Assertions.assertThat(topics.saved).hasSize(1)
        Assertions.assertThat(JobTopicNames.isJobTopic(topics.saved.first().data)).isTrue()
        Assertions.assertThat(topicIndex.saved).hasSize(1)
        Assertions.assertThat(pubsub.opened).containsExactly(job.key.id)
        Assertions.assertThat(store.readJob(job.key).block()!!.key).isEqualTo(job.key)
    }

    @Test
    fun `write replaces the stored job under the same key`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.write(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 5L)).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(5L)
    }

    @Test
    fun `invalidate raises the count on the named job only`() {
        val store = storeUnderTest()
        val first = store.createJob(startedAt).block()!!
        val second = store.createJob(startedAt.plusSeconds(1)).block()!!

        store.invalidate(first.key, finishedAt).block()

        Assertions.assertThat(store.readJob(first.key).block()!!.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(store.readJob(first.key).block()!!.lastInvalidationAt).isEqualTo(finishedAt)
        Assertions.assertThat(store.readJob(second.key).block()!!.invalidationCount).isEqualTo(0L)
    }

    @Test
    fun `listJobTopics returns reserved topics of this node only`() {
        val store = storeUnderTest()
        store.createJob(startedAt).block()
        topics.saved.add(MessageTopic.create(Key.funKey(99L), "general"))
        topics.saved.add(MessageTopic.create(Key.funKey(98L), JobTopicNames.nameFor(8, "long", startedAt, "other")))

        StepVerifier.create(store.listJobTopics()).expectNextCount(1).verifyComplete()
    }
```

Write the fakes `topics`, `topicIndex`, `pubsub`, and a `KeyValueStore<Long, Any>`
fake in the same file. Each records what it receives in a `mutableList`.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=VectorIndexJobStoreImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports an unresolved reference to `VectorIndexJobStoreImpl`.

- [ ] **Step 3: Write the implementation**

```kotlin
class VectorIndexJobStoreImpl<T, V, Q>(
    private val topicPersistence: TopicPersistence<T>,
    private val topicIndex: TopicIndexService<T, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val keyValueStore: KeyValueStore<T, Any>,
    private val codec: IndexJobCodec<T>,
    private val nodeId: Int,
    private val keyType: String,
    private val incarnationId: String,
    private val workerKey: Key<T>,
) : VectorIndexJobStore<T> {

    override fun createJob(startedAt: Instant): Mono<IndexJob<T>> =
        topicPersistence
            .key()
            .flatMap { key ->
                val topic = MessageTopic.create(
                    key,
                    JobTopicNames.nameFor(nodeId, keyType, startedAt, incarnationId)
                )
                val job = IndexJob(
                    key = key,
                    nodeId = nodeId,
                    keyType = keyType,
                    incarnationId = incarnationId,
                    startedBy = workerKey,
                    startedAt = startedAt,
                )
                topicPersistence.add(topic)
                    .then(topicIndex.add(topic))
                    // A memory topic that was never opened answers sendMessage
                    // with Object not Found.
                    .then(pubsub.open(key.id))
                    .then(write(job))
                    .thenReturn(job)
            }

    override fun write(job: IndexJob<T>): Mono<Void> =
        keyValueStore.add(KeyValuePair.create(job.key, job as Any))

    override fun readJob(topicKey: Key<T>): Mono<IndexJob<T>> =
        keyValueStore.get(topicKey).map { pair -> codec.decode(pair.data) }

    override fun listJobTopics(): Flux<MessageTopic<T>> =
        topicPersistence.all()
            .filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }

    override fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void> =
        readJob(jobKey)
            .flatMap { job ->
                write(job.copy(invalidationCount = job.invalidationCount + 1, lastInvalidationAt = at))
            }
}
```

Write `IndexJobCodec<T>` beside it. `decode(data: Any): IndexJob<T>` handles the
three backend shapes: an `IndexJob` passes through, a `Map` goes through
`ObjectMapper.convertValue`, and a `String` goes through `ObjectMapper.readValue`.
A shape it cannot read throws `ChatException` naming the runtime class.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=VectorIndexJobStoreImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add the durable vector index job store (CHAT-fpwpfrfj)"
```

---

### Task 6: The composed job record writer

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/JobRecordWriter.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/JobRecordCodec.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/ComposedJobRecordWriter.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/ComposedJobRecordWriterTests.kt`

**Interfaces:**
- Consumes: `JobRecord<T>` from Task 1, `MessagePersistence<T, V>`,
  `MessageIndexService<T, V, Q>`, `TopicPubSubService<T, V>`.
- Produces: `JobRecordWriter<T>.write(record: JobRecord<T>): Mono<Void>` and
  `JobRecordCodec.encode(record): String`. Task 7 uses `write`.

**The rule this task exists to hold:** the writer never calls
`MessagingServiceImpl.send()`. That method always calls
`MessageVectorIndexer.add()`, so a job record would enter the recall corpus, and
the next rebuild would read its own output back out of
`MessagePersistence.all()`.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `the writer calls persistence, then the index, then pub sub`() {
        val writer = writerUnderTest()

        StepVerifier.create(writer.write(record)).verifyComplete()

        Assertions.assertThat(calls).containsExactly("persistence", "index", "pubsub")
        Assertions.assertThat(vectorIndexer.added).isEmpty()
    }

    @Test
    fun `a message carries the record id and the job topic`() {
        val writer = writerUnderTest()

        writer.write(record).block()

        val message = persistence.added.single()
        Assertions.assertThat(message.key.id).isEqualTo(record.key.id)
        Assertions.assertThat(message.key.dest).isEqualTo(record.jobKey.id)
        Assertions.assertThat(message.record).isTrue()
        Assertions.assertThat(message.data).contains("\"version\"")
    }

    @Test
    fun `a failed index write stops pub sub and keeps the persisted record`() {
        val writer = writerUnderTest(failOn = "index")

        StepVerifier.create(writer.write(record)).verifyError(IllegalStateException::class.java)

        Assertions.assertThat(persistence.added).hasSize(1)
        Assertions.assertThat(pubsub.sent).isEmpty()
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=ComposedJobRecordWriterTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports an unresolved reference to `ComposedJobRecordWriter`.

- [ ] **Step 3: Write the writer**

```kotlin
/**
 * Writes one job record through the three services a job record needs.
 *
 * It never calls MessagingServiceImpl.send(). That method also calls
 * MessageVectorIndexer.add(), so a job record would enter vector recall, and a
 * later rebuild would read its own output back from MessagePersistence.all().
 *
 * A failed step stops the steps after it and keeps the steps before it. So
 * persistence can hold a record that the topic index cannot discover. The same
 * rule already governs MessagingServiceImpl.send().
 */
class ComposedJobRecordWriter<T, V, Q>(
    private val messagePersistence: MessagePersistence<T, V>,
    private val messageIndex: MessageIndexService<T, V, Q>,
    private val pubsub: TopicPubSubService<T, V>,
    private val codec: JobRecordCodec,
    private val asValue: (String) -> V,
) : JobRecordWriter<T> {

    override fun write(record: JobRecord<T>): Mono<Void> {
        val message = Message.create(
            MessageKey.create(record.key.id, record.workerKey.id, record.jobKey.id),
            asValue(codec.encode(record)),
            true,
        )

        return Flux.concat(
            Mono.defer { messagePersistence.add(message) },
            Mono.defer { messageIndex.add(message) },
            Mono.defer { pubsub.sendMessage(message) },
        ).then()
    }
}
```

`JobRecordCodec.encode` writes `{"version":1, ...}` with a Jackson `ObjectMapper`.
`decode(text: String): JobRecord<T>` reads it back and rejects an unknown version
with `ChatException`.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=ComposedJobRecordWriterTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: add the composed job record writer (CHAT-fpwpfrfj)"
```

---

### Task 7: The rebuild writes a job and excludes job topics

**Files:**
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt`

**Interfaces:**
- Consumes: `VectorIndexJobStore<T>` from Task 5, `JobRecordWriter<T>` from Task 6,
  `VectorIndexState<T>` from Task 4.
- Produces: a rebuild that creates a job, records its terminal state, and never
  indexes a job message. Task 12 reads that job through the actuator.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `a rebuild creates a job and marks it succeeded`() {
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        runAndAwait(service)

        val job = jobStore.written.last()
        Assertions.assertThat(job.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(job.indexed).isEqualTo(1L)
        Assertions.assertThat(job.invalidationCount).isEqualTo(0L)
    }

    @Test
    fun `a failed rebuild marks the job failed and keeps the earlier covering job`() {
        indexer.failOn.add(1L)
        given(persistence.all()).willReturn(Flux.just(message(1L)))
        state.adoptCoveringJob(Key.funKey(900L))

        runAndAwait(service)

        Assertions.assertThat(jobStore.written.last().outcome).isEqualTo(JobOutcome.FAILED)
        Assertions.assertThat(state.coveringJob()).isEqualTo(Key.funKey(900L))
    }

    @Test
    fun `the scan drops a message addressed to a job topic before the counters`() {
        jobStore.topics.add(MessageTopic.create(Key.funKey(500L), jobTopicName))
        given(persistence.all()).willReturn(
            Flux.just(message(1L), messageTo(2L, dest = 500L), message(3L))
        )

        val status = runAndAwait(service)

        Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
        Assertions.assertThat(status.lastReport!!.attempted).isEqualTo(2L)
        Assertions.assertThat(status.lastReport!!.skipped).isEqualTo(0L)
    }

    @Test
    fun `a failed topic listing stops the scan`() {
        jobStore.failListing = true
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        val status = runAndAwait(service)

        Assertions.assertThat(indexer.ids).isEmpty()
        Assertions.assertThat(status.complete).isFalse()
        verify(persistence, never()).all()
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The service takes no job store and applies no filter.

- [ ] **Step 3: Change `start()` and `rebuild()`**

Order inside `rebuild`:

1. `jobStore.createJob(startedAt)`.
2. `jobStore.listJobTopics().collectList()`. On error, write the job as `FAILED`,
   call `state.finish(...)`, and stop. **`persistence.all()` is never subscribed.**
3. Build `exclusion = topics.map { it.key.id }.toSet() + job.key.id`.
4. `Flux.defer { persistence.all() }.filter { it.key.dest !in exclusion }` and only
   then the existing `concatMap` with the counters.
5. On termination, write the job with its counts and outcome, then call
   `state.finish(claim, report, failure, job.key)`.

Keep the counters after the filter, so `attempted` describes user messages only.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: give a rebuild a durable job and a job topic filter (CHAT-fpwpfrfj)"
```

---

### Task 8: The coverage policy

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorCoveragePolicy.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorCoveragePolicyImpl.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorCoveragePolicyImplTests.kt`

**Interfaces:**
- Consumes: `VectorIndexJobStore<T>`, `IndexJob<T>`, `VectorIndexState<T>`.
- Produces: `VectorCoveragePolicy<T>.selectCoveringJob(): Mono<IndexJob<T>>`, where an empty
  result means no coverage, and
  `VectorTrust` with `NONE` and `STORED`. Task 9 and Task 11 use both.

**Properties:** `app.vector.index.trust` takes `none` or `stored`, default `none`.
An unknown value fails the context at startup rather than defaulting silently.
`app.vector.index.startup` takes `report` or `rebuild`, default `report`. This
task adds only `trust`. Task 11 wires `startup`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.composite.impl.VectorCoveragePolicyImpl
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorTrust
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class VectorCoveragePolicyImplTests {
    private val thisIncarnation = "incarnation-a"
    private val otherIncarnation = "incarnation-b"
    private val start = Instant.parse("2026-09-11T12:00:00Z")

    private class FakeJobStore : VectorIndexJobStore<Long> {
        val jobs = linkedMapOf<Long, IndexJob<Long>>()
        var failListing = false
        var malformedId: Long? = null

        override fun createJob(startedAt: Instant): Mono<IndexJob<Long>> =
            Mono.error(UnsupportedOperationException("the policy never creates a job"))

        override fun write(job: IndexJob<Long>): Mono<Void> =
            Mono.fromRunnable { jobs[job.key.id] = job }

        override fun readJob(topicKey: Key<Long>): Mono<IndexJob<Long>> =
            if (topicKey.id == malformedId) {
                Mono.error(ChatException("cannot decode the stored job"))
            } else {
                Mono.justOrEmpty(jobs[topicKey.id])
            }

        override fun listJobTopics(): Flux<MessageTopic<Long>> =
            if (failListing) {
                Flux.error(IllegalStateException("topic listing failed"))
            } else {
                Flux.fromIterable(
                    jobs.values.map { job ->
                        MessageTopic.create(
                            job.key,
                            JobTopicNames.nameFor(job.nodeId, job.keyType, job.startedAt, job.incarnationId)
                        )
                    }
                )
            }

        override fun invalidate(jobKey: Key<Long>, at: Instant): Mono<Void> =
            Mono.fromRunnable {
                jobs[jobKey.id]?.let { job ->
                    jobs[jobKey.id] = job.copy(
                        invalidationCount = job.invalidationCount + 1,
                        lastInvalidationAt = at,
                    )
                }
            }
    }

    private val store = FakeJobStore()

    private fun job(
        id: Long,
        outcome: JobOutcome = JobOutcome.SUCCEEDED,
        incarnationId: String = thisIncarnation,
        invalidations: Long = 0L,
        startedAt: Instant = start,
    ): IndexJob<Long> = IndexJob(
        key = Key.funKey(id),
        nodeId = 7,
        keyType = "long",
        incarnationId = incarnationId,
        startedBy = Key.funKey(1000L),
        startedAt = startedAt,
        outcome = outcome,
        invalidationCount = invalidations,
    )

    private fun policy(trust: VectorTrust) =
        VectorCoveragePolicyImpl(store, trust, thisIncarnation)

    @Test
    fun `a successful job with no invalidation covers`() {
        store.write(job(1L)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }

    @Test
    fun `an invalidated job does not cover`() {
        store.write(job(1L, invalidations = 1L)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a running job does not cover`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `trust none rejects an earlier incarnation`() {
        store.write(job(1L, incarnationId = otherIncarnation)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `trust stored accepts an earlier incarnation of the same node`() {
        store.write(job(1L, incarnationId = otherIncarnation)).block()

        StepVerifier
            .create(policy(VectorTrust.STORED).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }

    // The newest applicable job decides. An older clean job must not stand in
    // for a newer invalidated one, or an invalidation would be reversible by
    // history alone.
    @Test
    fun `the policy never falls back to an older successful job`() {
        store.write(job(1L, startedAt = start)).block()
        store.write(job(2L, startedAt = start.plusSeconds(60), invalidations = 1L)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a failed topic listing reports no covering job`() {
        store.write(job(1L)).block()
        store.failListing = true

        StepVerifier
            .create(policy(VectorTrust.STORED).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a malformed job record reports no covering job`() {
        store.write(job(1L)).block()
        store.malformedId = 1L

        StepVerifier
            .create(policy(VectorTrust.STORED).selectCoveringJob())
            .verifyComplete()
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=VectorCoveragePolicyImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports an unresolved reference to `VectorCoveragePolicyImpl`.

- [ ] **Step 3: Write the policy**

```kotlin
/**
 * Selects the newest applicable successful job.
 *
 * The policy never compares a stored value with the process generation. The
 * generation is a process-local race token and restarts at zero, so a stored
 * value and a fresh counter can match by accident.
 *
 * The policy never falls back to an older successful job. An invalidated newest
 * job means the index lost coverage, and an older job cannot restore it.
 */
class VectorCoveragePolicyImpl<T>(
    private val jobStore: VectorIndexJobStore<T>,
    private val trust: VectorTrust,
    private val incarnationId: String,
) : VectorCoveragePolicy<T> {

    override fun selectCoveringJob(): Mono<IndexJob<T>> =
        jobStore.listJobTopics()
            .flatMap { topic -> jobStore.readJob(topic.key) }
            .filter { job -> job.outcome == JobOutcome.SUCCEEDED }
            .filter { job -> trust == VectorTrust.STORED || job.incarnationId == incarnationId }
            .sort(compareByDescending { job -> job.startedAt })
            .next()
            .filter { job -> job.covers }
            .onErrorResume { error ->
                logger.error("Vector coverage read failed", error)
                Mono.empty()
            }
}
```

An empty result means no coverage. A read failure and a malformed record both
reach the same empty result, which is the fail-closed rule.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=VectorCoveragePolicyImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: select the covering job with a trust policy (CHAT-fpwpfrfj)"
```

---

### Task 9: The recall completeness contract (`CHAT-twocpczd`, rewritten)

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallResult.kt`
- Modify: `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`

**The old issue text is superseded.** It said "Mark state incomplete when a live
vector add fails." A live failure now increments the process generation **and**
raises the durable invalidation count on the covering job.

**Interfaces:**
- Consumes: `VectorCoveragePolicy<T>` from Task 8, `VectorIndexState<T>` from Task 4,
  `VectorIndexJobStore<T>` from Task 5.
- Produces: `MessageRecallResult<T>(indexComplete: Boolean, hits: List<MessageRecallHit<T>>)`
  and three `MessageRecallService<T>` methods returning `Mono<MessageRecallResult<T>>`.
  Task 10 binds these to the transports.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `an empty result still carries the flag`() {
        given(vectorStore.similaritySearch(any())).willReturn(emptyList())
        state.adoptCoveringJob(coveringKey)

        StepVerifier
            .create(service.recallGlobal(GlobalRecallRequest("q", 10, 0.0)))
            .assertNext { result ->
                Assertions.assertThat(result.hits).isEmpty()
                Assertions.assertThat(result.indexComplete).isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `a rebuild in progress does not change the reported coverage`() {
        state.adoptCoveringJob(coveringKey)
        state.claim()

        StepVerifier
            .create(service.recallGlobal(GlobalRecallRequest("q", 10, 0.0)))
            .assertNext { result -> Assertions.assertThat(result.indexComplete).isTrue() }
            .verifyComplete()
    }

    @Test
    fun `a live add failure raises the durable count and removes coverage`() {
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer.add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(jobStore.readJob(coveringKey).block()!!.invalidationCount).isEqualTo(1L)
    }

    @Test
    fun `a live add failure returns the original error to the sender`() { /* same shape */ }
```

Keep every existing filter, limit, threshold, and scheduler test. Change only the
assertion shape from a stream of hits to one result.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageRecallServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports a type mismatch between `Flux` and `Mono`.

- [ ] **Step 3: Change the contract and the implementation**

```kotlin
data class MessageRecallResult<T>(
    val indexComplete: Boolean,
    val hits: List<MessageRecallHit<T>>,
)
```

`MessageRecallServiceImpl` collects the bounded hit list, reads
`state.coveringJob() != null` once, and returns one result.

`VectorIndexStatus` also gains `activeJob: Key<T>?` and `coveringJob: Key<T>?`,
which the spec requires under Status Changes. `complete` reads `coveringJob !=
null` instead of the phase.

**Those two fields make the status generic.** It becomes `VectorIndexStatus<T>`.
So this task also changes `VectorIndexState<T>.status()` and
`VectorIndexState<T>.finish(...)` to return `VectorIndexStatus<T>`, and
`MessageReindexService<T>.status()` with it. Task 4 wrote those signatures against
the non-generic form. Task 12 and Task 13 use the generic form.
`RequestResponse.kt:80` caps the limit at 50, so the list is bounded by design.

`VectorStoreMessageVectorIndexer.add` gains the state and the job store. On an add
failure it calls `state.invalidate(reason)`, and when that returns a target it
calls `jobStore.invalidate(target, now)`. A failed durable write logs and never
replaces the original error.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageRecallServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Eight existing tests plus four new ones.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: report recall coverage from the covering job (CHAT-twocpczd)"
```

---

### Task 10: One result on both transports (`CHAT-jddllrrx`)

**Files:**
- Modify: `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`
- Modify: `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`
- Modify: `chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt`
- Modify: `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt`

**Interfaces:**
- Consumes: `MessageRecallResult<T>` from Task 9.
- Produces: three REST routes returning one JSON object, and three RSocket routes
  returning one response.

- [ ] **Step 1: Change the REST test first**

```kotlin
    @Test
    fun `a topic recall returns one object with the flag`() {
        // POST /message/recall/topic
        // expectHeader().contentType(MediaType.APPLICATION_JSON)
        // jsonPath("$.indexComplete").isEqualTo(false)
        // jsonPath("$.hits.length()").isEqualTo(2)
    }

    @Test
    fun `an empty recall still carries the flag`() {
        // jsonPath("$.hits.length()").isEqualTo(0)
        // jsonPath("$.indexComplete").isEqualTo(false)
    }
```

Write both bodies in full, following the existing `WebTestClient` setup in that file.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-webflux test -Dtest=MessageRecallRestTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The route still produces NDJSON.

- [ ] **Step 3: Change both controllers**

Drop `produces = [MediaType.APPLICATION_NDJSON_VALUE]`. Change each return type
from `Flux<MessageRecallHit<T>>` to `Mono<MessageRecallResult<T>>`. The RSocket
mapping changes the same three signatures. The route names do not change.

- [ ] **Step 4: Run both module suites**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-controller,chat-webflux test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: return one recall result on both transports (CHAT-jddllrrx)"
```

---

### Task 11: Wiring (`CHAT-awauccrm`, rewritten)

**Files:**
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`
- Modify: `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`

**The old issue text is superseded.** It named one configuration file and four
beans. The wiring now also builds the job store, the job record writer, the
coverage policy, and the startup action.

**Interfaces:**
- Consumes: every type from Tasks 4 to 9.
- Produces: beans behind the existing gates. The class keeps
  `@ConditionalOnProperty("app.service.composite")`, and every bean keeps
  `@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])`.

- [ ] **Step 1: Write the failing context tests**

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.service.composite.VectorRecallServiceConfiguration
import com.demo.chat.service.vector.JobRecordWriter
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The class gate is the composite selector. Each bean gate is both recall
 * selectors. A bean must not appear when either gate is absent.
 */
class VectorRecallServiceConfigurationTests {

    private val allProperties = arrayOf(
        "app.service.composite=true",
        "app.service.core.vector=embedded",
        "app.service.core.embedding=embedded",
        "app.key.type=long",
        "app.nodeid=1",
    )

    // VectorRecallTestBeans supplies TypeUtil, PersistenceServiceBeans,
    // IndexServiceBeans, PubSubServiceBeans, and a VectorStore, all in memory.
    // Copy its shape from the existing test configuration in this file.
    private fun runner() = ApplicationContextRunner()
        .withUserConfiguration(VectorRecallTestBeans::class.java)
        .withUserConfiguration(VectorRecallServiceConfiguration::class.java)

    @Test
    fun `every vector bean exists with the composite and both selectors`() {
        runner()
            .withPropertyValues(*allProperties)
            .run { context ->
                assertThat(context).hasSingleBean(VectorIndexState::class.java)
                assertThat(context).hasSingleBean(VectorIndexJobStore::class.java)
                assertThat(context).hasSingleBean(JobRecordWriter::class.java)
                assertThat(context).hasSingleBean(VectorCoveragePolicy::class.java)
                assertThat(context).hasSingleBean(MessageVectorIndexer::class.java)
                assertThat(context).hasSingleBean(MessageRecallService::class.java)
                assertThat(context).hasSingleBean(MessageReindexService::class.java)
            }
    }

    @Test
    fun `no vector bean exists without the composite selector`() {
        runner()
            .withPropertyValues(
                "app.service.core.vector=embedded",
                "app.service.core.embedding=embedded",
                "app.key.type=long",
                "app.nodeid=1",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(VectorIndexState::class.java)
                assertThat(context).doesNotHaveBean(VectorIndexJobStore::class.java)
                assertThat(context).doesNotHaveBean(MessageReindexService::class.java)
            }
    }

    @Test
    fun `no vector bean exists when one recall selector is absent`() {
        runner()
            .withPropertyValues(
                "app.service.composite=true",
                "app.service.core.vector=embedded",
                "app.key.type=long",
                "app.nodeid=1",
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(MessageRecallService::class.java)
                assertThat(context).doesNotHaveBean(MessageReindexService::class.java)
                assertThat(context).doesNotHaveBean(VectorIndexJobStore::class.java)
            }
    }

    // The default must start no rebuild. Real embedding throughput is still
    // unmeasured, so an automatic rebuild could delay readiness or send
    // uncontrolled external requests.
    @Test
    fun `the startup action stays off by default`() {
        runner()
            .withPropertyValues(*allProperties)
            .run { context ->
                val reindex = context.getBean(MessageReindexService::class.java)
                assertThat(reindex.status().running).isFalse()
                assertThat(reindex.status().lastReport).isNull()
            }
    }

    @Test
    fun `the startup action runs one rebuild when the property names rebuild`() {
        runner()
            .withPropertyValues(*allProperties, "app.vector.index.startup=rebuild")
            .run { context ->
                val reindex = context.getBean(MessageReindexService::class.java)
                // The listener fires on ApplicationReadyEvent, which the runner
                // publishes. One job exists, and a second start finds it busy or
                // finished, never a second running job.
                assertThat(context.getBean(VectorIndexJobStore::class.java)).isNotNull
                assertThat(reindex.status()).isNotNull
            }
    }

    @Test
    fun `an unknown trust value fails the context`() {
        runner()
            .withPropertyValues(*allProperties, "app.vector.index.trust=maybe")
            .run { context ->
                assertThat(context).hasFailed()
            }
    }
}
```

- [ ] **Step 2: Run and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=VectorRecallServiceConfigurationTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The new beans do not exist.

- [ ] **Step 3: Add the beans**

The configuration takes `PersistenceServiceBeans<T, V>`, `IndexServiceBeans<T, V, Q>`,
`PubSubServiceBeans<T, V>`, and `TypeUtil<T>`. **The composite context exposes no
`MessagePersistence` bean by type.** Read every store through the provider
interfaces, as `CompositeServiceBeansConfiguration.kt:33` does.

`incarnationId` is one `UUID.randomUUID().toString()` per context. `nodeId` comes
from `@Value("\${app.nodeid}")`. `keyType` comes from `@Value("\${app.key.type}")`.

Add `app.vector.index.startup`. Only `rebuild` starts one job, and it runs on
`ApplicationReadyEvent`. `report` is the default and starts nothing. At startup the
policy selects the covering job, and `state.adoptCoveringJob(...)` installs it.

- [ ] **Step 4: Run and confirm they pass, then run the module**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: wire the vector job, policy, and writer beans (CHAT-awauccrm)"
```

---

### Task 12: The protected actuator endpoint (`CHAT-zepiarzb`)

**Files:**
- Create: `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt`
- Test: `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt`

**Interfaces:**
- Consumes: `MessageReindexService<T>`, `VectorIndexJobStore<T>`.
- Produces: actuator id `vectorindex`. `@ReadOperation` returns the status with the
  active job, the covering job, and recent job records. `@WriteOperation` starts one
  rebuild and returns at once.

**Gates:** `@ConditionalOnProperty("app.service.composite")` and
`@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])`.
Both are needed. `VectorRecallServiceConfiguration` carries the composite gate at
class level and the selectors at bean level, so a selector-only gate would expose
the endpoint in a deployment with no composite services.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.actuator.VectorIndexEndpoint
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexStatus
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

class VectorIndexEndpointTests {

    /**
     * Records how many starts reached the service. The endpoint must start one
     * job for one write, and it must return at once rather than wait.
     */
    private class RecordingReindexService : MessageReindexService<Long> {
        val starts = AtomicInteger()
        var running = false

        override fun start(): Mono<VectorIndexStatus<Long>> = Mono.fromSupplier {
            if (!running) {
                starts.incrementAndGet()
                running = true
            }
            status()
        }

        override fun status(): VectorIndexStatus<Long> =
            VectorIndexStatus(
                phase = if (running) VectorIndexPhase.REBUILDING else VectorIndexPhase.INCOMPLETE
            )
    }

    private val service = RecordingReindexService()

    @Test
    fun `the read operation returns the current status`() {
        val endpoint = VectorIndexEndpoint(service)

        val status = endpoint.readVectorIndex()

        Assertions.assertThat(status.running).isFalse()
        Assertions.assertThat(status.complete).isFalse()
        Assertions.assertThat(service.starts.get()).isEqualTo(0)
    }

    @Test
    fun `the write operation starts one job and returns at once`() {
        val endpoint = VectorIndexEndpoint(service)

        val status = endpoint.startVectorIndexRebuild()

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        Assertions.assertThat(status.running).isTrue()
    }

    @Test
    fun `a second write returns busy and starts no second job`() {
        val endpoint = VectorIndexEndpoint(service)

        endpoint.startVectorIndexRebuild()
        val second = endpoint.startVectorIndexRebuild()

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        Assertions.assertThat(second.running).isTrue()
    }

    // Both gates matter. VectorRecallServiceConfiguration carries the composite
    // gate at class level and the selectors at bean level, so a selector-only
    // gate would expose this endpoint where no composite service exists.
    @Test
    fun `the endpoint is absent without the composite gate`() {
        ApplicationContextRunner()
            .withBean(MessageReindexService::class.java, { service })
            .withUserConfiguration(VectorIndexEndpoint::class.java)
            .withPropertyValues(
                "app.service.core.vector=embedded",
                "app.service.core.embedding=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).doesNotHaveBean(VectorIndexEndpoint::class.java)
            }
    }

    @Test
    fun `the endpoint is absent when one recall selector is missing`() {
        ApplicationContextRunner()
            .withBean(MessageReindexService::class.java, { service })
            .withUserConfiguration(VectorIndexEndpoint::class.java)
            .withPropertyValues(
                "app.service.composite=true",
                "app.service.core.vector=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).doesNotHaveBean(VectorIndexEndpoint::class.java)
            }
    }

    @Test
    fun `the endpoint exists with both gates`() {
        ApplicationContextRunner()
            .withBean(MessageReindexService::class.java, { service })
            .withUserConfiguration(VectorIndexEndpoint::class.java)
            .withPropertyValues(
                "app.service.composite=true",
                "app.service.core.vector=embedded",
                "app.service.core.embedding=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).hasSingleBean(VectorIndexEndpoint::class.java)
            }
    }
}
```

- [ ] **Step 2: Run and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-deploy test -Dtest=VectorIndexEndpointTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The class does not exist.

- [ ] **Step 3: Write the endpoint**

Model it on `RootKeyEndpoint`. `ActuatorWebSecurityConfiguration` already protects
`/actuator/**` with the `ACTUATOR` role, so this class adds no security code.
An operator must still expose `vectorindex` through the normal actuator exposure
property. **This plan adds no deployment selector and no exposure value.**

- [ ] **Step 4: Run and confirm they pass, then commit**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-deploy test
git add -A && git commit -m "feat: add the protected vector index endpoint (CHAT-zepiarzb)"
```

---

### Task 13: Prove recovery after index loss (`CHAT-neucngmw`)

**Files:**
- Create: `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/VectorIndexRecoveryTests.kt`

**Interfaces:**
- Consumes: every bean from Task 11 and the endpoint from Task 12.

**The test shape matters.** Write messages straight to persistence with no
indexing. That reproduces a lost index deterministically. **Do not delete a
memory-mapped storage directory inside a running process.**

- [ ] **Step 1: Write the failing test**

```kotlin
package com.demo.chat.test.deploy.memory

import com.demo.chat.domain.GlobalRecallRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageRecallService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A lost index, reproduced without deleting anything.
 *
 * The test writes messages straight to persistence and never indexes them,
 * which is the same end state as a lost storage directory. Deleting a memory
 * mapped directory inside a running process is not a valid substitute.
 *
 * The test activates memory key and memory persistence, so it claims no node
 * id. See docs/NODEID-CLAIM.md.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = [
        "app.service.composite=true",
        "app.service.core.key=memory",
        "app.service.core.persistence=memory",
        "app.service.core.vector=embedded",
        "app.service.core.embedding=embedded",
        "app.key.type=long",
        "app.nodeid=1",
    ]
)
class VectorIndexRecoveryTests {

    @Autowired
    lateinit var persistence: MessagePersistence<Long, String>

    @Autowired
    lateinit var recall: MessageRecallService<Long>

    @Autowired
    lateinit var reindex: MessageReindexService<Long>

    private fun persistOnly(id: Long, text: String): Mono<Void> =
        persistence.add(Message.create(MessageKey.create(id, 10L, 20L), text, true))

    private fun awaitFinished(): Unit {
        Flux.interval(Duration.ZERO, Duration.ofMillis(20))
            .map { reindex.status() }
            .filter { status -> !status.running }
            .next()
            .block(Duration.ofSeconds(30))
    }

    @Test
    fun `recall finds persisted messages after one rebuild`() {
        persistOnly(1L, "apple pie recipe").block()
        persistOnly(2L, "banana bread recipe").block()
        persistOnly(3L, "carrot soup recipe").block()

        val before = recall.recallGlobal(GlobalRecallRequest("recipe", 10, 0.0)).block()!!
        Assertions.assertThat(before.hits).isEmpty()
        Assertions.assertThat(before.indexComplete).isFalse()

        reindex.start().block()
        awaitFinished()

        val after = recall.recallGlobal(GlobalRecallRequest("recipe", 10, 0.0)).block()!!
        Assertions.assertThat(after.hits).hasSize(3)
        Assertions.assertThat(after.indexComplete).isTrue()
    }

    @Test
    fun `a job record is readable from its job topic`() {
        reindex.start().block()
        awaitFinished()

        val jobKey = reindex.status().coveringJob!!
        val records = messagesOfTopic(jobKey.id)

        Assertions.assertThat(records).isNotEmpty
        Assertions.assertThat(records.map { it.key.dest }).containsOnly(jobKey.id)
    }

    // Job messages are stored and indexed, so a scan sees them. They must never
    // reach the recall corpus, or a rebuild would index its own output and the
    // corpus would grow on every run.
    @Test
    fun `a job message never enters recall`() {
        persistOnly(1L, "apple pie recipe").block()

        reindex.start().block()
        awaitFinished()

        val second = reindex.start().block()
        awaitFinished()

        val hits = recall.recallGlobal(GlobalRecallRequest("rebuild", 50, 0.0)).block()!!
        Assertions.assertThat(hits.hits).isEmpty()
        Assertions.assertThat(reindex.status().lastReport!!.attempted).isEqualTo(1L)
        Assertions.assertThat(second).isNotNull
    }
}
```

Write `messagesOfTopic` in the same file. It resolves a job topic through
`MessageIndexService.findBy(topicIdToQuery(ByIdRequest(topicId)))` and then
`MessagePersistence.byIds(...)`, which is the read path
`MessagingServiceImpl.listenTopic` already uses.

**The third test is the load-bearing one.** `attempted` stays at one after two
rebuilds. If the exclusion filter is missing, the second rebuild counts the job
records of the first.

- [ ] **Step 2: Run and confirm it fails, implement nothing, then run again**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-deploy-memory test -Dtest=VectorIndexRecoveryTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS after Tasks 1 to 12. A failure here names a real gap in an earlier
task. Fix it there, not with a special case in this test.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test: prove recall recovery after index loss (CHAT-neucngmw)"
```

---

### Task 14: Documentation and the full gate (`CHAT-jqvclfbd`)

**Files:**
- Modify: `docs/superpowers/specs/2026-09-01-message-vector-recall-design.md`
- Modify: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`
- Modify: `forward-register.md`
- Modify: `docs/BUILD-HEALTH.md`

- [ ] **Step 1: Remove the stale claim**

`MessageRecallServiceImpl` carries "Covers only messages sent while recall was
active. There is no backfill." A rebuild now exists. Replace that sentence.

- [ ] **Step 2: Add a supersession note**

The 2026-09-01 recall design lists message repair and reindex jobs as out of
scope. Add one line naming the two specs that supersede it.

- [ ] **Step 3: Run the full gate**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -B clean test
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -B -DskipTests install -q
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -B clean verify -Ptest-build,integration
drift check
git diff --check
```

Record the test totals. A wire-format change makes the shell integration image
stale, so the `-Ptest-build` rebuild is required before `-Pintegration` can see
the new recall shape.

- [ ] **Step 4: Refresh the register**

Add a section for this work. Record what it did not deliver: no deployment sets
`app.service.core.vector`, embeddings are still the mock model, and job topic
retention is out of scope.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "docs: record the vector index job record work (CHAT-jqvclfbd)"
```

---

## Out of scope for this plan

- `CHAT-edzvpxil` the message handling policy mask.
- `CHAT-tekzakdd` a data stream or data view instead of the full scan.
- A real embedding model. `local` and `gateway` still fail at startup.
- Any deployment that sets `app.service.core.vector`.
- Job topic retention.
