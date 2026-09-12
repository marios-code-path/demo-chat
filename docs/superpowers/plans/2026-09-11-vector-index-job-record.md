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

## One rule that is easy to drop

`addRoom()` calls `pubsub.open(room.key.id)` after it writes persistence and the
index. A job topic needs the same third step. Without it `sendMessage()` fails on
the memory backend with `Object not Found`, which is the defect recorded for
`CHAT-qonhhtuq`.

The spec carries the ordered sequence under Job Topic And Discovery, and **Task 5
calls `pubsub.open()`.** The plan raised this gap, and the spec was revised in
`2c9b408e`.

## Reconciliation with the open issues

| Issue | Outcome |
|-------|---------|
| `CHAT-twocpczd` | Rewritten. Task 9. Its old How described the superseded phase model. |
| `CHAT-awauccrm` | Rewritten. Task 11. Its old file list held one configuration file. |
| `CHAT-jddllrrx` | Kept. Task 10. |
| `CHAT-zepiarzb` | Kept. Task 12. |
| `CHAT-neucngmw` | Kept. Task 13. |
| `CHAT-jqvclfbd` | Kept. Task 15. |
| New | Task 14 proves the job decode on each backend, which the spec requires and no earlier task covered. |
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

import com.fasterxml.jackson.annotation.JsonIgnore
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
    /**
     * Derived, and never written.
     *
     * Jackson serializes a getter, and this class has no matching constructor
     * argument, so a stored job would fail to read back with an unrecognized
     * field. The redis and cassandra shapes both go through Jackson.
     */
    @get:JsonIgnore
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

    // The index fails for every query here. A guard placed after findBy would
    // surface that failure instead of the rejection, so this pins the order.
    // A healthy index cannot pin it, because both orders pass against one.
    @Test
    fun `addRoom rejects a reserved name without reading the index`() {
        val service = topicServiceUnderTest(indexFailure = IllegalStateException("index unavailable"))

        StepVerifier
            .create(service.addRoom(ByStringRequest(jobName)))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error)
                    .isInstanceOf(ChatException::class.java)
                    .hasMessageContaining(JobTopicNames.PREFIX)
            }
    }

    // The complement. A name that is not reserved still reaches the index, so
    // the guard narrows nothing else.
    @Test
    fun `addRoom reads the index for a name that is not reserved`() {
        val service = topicServiceUnderTest(indexFailure = IllegalStateException("index unavailable"))

        StepVerifier
            .create(service.addRoom(ByStringRequest("general")))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error)
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("index unavailable")
            }
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

`topicServiceUnderTest` builds the service from the ten constructor arguments
`TopicServiceImpl` declares. Only the topic store, the topic index, and pub/sub
take part in these two tests, so the rest are dummies.

```kotlin
    private fun topicServiceUnderTest(
        existing: List<MessageTopic<Long>> = emptyList(),
        indexFailure: Throwable? = null,
    ): ChatTopicService<Long, String> {
        val stored = existing.toMutableList()
        val indexed = mutableListOf<MessageTopic<Long>>()
        var nextId = 100L

        val topicPersistence = object : TopicPersistence<Long> {
            override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
            override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { stored.add(ent) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { stored.removeIf { it.key == key } }
            override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
                Mono.justOrEmpty(stored.firstOrNull { it.key == key })
            override fun all(): Flux<out MessageTopic<Long>> = Flux.fromIterable(stored)
        }

        val topicIndex = object : TopicIndexService<Long, IndexSearchRequest> {
            override fun add(entity: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { indexed.add(entity) }
            override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { indexed.removeIf { it.key == key } }
            override fun findBy(query: IndexSearchRequest): Flux<out Key<Long>> =
                if (indexFailure != null) {
                    Flux.error(indexFailure)
                } else {
                    Flux.fromIterable(indexed.filter { it.data == query.second }.map { it.key })
                }
            override fun findUnique(query: IndexSearchRequest): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
        }

        val pubsub = object : TopicPubSubService<Long, String> {
            val opened = mutableListOf<Long>()
            override fun open(topicId: Long): Mono<Void> = Mono.fromRunnable { opened.add(topicId) }
            override fun close(topicId: Long): Mono<Void> = Mono.empty()
            override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
            override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
            override fun subscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
            override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()
            override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.empty()
            override fun listenTo(topic: Long): Flux<out Message<Long, String>> = Flux.empty()
            override fun exists(topic: Long): Mono<Boolean> = Mono.just(true)
        }

        return TopicServiceImpl(
            topicPersistence = topicPersistence,
            topicIndex = topicIndex,
            pubsub = pubsub,
            userPersistence = DummyUserPersistence(),
            membershipPersistence = DummyMembershipPersistence(),
            membershipIndex = DummyMembershipIndex(),
            emptyDataCodec = Supplier { "" },
            topicNameToQuery = Function { req -> IndexSearchRequest("name", req.name, 100) },
            memberOfIdToQuery = Function { IndexSearchRequest("memberOf", "", 100) },
            memberWithTopicToQuery = Function { IndexSearchRequest("member", "", 100) },
        )
    }
```

`DummyUserPersistence`, `DummyMembershipPersistence`, and `DummyMembershipIndex`
are the existing dummies in `com.demo.chat.service.dummy`. Neither test reaches
them.

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
                .flatMap { exists ->
                    if (exists) {
                        Mono.error(DuplicateException)
                    } else {
                        topicPersistence
                            .key()
                            .map { key -> MessageTopic.create(key, req.name) }
                            .flatMap { room ->
                                topicPersistence
                                    .add(room)
                                    .then(topicIndex.add(room))
                                    .then(pubsub.open(room.key.id))
                                    .then(Mono.just(room.key))
                            }
                    }
                }
        }
```

The inner block is the current body of `addRoom`, moved under the new guard and
otherwise unchanged. The duplicate-name check from PR #61 stays exactly as it is.

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
Expected: PASS, 3 tests.

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
  `finish(claim, report, failure, jobKey): VectorFinishResult`,
  `adoptCoveringJob(jobKey)`, and `coveringJob()`. Task 5, Task 7, and Task 8 use
  all four. `VectorInvalidation<T>(generation, target)`.

**Why the state becomes generic:** the target is a `Key<T>`. A raw `Key<*>` would
push an unchecked cast into every caller.

**`complete` still reads the phase in this task.** It moves to the covering job
in Task 9. The verdict and the index state cannot differ until then, because a
failed run sets the phase to `INCOMPLETE` either way. The test that pins that
difference therefore lives in Task 9, not here.

- [ ] **Step 1: Write the failing tests**

Add these seven to `InMemoryVectorIndexStateTests`, and change the class field to
`InMemoryVectorIndexState<Long>()`. The five tests already in the class keep
their names. Each one gains the `jobKey` argument, and each reads its assertions
from `result.status` rather than from a returned status.

They need five imports beyond the ones the class already holds:
`com.demo.chat.service.vector.VectorFinishResult`,
`com.demo.chat.service.vector.VectorInvalidation`,
`java.util.concurrent.CyclicBarrier`, `java.util.concurrent.Executors`, and
`java.util.concurrent.TimeUnit`.

**The last two need contention, and one race is not enough.** The window between
a read and a write is small, so a single round lets a broken implementation
pass. Measured on this code: a claim race of one round passed against a claim
that wrote without compare-and-set, and two hundred rounds failed it at round
104. A finish race of two hundred rounds passed against the same fault in
finish, and two thousand rounds failed it.

```kotlin
    @Test
    fun `a successful finish installs its job as the invalidation target`() {
        val state = InMemoryVectorIndexState<Long>()
        val claim = state.claim()
        val jobKey = Key.funKey(11L)

        val result = state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L), null, jobKey)

        Assertions.assertThat(result.succeeded).isTrue()
        Assertions.assertThat(state.coveringJob()).isEqualTo(jobKey)
    }

    @Test
    fun `a failed finish does not install its job as the target`() {
        val state = InMemoryVectorIndexState<Long>()
        state.adoptCoveringJob(Key.funKey(9L))
        val claim = state.claim()

        val result = state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 0L, 0L, 1L), "boom", Key.funKey(11L))

        Assertions.assertThat(result.succeeded).isFalse()
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

        val result = state.finish(claim, VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L), null, Key.funKey(11L))

        Assertions.assertThat(result.succeeded).isFalse()
        Assertions.assertThat(result.status.complete).isFalse()
        // invalidate() cleared the target, and a losing finish installs
        // nothing. So no job covers until the next successful run.
        Assertions.assertThat(state.coveringJob()).isNull()
    }

    /**
     * The sequential claim test cannot reach the compare-and-set loop.
     *
     * One race is not enough either. The window between a read and a write is
     * small, so a single round lets a broken implementation pass. The race
     * repeats, and every round must accept exactly one caller.
     */
    @Test
    fun `only one of many concurrent claims is accepted`() {
        val callers = 16
        val pool = Executors.newFixedThreadPool(callers)

        try {
            repeat(200) { round ->
                val state = InMemoryVectorIndexState<Long>()
                val barrier = CyclicBarrier(callers)

                val results = (0 until callers).map {
                    pool.submit<Boolean> {
                        barrier.await(10, TimeUnit.SECONDS)
                        state.claim().accepted
                    }
                }

                val accepted = results.count { result -> result.get(10, TimeUnit.SECONDS) }

                Assertions.assertThat(accepted)
                    .`as`("round %d accepts one claim", round)
                    .isEqualTo(1)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * A finish and an invalidation race two hundred times.
     *
     * Two outcomes are coherent, and no third exists. A finish that wins
     * installs its job, so the invalidation that follows reports that new job.
     * An invalidation that wins moves the generation, so the finish behind it
     * fails and installs nothing, and the invalidation reports the earlier job.
     *
     * The target is null after either outcome, because the invalidation always
     * clears what it found.
     */
    @Test
    fun `a concurrent finish and invalidation reach one coherent outcome`() {
        val prior = Key.funKey(9L)
        val fresh = Key.funKey(11L)
        val pool = Executors.newFixedThreadPool(2)

        try {
            repeat(2000) {
                val state = InMemoryVectorIndexState<Long>()
                state.adoptCoveringJob(prior)
                val claim = state.claim()
                val barrier = CyclicBarrier(2)

                val finishing = pool.submit<VectorFinishResult> {
                    barrier.await(10, TimeUnit.SECONDS)
                    state.finish(
                        claim,
                        VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L),
                        null,
                        fresh,
                    )
                }
                val invalidating = pool.submit<VectorInvalidation<Long>> {
                    barrier.await(10, TimeUnit.SECONDS)
                    state.invalidate("live vector add failed")
                }

                val finished = finishing.get(10, TimeUnit.SECONDS)
                val invalidated = invalidating.get(10, TimeUnit.SECONDS)

                if (finished.succeeded) {
                    Assertions.assertThat(invalidated.target)
                        .`as`("a finish that wins installs its job before the invalidation reads it")
                        .isEqualTo(fresh)
                } else {
                    Assertions.assertThat(invalidated.target)
                        .`as`("an invalidation that wins reports the job it replaced")
                        .isEqualTo(prior)
                }

                Assertions.assertThat(state.coveringJob())
                    .`as`("the invalidation clears whatever it found")
                    .isNull()
            }
        } finally {
            pool.shutdownNow()
        }
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

/**
 * The outcome of one run.
 *
 * [succeeded] is true only when this run finished with no failed message, no
 * scan error, and an unchanged generation. [status] describes the index, which
 * an earlier job can still cover.
 */
data class VectorFinishResult(
    val succeeded: Boolean,
    val status: VectorIndexStatus,
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
     * Installs [jobKey] as the covering job when this run succeeded and the
     * generation did not move. A losing run leaves the current target
     * unchanged, which is null when an invalidation cleared it.
     *
     * [jobKey] is null when the caller holds no durable job. The run can then
     * still succeed in process, and it installs no target.
     *
     * The result carries a verdict for this run, not for the index. A caller
     * that writes a durable outcome reads `succeeded`.
     *
     * In this task `status.complete` still reads the phase, so a failed run
     * reports both values false. Task 9 moves `complete` to the covering job,
     * and the two answers can differ from then on.
     */
    fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
        jobKey: Key<T>?,
    ): VectorFinishResult

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
Expected: PASS, 12 tests.

- [ ] **Step 6: Repair the reindex service call site**

`MessageReindexServiceImpl` calls `state.finish(claim, report, failure)`. It has
no durable job yet, so pass `null` for `jobKey`. That is the honest value, not a
stand-in: a run with no durable job installs no target, and the contract says so.
Task 7 passes the real key once the service creates a job.

- [ ] **Step 7: Run the composite module and commit**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test
git add -A && git commit -m "feat: hold the invalidation target in the vector index state (CHAT-fpwpfrfj)"
```

---

### Task 5: The durable job store

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexJobStore.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/IndexJobCodec.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorIndexJobStoreImpl.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/SerialWriter.kt`
- Create: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorTestFakes.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorIndexJobStoreImplTests.kt`
- Test: `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/SerialWriterTests.kt`

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

    /**
     * Applies a terminal outcome and its counts.
     *
     * It never lowers the stored invalidation fields. An invalidation that
     * lands between the state decision and this write must survive it,
     * otherwise a restart under `stored` would trust a job that a live failure
     * already invalidated.
     *
     * This call and [invalidate] run in order on one worker, so no pair of
     * writes interleaves inside a read and a write.
     */
    fun finishJob(job: IndexJob<T>): Mono<Void>

    fun readJob(topicKey: Key<T>): Mono<IndexJob<T>>

    /** One listing. The caller uses it for discovery and for scan exclusion. */
    fun listJobTopics(): Flux<out MessageTopic<T>>

    fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void>
}
```

- [ ] **Step 1: Write the failing store tests**

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

    // Interleaving one. The invalidation lands first. The terminal write must
    // not lower the count back to the value the run carried.
    @Test
    fun `a terminal write keeps an invalidation that arrived first`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.invalidate(job.key, finishedAt).block()
        store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(3L)
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(read.covers).isFalse()
    }

    // Interleaving two. The terminal write lands first, and the invalidation
    // then applies to the job that just became the target.
    @Test
    fun `an invalidation after a terminal write raises the count`() {
        val store = storeUnderTest()
        val job = store.createJob(startedAt).block()!!

        store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).block()
        store.invalidate(job.key, finishedAt).block()

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(read.covers).isFalse()
    }

    // The two tests above call the operations in order, so they prove the merge
    // rule and nothing about contention. This one overlaps them. The store
    // reads through a publisher that completes only when the test releases it,
    // so a writer that did not wait would read before the first write landed.
    @Test
    fun `an overlapping invalidation does not interleave with a terminal write`() {
        // Each read waits on its own timer rather than on one shared gate. A
        // single sink resumes its subscribers one after another on the
        // emitting thread, so two operations behind it never truly overlap and
        // the test passes even without serialization.
        val store = storeUnderTest(readDelay = Mono.delay(Duration.ofMillis(50)).then())
        val job = store.createJob(startedAt).block()!!

        val terminal = store.finishJob(job.copy(outcome = JobOutcome.SUCCEEDED, indexed = 3L)).toFuture()
        val invalidation = store.invalidate(job.key, finishedAt).toFuture()

        terminal.get(10, TimeUnit.SECONDS)
        invalidation.get(10, TimeUnit.SECONDS)

        val read = store.readJob(job.key).block()!!
        Assertions.assertThat(read.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(read.indexed).isEqualTo(3L)
        // The invalidation ran after the terminal write, on the value that
        // write produced. A lost update would leave this at zero.
        Assertions.assertThat(read.invalidationCount).isEqualTo(1L)
    }

    // Every reserved topic, from every node. The scan exclusion needs all of
    // them, because another node's job records sit in the same message store.
    @Test
    fun `listJobTopics returns every reserved topic and no user topic`() {
        val store = storeUnderTest()
        store.createJob(startedAt).block()
        topics.saved.add(MessageTopic.create(Key.funKey(99L), "general"))
        topics.saved.add(MessageTopic.create(Key.funKey(98L), JobTopicNames.nameFor(8, "long", startedAt, "other")))

        StepVerifier.create(store.listJobTopics()).expectNextCount(2).verifyComplete()
    }
```

`storeUnderTest(readDelay: Mono<Void> = Mono.empty())` builds the store from four
fakes. `readDelay` is what makes the contention test possible: the key-value fake
waits on it before each read, so two operations can overlap on demand.

**These doubles are shared.** Task 6 and Task 11 use them too, so they go in one
file rather than as private classes inside a test. Create
`chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/VectorTestFakes.kt`:

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * In-memory doubles for the composite vector tests.
 *
 * They are internal rather than private, because three tasks use them. A
 * private class cannot leave its own file.
 *
 * A double that takes [calls] appends its own name on each write, so a test
 * can assert the order in which services were called.
 */
internal class FakeTopicPersistence : TopicPersistence<Long> {
    val saved = mutableListOf<MessageTopic<Long>>()
    private var nextId = 500L
    override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
    override fun add(ent: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { saved.add(ent) }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { saved.removeIf { it.key == key } }
    // Every read defers. A real store reads when a caller subscribes, and a
    // fake that reads at assembly time would hand back a value from before an
    // earlier write, which makes correct serialization look broken.
    override fun get(key: Key<Long>): Mono<out MessageTopic<Long>> =
        Mono.defer { Mono.justOrEmpty(saved.firstOrNull { it.key == key }) }
    override fun all(): Flux<out MessageTopic<Long>> = Flux.defer { Flux.fromIterable(saved.toList()) }
}

internal class FakeTopicIndex : TopicIndexService<Long, Map<String, String>> {
    val saved = mutableListOf<MessageTopic<Long>>()
    override fun add(entity: MessageTopic<Long>): Mono<Void> = Mono.fromRunnable { saved.add(entity) }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { saved.removeIf { it.key == key } }
    override fun findBy(query: Map<String, String>): Flux<out Key<Long>> =
        Flux.fromIterable(saved.filter { it.data == query["name"] }.map { it.key })
    override fun findUnique(query: Map<String, String>): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
}

internal class FakePubSub(private val calls: MutableList<String>? = null) : TopicPubSubService<Long, String> {
    val opened = mutableListOf<Long>()
    val sent = mutableListOf<Message<Long, String>>()
    override fun open(topicId: Long): Mono<Void> = Mono.fromRunnable { opened.add(topicId) }
    override fun close(topicId: Long): Mono<Void> = Mono.empty()
    override fun getByUser(uid: Long): Flux<Long> = Flux.empty()
    override fun getUsersBy(topicId: Long): Flux<Long> = Flux.empty()
    override fun subscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
    override fun unSubscribe(member: Long, topic: Long): Mono<Void> = Mono.empty()
    override fun unSubscribeAll(member: Long): Mono<Void> = Mono.empty()
    override fun unSubscribeAllIn(topic: Long): Mono<Void> = Mono.empty()
    override fun sendMessage(message: Message<Long, String>): Mono<Void> = Mono.fromRunnable {
        calls?.add("pubsub")
        sent.add(message)
    }
    override fun listenTo(topic: Long): Flux<out Message<Long, String>> = Flux.empty()
    override fun exists(topic: Long): Mono<Boolean> = Mono.just(true)
}

/**
 * The read waits on [readDelay] before it answers, so a caller can hold one
 * operation inside its read and start a second one.
 */
internal class FakeKeyValueStore(private val readDelay: Mono<Void> = Mono.empty()) : KeyValueStore<Long, Any> {
    val values = linkedMapOf<Long, KeyValuePair<Long, Any>>()
    override fun key(): Mono<out Key<Long>> = Mono.empty()
    override fun add(ent: KeyValuePair<Long, Any>): Mono<Void> = Mono.fromRunnable { values[ent.key.id] = ent }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.fromRunnable { values.remove(key.id) }
    override fun get(key: Key<Long>): Mono<out KeyValuePair<Long, Any>> =
        readDelay.then(Mono.defer { Mono.justOrEmpty(values[key.id]) })
    override fun all(): Flux<out KeyValuePair<Long, Any>> =
        Flux.defer { Flux.fromIterable(values.values.toList()) }
}

/**
 * [delay] holds the write open. A synchronous double cannot tell a chain that
 * waits for each step from one that starts them all at once, because both
 * subscribe in the same order.
 */
internal class FakeMessagePersistence(
    private val calls: MutableList<String>? = null,
    private val delay: Duration = Duration.ZERO,
    private val failure: Throwable? = null,
) : MessagePersistence<Long, String> {
    val added = mutableListOf<Message<Long, String>>()
    private var nextId = 900L
    override fun key(): Mono<out Key<Long>> = Mono.fromSupplier { Key.funKey(nextId++) }
    override fun add(ent: Message<Long, String>): Mono<Void> =
        Mono.delay(delay).then(
            Mono.defer {
                calls?.add("persistence")
                if (failure != null) {
                    Mono.error(failure)
                } else {
                    added.add(ent)
                    Mono.empty()
                }
            }
        )
    override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
    override fun get(key: Key<Long>): Mono<out Message<Long, String>> =
        Mono.defer { Mono.justOrEmpty(added.firstOrNull { it.key.id == key.id }) }
    override fun all(): Flux<out Message<Long, String>> = Flux.defer { Flux.fromIterable(added.toList()) }
    override fun byIds(keys: List<Key<Long>>): Flux<out Message<Long, String>> =
        Flux.defer { Flux.fromIterable(added.filter { message -> keys.any { it.id == message.key.id } }) }
}

internal class FakeMessageIndex(
    private val calls: MutableList<String>? = null,
    private val failOn: String? = null,
) : MessageIndexService<Long, String, Map<String, String>> {
    val added = mutableListOf<Message<Long, String>>()
    override fun add(entity: Message<Long, String>): Mono<Void> = Mono.defer {
        calls?.add("index")
        if (failOn == "index") {
            Mono.error(IllegalStateException("index is down"))
        } else {
            added.add(entity)
            Mono.empty()
        }
    }
    override fun rem(key: Key<Long>): Mono<Void> = Mono.empty()
    override fun findBy(query: Map<String, String>): Flux<out Key<Long>> =
        Flux.fromIterable(added.filter { it.key.dest.toString() == query["topic"] }.map { it.key })
    override fun findUnique(query: Map<String, String>): Mono<out Key<Long>> = findBy(query).singleOrEmpty()
}
```

The store test then holds instances and one builder:

```kotlin
    private val topics = FakeTopicPersistence()
    private val topicIndex = FakeTopicIndex()
    private val pubsub = FakePubSub()

    private fun storeUnderTest(readDelay: Mono<Void> = Mono.empty()): VectorIndexJobStore<Long> =
        VectorIndexJobStoreImpl(
            topicPersistence = topics,
            topicIndex = topicIndex,
            pubsub = pubsub,
            keyValueStore = FakeKeyValueStore(readDelay),
            codec = IndexJobCodec(ObjectMapper().findAndRegisterModules()),
            nodeId = 7,
            keyType = "long",
            incarnationId = "incarnation-a",
            workerKey = Key.funKey(1000L),
        )
```

The memory key-value store returns the stored object, so these tests exercise the
pass-through branch. `IndexJobCodecTests` covers the map and the JSON string
branches directly against a mapper carrying the chat modules. **Task 14 proves
the same decode against each real backend**, which is what the spec requires.

- [ ] **Step 2: Write the failing SerialWriter tests**

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/SerialWriterTests.kt`.

```kotlin
package com.demo.chat.test.service.composite

import com.demo.chat.domain.ChatException
import com.demo.chat.service.composite.impl.SerialWriter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class SerialWriterTests {

    // Concurrent submission is what produces FAIL_NON_SERIALIZED. Every caller
    // must still receive a completion.
    @Test
    fun `concurrent submissions all complete`() {
        val writer = SerialWriter()
        val done = AtomicInteger()

        StepVerifier
            .create(
                Flux.range(0, 200)
                    .parallel(8)
                    .runOn(Schedulers.boundedElastic())
                    .flatMap { writer.submit(Mono.fromRunnable { done.incrementAndGet() }) }
                    .then()
            )
            .verifyComplete()

        Assertions.assertThat(done.get()).isEqualTo(200)
        writer.close().block()
    }

    // One at a time means one at a time. A second body must not start while
    // the first is still running.
    @Test
    fun `work never overlaps`() {
        val writer = SerialWriter()
        val running = AtomicBoolean(false)
        val overlapped = AtomicBoolean(false)

        // fromRunnable completes empty, and delayElement only delays an onNext.
        // An empty completion passes straight through, so a delay after
        // fromRunnable creates no window at all. fromCallable emits a value,
        // which the delay can hold.
        //
        // The flag clears inside doOnNext, not in doFinally. Reactor runs a
        // doFinally callback after it propagates the terminal signal, so the
        // next body would start before the previous one cleared the flag, and
        // a correct writer would look like it overlapped.
        val body = Mono.fromCallable { !running.compareAndSet(false, true) }
            .delayElement(Duration.ofMillis(5))
            .doOnNext { collided ->
                if (collided) overlapped.set(true)
                running.set(false)
            }
            .then()

        StepVerifier
            .create(Flux.range(0, 50).flatMap { writer.submit(body) }.then())
            .verifyComplete()

        Assertions.assertThat(overlapped.get()).isFalse()
        writer.close().block()
    }

    // close() must let queued work finish. An immediate dispose would strand
    // every queued result, and the caller would wait for a completion that
    // never arrives.
    @Test
    fun `close drains queued work`() {
        val writer = SerialWriter()
        val started = AtomicInteger()
        val done = AtomicInteger()
        val slow = Mono.fromRunnable<Void> { started.incrementAndGet() }
            .then(Mono.delay(Duration.ofMillis(20)))
            .then(Mono.fromRunnable<Void> { done.incrementAndGet() })

        // submit returns a deferred Mono, so nothing reaches the queue until
        // something subscribes. toFuture subscribes now. Closing before this
        // would reject every submission instead of draining it.
        val all = Flux.merge((0 until 10).map { writer.submit(slow) }).then().toFuture()

        Flux.interval(Duration.ZERO, Duration.ofMillis(5))
            .filter { started.get() > 0 }
            .next()
            .block(Duration.ofSeconds(10))

        writer.close().block()
        all.get(10, TimeUnit.SECONDS)

        Assertions.assertThat(done.get()).isEqualTo(10)
    }

    /**
     * The hazard this test exists for.
     *
     * Each body needs 400 milliseconds and the shutdown timeout is 50, so no
     * body can finish. Every caller must receive the shutdown error.
     *
     * The assertion demands that error rather than accepting any termination.
     * A writer that ignored the timeout and drained normally would complete all
     * five callers, and a weaker assertion would pass it.
     */
    @Test
    fun `work that outlasts the shutdown timeout fails every caller`() {
        val writer = SerialWriter()
        val started = AtomicInteger()
        val slow = Mono.fromRunnable<Void> { started.incrementAndGet() }
            .then(Mono.delay(Duration.ofMillis(400)))
            .then()

        val outcomes = (0 until 5).map { writer.submit(slow).materialize().toFuture() }

        Flux.interval(Duration.ZERO, Duration.ofMillis(5))
            .filter { started.get() > 0 }
            .next()
            .block(Duration.ofSeconds(10))

        writer.close(Duration.ofMillis(50)).block()

        outcomes.forEach { outcome ->
            // A stranded caller appears here as a future that never resolves.
            val signal = outcome.get(10, TimeUnit.SECONDS)

            Assertions.assertThat(signal.hasError())
                .`as`("no body can finish inside the shutdown timeout")
                .isTrue()
            Assertions.assertThat(signal.throwable)
                .isInstanceOf(ChatException::class.java)
                .hasMessageContaining("shut down before this work ran")
        }
    }

    @Test
    fun `a submission after close fails rather than hanging`() {
        val writer = SerialWriter()
        writer.close().block()

        StepVerifier
            .create(writer.submit(Mono.empty()))
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `a failed body reaches its own caller only`() {
        val writer = SerialWriter()

        StepVerifier
            .create(writer.submit(Mono.error(IllegalStateException("write failed"))))
            .verifyError(IllegalStateException::class.java)

        StepVerifier
            .create(writer.submit(Mono.empty()))
            .verifyComplete()

        writer.close().block()
    }
}
```

- [ ] **Step 2b: Write the failing codec tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/IndexJobCodecTests.kt`.

**A bare `ObjectMapper` cannot read a `Key`.** It is an interface with no type
information on the wire, so the mapper registers the same modules the
deployments register.

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.IndexJobCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Each backend hands the store a different shape. The memory store returns the
 * object, redis returns a map, and cassandra returns the JSON string it stored.
 */
class IndexJobCodecTests {
    // The same modules the deployments register. A bare mapper cannot read a
    // Key, which is an interface with no type information on the wire.
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }
    private val codec = IndexJobCodec<Long>(mapper)

    private val job = IndexJob(
        key = Key.funKey(500L),
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(1000L),
        startedAt = Instant.parse("2026-09-12T12:00:00Z"),
        outcome = JobOutcome.SUCCEEDED,
        indexed = 3L,
        invalidationCount = 1L,
    )

    @Test
    fun `a stored object passes through`() {
        Assertions.assertThat(codec.decode(job)).isEqualTo(job)
    }

    @Test
    fun `a job survives a json string round trip`() {
        val decoded = codec.decode(mapper.writeValueAsString(job))

        Assertions.assertThat(decoded.key.id).isEqualTo(500L)
        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
        Assertions.assertThat(decoded.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(decoded.startedAt).isEqualTo(job.startedAt)
        Assertions.assertThat(decoded.covers).isFalse()
    }

    @Test
    fun `a job survives a map round trip`() {
        val asMap = mapper.convertValue(job, Map::class.java)

        val decoded = codec.decode(asMap)

        Assertions.assertThat(decoded.nodeId).isEqualTo(7)
        Assertions.assertThat(decoded.keyType).isEqualTo("long")
        Assertions.assertThat(decoded.incarnationId).isEqualTo("incarnation-a")
        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
    }

    @Test
    fun `an unknown shape names the runtime class`() {
        Assertions.assertThatThrownBy { codec.decode(42) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("java.lang.Integer")
    }
}
```

- [ ] **Step 3: Run all three suites and confirm they fail**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test \
  -Dtest=IndexJobCodecTests,VectorIndexJobStoreImplTests,SerialWriterTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: FAIL. The compiler reports unresolved references to
`VectorIndexJobStoreImpl`, `IndexJobCodec`, and `SerialWriter`.

**All three test classes are written before any implementation.** The earlier
order wrote the writer tests after its implementation, which left that class
with no red step at all.

- [ ] **Step 4: Write SerialWriter, the codec, and the store**

The store needs a codec, because each backend hands back a different shape.
`IndexJobCodec<T>` goes in `chat-core`, at
`com.demo.chat.service.vector.IndexJobCodec`.

**It belongs in `chat-core`, not beside the store.** Task 14 proves the decode
from the redis and cassandra test sources, and neither module depends on
`chat-service-composite`. Placing it in `chat-core` keeps those tests inside
dependencies that already exist, so no POM changes.

```kotlin
/**
 * Reads one stored job back from any backend shape.
 *
 * The memory store returns the object it was given. Redis returns a map after
 * its JSON round trip. Cassandra returns the JSON string it stored.
 */
class IndexJobCodec<T>(private val mapper: ObjectMapper) {

    @Suppress("UNCHECKED_CAST")
    fun decode(data: Any): IndexJob<T> = when (data) {
        is IndexJob<*> -> data as IndexJob<T>
        is Map<*, *> -> mapper.convertValue(data, IndexJob::class.java) as IndexJob<T>
        is String -> mapper.readValue(data, IndexJob::class.java) as IndexJob<T>
        else -> throw ChatException(
            "A stored index job cannot be read from '${data.javaClass.name}'."
        )
    }
}
```

The file also needs these imports: `ChatException`, `IndexJob`, `Key`,
`KeyValuePair`, `MessageTopic`, the four `com.demo.chat.service.core` stores,
`JobTopicNames`, `VectorIndexJobStore`, `ObjectMapper`, `Flux`, `Mono`,
`Duration`, and `Instant`.

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

    override fun listJobTopics(): Flux<out MessageTopic<T>> =
        topicPersistence.all()
            .filter { topic -> JobTopicNames.matches(topic.data, nodeId, keyType) }

    // Every read and write pair below runs to completion before the next one
    // starts. Two callers that both read, change, and write one job would
    // otherwise lose one of the changes.
    //
    // subscribeOn does not give this. It moves the subscription to another
    // thread and returns. An asynchronous backend yields between its read and
    // its write, so a second operation interleaves there. concatMap waits for
    // each inner publisher to complete, which is the property this needs.
    private val writer = SerialWriter()

    override fun finishJob(job: IndexJob<T>): Mono<Void> =
        writer.submit(
            readJob(job.key)
                .map { stored ->
                    job.copy(
                        invalidationCount = maxOf(job.invalidationCount, stored.invalidationCount),
                        lastInvalidationAt = stored.lastInvalidationAt ?: job.lastInvalidationAt,
                    )
                }
                .defaultIfEmpty(job)
                .flatMap { merged -> write(merged) }
        )

    override fun invalidate(jobKey: Key<T>, at: Instant): Mono<Void> =
        writer.submit(
            readJob(jobKey)
                .flatMap { job ->
                    write(job.copy(invalidationCount = job.invalidationCount + 1, lastInvalidationAt = at))
                }
        )

    /**
     * The configuration registers this as the bean destroy method.
     *
     * `block()` takes no timeout. `close()` is already bounded, and an outer
     * timeout would cancel it, which would dispose the worker while work is
     * still queued.
     */
    fun close() {
        writer.close().block()
    }
}
```

Write `SerialWriter` beside the store:

```kotlin
package com.demo.chat.service.composite.impl

import com.demo.chat.domain.ChatException
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs submitted work one piece at a time, and waits for each piece to
 * complete before it starts the next.
 *
 * Every emission result is handled. An ignored result leaves the caller with a
 * Mono that never completes, which is worse than an error, because nothing
 * upstream can react to it.
 */
class SerialWriter(private val emitTimeout: Duration = Duration.ofSeconds(10)) {
    private class Work(val body: Mono<Void>, val result: Sinks.Empty<Void>)

    private val closed = AtomicBoolean(false)
    private val drained = Sinks.empty<Void>()
    private val queue = Sinks.many().unicast().onBackpressureBuffer<Work>()

    /**
     * Every submitted piece of work that has not finished.
     *
     * Shutdown reads this. A piece that never ran must still terminate its
     * caller, because a caller that waits forever is the failure this class
     * exists to prevent.
     */
    private val pending = ConcurrentLinkedQueue<Work>()

    private val worker: Disposable = queue.asFlux()
        .concatMap { work ->
            work.body
                .doOnSuccess {
                    pending.remove(work)
                    work.result.tryEmitEmpty()
                }
                .onErrorResume { error ->
                    pending.remove(work)
                    work.result.tryEmitError(error)
                    Mono.empty()
                }
        }
        .doFinally { drained.tryEmitEmpty() }
        .subscribe()

    fun submit(body: Mono<Void>): Mono<Void> = Mono.defer {
        if (closed.get()) {
            return@defer Mono.error(ChatException("The job writer is closed and accepts no work."))
        }

        val work = Work(body, Sinks.empty())
        pending.add(work)
        try {
            // Two concurrent submissions produce FAIL_NON_SERIALIZED, and
            // busyLooping retries that case. A closed queue produces
            // FAIL_TERMINATED, which throws, so the caller sees an error
            // signal rather than a Mono that never completes.
            queue.emitNext(work, Sinks.EmitFailureHandler.busyLooping(emitTimeout))
        } catch (error: Throwable) {
            pending.remove(work)
            return@defer Mono.error(error)
        }

        work.result.asMono()
    }

    /**
     * Stops new work, waits up to [timeout] for the queued work to finish, and
     * then disposes the worker.
     *
     * **Every caller terminates.** Work that the timeout cuts short receives an
     * error, because disposing the worker leaves its result sink unfinished
     * otherwise, and that caller would wait forever.
     *
     * The wait happens here rather than in the caller. An outer
     * `block(timeout)` would cancel this Mono, and a cancel would dispose the
     * worker with work still queued.
     */
    fun close(timeout: Duration = Duration.ofSeconds(10)): Mono<Void> = Mono.defer {
        closed.set(true)
        try {
            // The same policy the submit path uses. A submit that is emitting
            // at this moment produces FAIL_NON_SERIALIZED here too, and an
            // ignored result would leave the queue open, so drained would
            // never complete and this Mono would never finish.
            queue.emitComplete(Sinks.EmitFailureHandler.busyLooping(emitTimeout))
        } catch (error: Sinks.EmissionException) {
            // FAIL_TERMINATED means the queue is already complete. A second
            // close is not an error.
            if (error.reason != Sinks.EmitResult.FAIL_TERMINATED) {
                return@defer Mono.error(error)
            }
        }

        drained.asMono()
            .timeout(timeout)
            .onErrorResume(TimeoutException::class.java) { Mono.empty<Void>() }
            .then(Mono.fromRunnable<Void> { abandonPending() })
    }

    private fun abandonPending() {
        worker.dispose()

        while (true) {
            val work = pending.poll() ?: break
            work.result.tryEmitError(
                ChatException("The job writer shut down before this work ran.")
            )
        }
    }
}
```

The block above carries every import this file needs.

- [ ] **Step 5: Run all three suites and confirm they pass**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite clean test \
  -Dtest=IndexJobCodecTests,VectorIndexJobStoreImplTests,SerialWriterTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS, 17 tests. Four codec, seven store, and six writer.

**Use `clean` here.** Surefire runs compiled classes, not sources, so a class
whose source moved between modules keeps running from the old target directory
and inflates the count.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: add the durable vector index job store (CHAT-fpwpfrfj)"
```

---

### Task 6: The composed job record writer

**Files:**
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/JobRecordWriter.kt`
- Create: `chat-core/src/main/kotlin/com/demo/chat/service/vector/JobRecordCodec.kt`
- Create: `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/ComposedJobRecordWriter.kt`
- Test: `chat-core/src/test/kotlin/com/demo/chat/test/vector/JobRecordCodecTests.kt`
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
    // The exact sequence is the boundary. A writer that called the composite
    // send would show four steps, and the vector indexer would be one of them.
    @Test
    fun `the writer calls persistence, then the index, then pub sub`() {
        val writer = writerUnderTest()

        StepVerifier.create(writer.write(record)).verifyComplete()

        Assertions.assertThat(calls).containsExactly("persistence", "index", "pubsub")
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

    // Subscription order is not execution order. A chain that starts every step
    // at once subscribes them in the same sequence, so a synchronous double
    // reports the same call list either way. A slow first step separates them.
    @Test
    fun `a slow first step still completes before the second starts`() {
        val slowCalls = mutableListOf<String>()
        val slowPersistence = FakeMessagePersistence(slowCalls, Duration.ofMillis(60))
        val writer = ComposedJobRecordWriter(
            messagePersistence = slowPersistence,
            messageIndex = FakeMessageIndex(slowCalls),
            pubsub = FakePubSub(slowCalls),
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )

        StepVerifier.create(writer.write(record)).verifyComplete()

        Assertions.assertThat(slowCalls).containsExactly("persistence", "index", "pubsub")
    }

    // The spec says any failed step stops the steps after it. The index case
    // alone would leave the first step unproven.
    @Test
    fun `a failed persistence write stops the index and pub sub`() {
        val failedCalls = mutableListOf<String>()
        val index = FakeMessageIndex(failedCalls)
        val sink = FakePubSub(failedCalls)
        val writer = ComposedJobRecordWriter(
            messagePersistence = FakeMessagePersistence(
                failedCalls,
                failure = IllegalStateException("persistence is down"),
            ),
            messageIndex = index,
            pubsub = sink,
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )

        StepVerifier
            .create(writer.write(record))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error).hasMessage("persistence is down")
            }

        Assertions.assertThat(index.added).isEmpty()
        Assertions.assertThat(sink.sent).isEmpty()
        Assertions.assertThat(failedCalls).containsExactly("persistence")
    }

    @Test
    fun `a failed index write stops pub sub and keeps the persisted record`() {
        val writer = writerUnderTest(failOn = "index")

        StepVerifier.create(writer.write(record)).verifyError(IllegalStateException::class.java)

        Assertions.assertThat(persistence.added).hasSize(1)
        Assertions.assertThat(pubsub.sent).isEmpty()
    }
```

`writerUnderTest(failOn: String? = null)` builds the writer from three doubles in
`VectorTestFakes`. It passes no vector indexer, because the writer takes none.

**That is why the call list is the proof.** A test cannot assert that an
indexer stayed untouched when the writer never holds one, so
`containsExactly("persistence", "index", "pubsub")` carries the boundary: three
steps, in that order, and no fourth.

```kotlin
    private val calls = mutableListOf<String>()
    private val persistence = FakeMessagePersistence(calls)
    private val pubsub = FakePubSub(calls)

    private val record = JobRecord(
        key = Key.funKey(7L),
        jobKey = Key.funKey(500L),
        workerKey = Key.funKey(1000L),
        at = Instant.parse("2026-09-11T12:00:00Z"),
        message = "rebuild started",
    )

    private fun writerUnderTest(failOn: String? = null): JobRecordWriter<Long> =
        ComposedJobRecordWriter(
            messagePersistence = persistence,
            messageIndex = FakeMessageIndex(calls, failOn),
            pubsub = pubsub,
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )
```

The three doubles come from `VectorTestFakes.kt`, which Task 5 creates. Each one
appends its own name to `calls`, and `FakeMessageIndex(calls, "index")` is what
the third test uses to fail the second step.

- [ ] **Step 1b: Write the failing codec tests**

Create `chat-core/src/test/kotlin/com/demo/chat/test/vector/JobRecordCodecTests.kt`.

**`decode` must read what `encode` wrote.** Nothing else in this task calls
`decode`, so without these tests the round trip is never exercised.

```kotlin
package com.demo.chat.test.vector

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JobRecord
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.JobRecordCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * A job record travels as text, so the codec must read back what it wrote.
 */
class JobRecordCodecTests {
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private val codec = JobRecordCodec(mapper)

    private val record = JobRecord(
        key = Key.funKey(7L),
        jobKey = Key.funKey(500L),
        workerKey = Key.funKey(1000L),
        at = Instant.parse("2026-09-12T12:00:00Z"),
        message = "rebuild started",
        indexed = 3L,
    )

    @Test
    fun `a known version completes an encode and decode round trip`() {
        val decoded = codec.decode<Long>(codec.encode(record))

        Assertions.assertThat(decoded.key.id).isEqualTo(7L)
        Assertions.assertThat(decoded.jobKey.id).isEqualTo(500L)
        Assertions.assertThat(decoded.workerKey.id).isEqualTo(1000L)
        Assertions.assertThat(decoded.message).isEqualTo("rebuild started")
        Assertions.assertThat(decoded.at).isEqualTo(record.at)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
    }

    // The version is checked before the payload binds. The payload here could
    // never bind, so a binding error would prove the check ran too late.
    @Test
    fun `an unknown version fails before the payload binds`() {
        val text = """{"version":99,"record":{"nonsense":true}}"""

        Assertions.assertThatThrownBy { codec.decode<Long>(text) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("99")
    }

    @Test
    fun `an envelope with no record fails`() {
        val text = """{"version":1}"""

        Assertions.assertThatThrownBy { codec.decode<Long>(text) }
            .isInstanceOf(ChatException::class.java)
    }
}
```

- [ ] **Step 2: Run both suites and confirm they fail**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=JobRecordCodecTests,ComposedJobRecordWriterTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL. The compiler reports unresolved references to `JobRecordWriter`,
`JobRecordCodec`, and `ComposedJobRecordWriter`. All three are new in this task,
and the codec tests name two of them before the writer test is reached.

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

The interface and the codec both live in `chat-core`:

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.JobRecord
import reactor.core.publisher.Mono

/**
 * Writes one progress record of a rebuild.
 *
 * An implementation must not reach the vector indexer. A job record in the
 * recall corpus would let a later rebuild read its own output back.
 */
fun interface JobRecordWriter<T> {
    fun write(record: JobRecord<T>): Mono<Void>
}
```

```kotlin
package com.demo.chat.service.vector

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JobRecord
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Encodes one job record as a versioned JSON envelope.
 *
 * Every current deployment binds message data to String, so a record travels as
 * text.
 *
 * The version sits beside the record, not inside it. A version written among
 * the record fields reaches the binder as an unknown property, and the mapper
 * the deployments use refuses one. Field order gives no protection from that,
 * because a binder reads a whole object.
 */
class JobRecordCodec(private val mapper: ObjectMapper) {

    fun encode(record: JobRecord<*>): String {
        val envelope = mapper.createObjectNode()
        envelope.put(VERSION_FIELD, VERSION)
        envelope.set<ObjectNode>(RECORD_FIELD, mapper.valueToTree(record))
        return mapper.writeValueAsString(envelope)
    }

    /**
     * Reads the version before it binds anything. An unknown version stops the
     * read there, so a payload this reader cannot understand is never bound.
     */
    fun <T> decode(text: String): JobRecord<T> {
        val envelope = mapper.readTree(text)
        val version = envelope.get(VERSION_FIELD)?.asInt()

        if (version != VERSION) {
            throw ChatException(
                "A job record of version '$version' cannot be read. This reader knows version $VERSION."
            )
        }

        val payload = envelope.get(RECORD_FIELD)
            ?: throw ChatException("A job record envelope carries no '$RECORD_FIELD' field.")

        @Suppress("UNCHECKED_CAST")
        return mapper.treeToValue(payload, JobRecord::class.java) as JobRecord<T>
    }

    companion object {
        const val VERSION_FIELD = "version"
        const val RECORD_FIELD = "record"
        const val VERSION = 1
    }
}
```

The version sits beside the record, not inside it. A version among the record
fields reaches the binder as an unknown property, and the mapper the deployments
use refuses one. **Field order gives no protection**, because a binder reads a
whole object.

- [ ] **Step 4: Run both suites and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=JobRecordCodecTests,ComposedJobRecordWriterTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 8 tests. Three codec and five writer.

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

    // The state decides, and the durable outcome follows that decision. A
    // durable SUCCEEDED that a losing run wrote would be trusted after a
    // restart under the stored policy.
    @Test
    fun `a live failure during the run leaves no covering job in the store`() {
        given(persistence.all()).willReturn(
            Flux.just(message(1L)).delayElements(Duration.ofMillis(50))
        )
        state.adoptCoveringJob(Key.funKey(900L))

        service.start().block()
        state.invalidate("live vector add failed")
        awaitFinished(service)

        val written = jobStore.written.last()
        Assertions.assertThat(written.outcome).isEqualTo(JobOutcome.FAILED)
        Assertions.assertThat(state.coveringJob()).isNull()
    }

    // The job writes records to its own topic while the scan runs, and that
    // topic is not in the listing the run captured, so it joins the set
    // explicitly. Without that, a rebuild indexes its own output.
    @Test
    fun `the scan drops a message addressed to the running job topic`() {
        given(persistence.all()).willReturn(
            Flux.just(message(1L), messageTo(2L, dest = FakeJobStore.FIRST_JOB_ID))
        )

        val status = runAndAwait(service)

        Assertions.assertThat(jobStore.written.first().key.id).isEqualTo(FakeJobStore.FIRST_JOB_ID)
        Assertions.assertThat(indexer.ids).containsExactly(1L)
        Assertions.assertThat(status.lastReport!!.attempted).isEqualTo(1L)
    }

    @Test
    fun `a rebuild writes a start record and a terminal record to the job topic`() {
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        runAndAwait(service)

        val jobTopicId = jobStore.written.first().key.id

        Assertions.assertThat(recordPersistence.added).hasSize(2)
        Assertions.assertThat(recordIndex.added).hasSize(2)
        Assertions.assertThat(recordPubSub.sent).hasSize(2)
        Assertions.assertThat(recordPubSub.sent.map { it.key.dest }).containsOnly(jobTopicId)
        Assertions.assertThat(recordPubSub.sent.map { it.data })
            .anyMatch { text -> text.contains("rebuild started") }
            .anyMatch { text -> text.contains("rebuild succeeded") }
    }

    @Test
    fun `a failed rebuild reports the failure in its terminal record`() {
        indexer.failOn.add(1L)
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        runAndAwait(service)

        Assertions.assertThat(recordPubSub.sent.map { it.data })
            .anyMatch { text -> text.contains("rebuild failed") }
    }

    @Test
    fun `a failed terminal write finishes the run once`() {
        jobStore.failFinish = true
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        service.start().block()
        awaitFinished(service)

        Assertions.assertThat(jobStore.finishCalls).isEqualTo(1)
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

**The class needs a job store double and two helpers.** The tests use
`jobStore.written`, `jobStore.topics`, `jobStore.failListing`,
`jobStore.failFinish`, `jobStore.finishCalls`, `messageTo`, and a job id a test
can address. Add these beside `RecordingIndexer`.

`configureService` also gains three arguments: the job store, a
`ComposedJobRecordWriter` over `FakeMessagePersistence`, `FakeMessageIndex` and
`FakePubSub`, and a key supplier for record ids. **The writer is real, not a
double**, so a record has to reach all three services to be seen.

```kotlin
    private fun messageTo(id: Long, dest: Long): Message<Long, String> =
        Message.create(MessageKey.create(id, 10L, dest), "message $id", true)

    /**
     * Records every durable write. [topics] is what one listing returns, and
     * [failListing] makes that listing fail.
     */
    private class FakeJobStore : VectorIndexJobStore<Long> {
        val written = mutableListOf<IndexJob<Long>>()
        val topics = mutableListOf<MessageTopic<Long>>()
        var failListing = false
        var failFinish = false
        var finishCalls = 0
        private var nextId = FIRST_JOB_ID

        override fun createJob(startedAt: Instant): Mono<IndexJob<Long>> = Mono.fromSupplier {
            val job = IndexJob(
                key = Key.funKey(nextId++),
                nodeId = 7,
                keyType = "long",
                incarnationId = "incarnation-a",
                startedBy = Key.funKey(1000L),
                startedAt = startedAt,
            )
            written.add(job)
            job
        }

        override fun write(job: IndexJob<Long>): Mono<Void> = Mono.fromRunnable { written.add(job) }

        override fun finishJob(job: IndexJob<Long>): Mono<Void> = Mono.defer {
            finishCalls += 1
            if (failFinish) {
                Mono.error(IllegalStateException("the terminal write failed"))
            } else {
                written.add(job)
                Mono.empty()
            }
        }

        override fun readJob(topicKey: Key<Long>): Mono<IndexJob<Long>> =
            Mono.defer { Mono.justOrEmpty(written.lastOrNull { it.key == topicKey }) }

        override fun listJobTopics(): Flux<out MessageTopic<Long>> = Flux.defer {
            if (failListing) {
                Flux.error(IllegalStateException("topic listing failed"))
            } else {
                Flux.fromIterable(topics.toList())
            }
        }

        override fun invalidate(jobKey: Key<Long>, at: Instant): Mono<Void> = Mono.empty()

        companion object {
            /** The first job this store creates. A test can address it. */
            const val FIRST_JOB_ID = 500L
        }
    }
```


**One claim this task cannot prove yet.** Taking the durable outcome from
`result.status.complete` instead of `result.succeeded` passes every test here,
because `complete` still reads the phase and a failed run sets that phase to
`INCOMPLETE` either way. Task 9 moves `complete` to the covering job, and it adds
the test that separates them.

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
   then the `concatMap` with the counters.

**`record` is not an inclusion rule.** The spec forbids it, and the scan drops
its `record == false` branch. `MessagingServiceImpl` sets `record` true on every
message it sends, and the indexer applies its own rule, so a rebuild offers every
message that is not a job record. Nothing skips a message any more, and a rebuild
leaves the `skipped` count at zero.

**The listing carries every node.** `listJobTopics` returns every reserved topic,
not this node's alone. Another node's job records sit in the same message store,
and a narrowed listing would let them into vector recall. The coverage policy in
Task 8 narrows by node itself.
5. On termination, call `state.finish(claim, report, failure, job.key)` **first**.
   That call decides this run's outcome atomically and installs the target. Then
   call `jobStore.finishJob(...)` with the outcome that verdict gives:
   `SUCCEEDED` when `result.succeeded` is true, and `FAILED` otherwise.

**Read `result.succeeded`, never `result.status.complete`.** The status describes
the index, and an earlier job can still cover it. A failed repair therefore leaves
`status.complete` true, and a durable outcome taken from it would store
`SUCCEEDED` for a run that failed.

**The order matters and is the fix for a durable race.** A durable `SUCCEEDED`
written before the generation check can be left behind by a live failure that
arrives between the two steps. The in-process state would refuse to install that
job, so this process reports no coverage, but a restart under `trust=stored`
would read a clean `SUCCEEDED` job and trust it. Deciding first, and merging in
`finishJob`, closes both halves.

Keep the counters after the filter, so `attempted` describes user messages only.

**Two events reach the job topic, through the composed writer.** A run emits
`rebuild started` after it creates its job, and `rebuild succeeded` or
`rebuild failed` beside the terminal write. A failed record write never fails the
run, because a record is a report and losing one must not turn a healthy rebuild
into a failed one.

**Each recovery covers one step.** The listing handler sits on the listing alone
and returns empty, so the scan never runs after it finishes the run. The scan
handler sits on the scan alone, so the terminal write runs once whether the scan
completed or failed. A handler that wrapped both would finish one run twice, and
a nested pair would finish it three times.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageReindexServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, 15 tests.

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
        VectorCoveragePolicyImpl(store, trust, thisIncarnation, nodeId = 7, keyType = "long")

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

    // The listing carries every node. A job of another node must not cover
    // this one, because each node holds its own index.
    @Test
    fun `another node's job does not cover`() {
        store.write(job(1L).copy(nodeId = 8)).block()

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
    private val nodeId: Int,
    private val keyType: String,
) : VectorCoveragePolicy<T> {

    override fun selectCoveringJob(): Mono<IndexJob<T>> =
        jobStore.listJobTopics()
            .flatMap { topic -> jobStore.readJob(topic.key) }
            // The listing carries every reserved topic, from every node,
            // because the scan exclusion needs all of them. Coverage is per
            // node and per key type, so the policy narrows it here.
            .filter { job -> job.nodeId == nodeId && job.keyType == keyType }
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
Expected: PASS, 9 tests.

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

    // The trap the run verdict exists to close, and the first task where it can
    // be seen. An earlier job still covers, so the index reports complete while
    // this run failed. A durable outcome taken from status.complete would store
    // SUCCEEDED for a failed run.
    @Test
    fun `a failed run reports a false verdict while an earlier job still covers`() {
        state.adoptCoveringJob(coveringKey)
        val claim = state.claim()

        val result = state.finish(
            claim,
            VectorRebuildReport(startedAt, finishedAt, 2L, 1L, 0L, 1L),
            null,
            Key.funKey(11L),
        )

        Assertions.assertThat(result.succeeded).isFalse()
        Assertions.assertThat(result.status.complete).isTrue()
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

    // The indexer records the failure and then rethrows. A caller must still
    // see the vector error, because the send chain decides what to do with it.
    @Test
    fun `a live add failure returns the original error to the sender`() {
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer.add(message))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error)
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("vector store is down")
            }
    }

    // A failed durable write must not replace the vector error, and must not
    // stop the send chain from seeing it.
    @Test
    fun `a failed invalidation write keeps the original error`() {
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true
        jobStore.failWrites = true

        StepVerifier
            .create(indexer.add(message))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error).hasMessage("vector store is down")
            }
    }
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
So this task also changes `VectorIndexState<T>.status()`,
`VectorFinishResult` to `VectorFinishResult<T>` holding `VectorIndexStatus<T>`,
and `MessageReindexService<T>.status()` with them. Task 4 wrote those signatures against
the non-generic form. Task 12 and Task 13 use the generic form.
`RequestResponse.kt:80` caps the limit at 50, so the list is bounded by design.

`VectorStoreMessageVectorIndexer.add` gains the state and the job store. On an add
failure it calls `state.invalidate(reason)`, and when that returns a target it
calls `jobStore.invalidate(target, now)`. A failed durable write logs and never
replaces the original error.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-service-composite test -Dtest=MessageRecallServiceImplTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. Eight existing tests plus six new ones.

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

Replace `recall topic returns NDJSON hits` with these two. Keep the existing
class annotations, the `WebTestClient`, and the `anyObject` helper, because the
module has no mockito-kotlin dependency.

```kotlin
    @Test
    fun `recall topic returns one object with the flag`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(
                            MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9),
                            MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.5),
                        ),
                    )
                )
            )

        client
            .post()
            .uri("/message/recall/topic")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.indexComplete").isEqualTo(true)
            .jsonPath("$.hits.length()").isEqualTo(2)
            .jsonPath("$.hits[0].key.id").isEqualTo(10)
    }

    // The empty case is the reason this contract changed. A stream of hits
    // cannot carry a flag when it carries no hit.
    @Test
    fun `an empty recall still carries the flag`() {
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(Mono.just(MessageRecallResult(indexComplete = false, hits = emptyList())))

        client
            .post()
            .uri("/message/recall/global")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"GlobalRecallRequest","query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.indexComplete").isEqualTo(false)
            .jsonPath("$.hits.length()").isEqualTo(0)
    }

    @Test
    fun `recall user returns one object`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(12L, 20L, 30L), 0.7)),
                    )
                )
            )

        client
            .post()
            .uri("/message/recall/user")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"type":"UserRecallRequest","userId":20,"query":"apple"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.hits.length()").isEqualTo(1)
    }
```

Change the RSocket tests in `MessageRecallControllerTests` with these three.

```kotlin
    @Test
    fun `the topic route returns one result`() {
        BDDMockito
            .given(recallService.recallInTopic(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)),
                    )
                )
            )

        StepVerifier
            .create(
                requester
                    .route("message-recall-topic")
                    .data(TopicRecallRequest(30L, "apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result ->
                Assertions.assertThat(result.indexComplete).isTrue()
                Assertions.assertThat(result.hits).hasSize(1)
            }
            .verifyComplete()
    }

    @Test
    fun `the user route returns one result`() {
        BDDMockito
            .given(recallService.recallByUser(anyObject()))
            .willReturn(Mono.just(MessageRecallResult(indexComplete = false, hits = emptyList())))

        StepVerifier
            .create(
                requester
                    .route("message-recall-user")
                    .data(UserRecallRequest(20L, "apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result ->
                Assertions.assertThat(result.indexComplete).isFalse()
                Assertions.assertThat(result.hits).isEmpty()
            }
            .verifyComplete()
    }

    // One response, not a stream. A stream of one would still decode here, so
    // the assertion that matters is the single completion above and the route
    // signature itself.
    @Test
    fun `the global route returns one result`() {
        BDDMockito
            .given(recallService.recallGlobal(anyObject()))
            .willReturn(
                Mono.just(
                    MessageRecallResult(
                        indexComplete = true,
                        hits = listOf(MessageRecallHit(MessageKey.create(11L, 20L, 30L), 0.4)),
                    )
                )
            )

        StepVerifier
            .create(
                requester
                    .route("message-recall-global")
                    .data(GlobalRecallRequest("apple", 10, 0.0))
                    .retrieveMono(MessageRecallResult::class.java)
            )
            .assertNext { result -> Assertions.assertThat(result.hits).hasSize(1) }
            .verifyComplete()
    }
```

Keep the existing `RSocketTestBase` setup and the `anyObject` helper. Build each
request with the constructor the sealed request types already declare.

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
- Create: `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/InMemoryServiceBeans.kt`

**The old issue text is superseded.** It named one configuration file and four
beans. The wiring now also builds the job store, the job record writer, the
coverage policy, and the startup action.

**Interfaces:**
- Consumes: every type from Tasks 4 to 9.
- Produces: beans behind the existing gates. The class keeps
  `@ConditionalOnProperty("app.service.composite")`, and every bean keeps
  `@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])`.

- [ ] **Step 1: Write the failing context tests**

**Build the context the way this file already does.** `chat-service-composite`
does not depend on `chat-persistence-memory` or `chat-index-lucene`, so a test
cannot name `MemoryPersistenceBeans` or `LuceneIndexBeans`. The existing tests in
this file use `AnnotationConfigApplicationContext` and register the few beans the
configuration reads. Keep that, and add fakes for the three provider interfaces,
which all live in `chat-core`.

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.config.service.composite.VectorRecallServiceConfiguration
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.RequestToQueryConverters
import com.demo.chat.service.dummy.DummyKeyValueIndexService
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import reactor.core.publisher.Flux
import java.time.Duration

class VectorRecallServiceConfigurationTests {

    private val allProperties = mapOf(
        "app.service.composite" to "true",
        "app.service.core.vector" to "simple",
        "app.service.core.embedding" to "mock",
        "app.key.type" to "long",
        "app.nodeid" to "1",
    )

    /**
     * Registers every bean the configuration reads, and nothing else.
     *
     * The three provider interfaces come from chat-core, so this module can
     * implement them. InMemoryServiceBeans backs them with the doubles in
     * VectorTestFakes, which Task 5 creates.
     */
    private val beans = InMemoryServiceBeans()

    private fun contextWith(properties: Map<String, String>): AnnotationConfigApplicationContext {
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(MapPropertySource("test", properties))
        context.beanFactory.registerSingleton("typeUtil", LongUtil())
        context.beanFactory.registerSingleton("vectorStore", MockVectorStore())
        context.beanFactory.registerSingleton("persistenceBeans", beans.persistence())
        context.beanFactory.registerSingleton("indexBeans", beans.index())
        context.beanFactory.registerSingleton("pubSubBeans", beans.pubSub())
        context.beanFactory.registerSingleton("queryConverters", beans.converters())
        context.register(VectorRecallServiceConfiguration::class.java)
        context.refresh()
        return context
    }

    private fun publishReady(context: AnnotationConfigApplicationContext) {
        context.publishEvent(
            ApplicationReadyEvent(SpringApplication(), arrayOf(), context, Duration.ZERO)
        )
    }

    private fun jobCount(context: AnnotationConfigApplicationContext): Long =
        context.getBean(VectorIndexJobStore::class.java)
            .listJobTopics()
            .count()
            .block(Duration.ofSeconds(10))!!

    private fun awaitNotRunning(context: AnnotationConfigApplicationContext) {
        val reindex = context.getBean(MessageReindexService::class.java)
        Flux.interval(Duration.ZERO, Duration.ofMillis(20))
            .map { reindex.status() }
            .filter { status -> !status.running }
            .next()
            .block(Duration.ofSeconds(30))
    }

    @Test
    fun `every vector bean exists with the composite and both selectors`() {
        val context = contextWith(allProperties)

        try {
            Assertions.assertThat(context.getBean(VectorIndexState::class.java)).isNotNull
            Assertions.assertThat(context.getBean(VectorIndexJobStore::class.java)).isNotNull
            Assertions.assertThat(context.getBean(VectorCoveragePolicy::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageVectorIndexer::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageRecallService::class.java)).isNotNull
            Assertions.assertThat(context.getBean(MessageReindexService::class.java)).isNotNull
        } finally {
            context.close()
        }
    }

    @Test
    fun `no vector bean exists without the composite selector`() {
        val context = contextWith(allProperties - "app.service.composite")

        try {
            Assertions.assertThat(context.getBeanNamesForType(VectorIndexState::class.java)).isEmpty()
            Assertions.assertThat(context.getBeanNamesForType(MessageReindexService::class.java)).isEmpty()
        } finally {
            context.close()
        }
    }

    @Test
    fun `no vector bean exists when one recall selector is absent`() {
        val context = contextWith(allProperties - "app.service.core.embedding")

        try {
            Assertions.assertThat(context.getBeanNamesForType(MessageRecallService::class.java)).isEmpty()
            Assertions.assertThat(context.getBeanNamesForType(VectorIndexJobStore::class.java)).isEmpty()
        } finally {
            context.close()
        }
    }

    // The default must start no rebuild. Real embedding throughput is still
    // unmeasured, so an automatic rebuild could delay readiness or send
    // uncontrolled external requests.
    @Test
    fun `the default startup action starts no job`() {
        val context = contextWith(allProperties)

        try {
            publishReady(context)

            Assertions.assertThat(jobCount(context)).isEqualTo(0L)
        } finally {
            context.close()
        }
    }

    // AnnotationConfigApplicationContext refreshes. It never publishes
    // ApplicationReadyEvent, so a listener bound to that event fires only when
    // the test publishes it.
    @Test
    fun `the rebuild startup action starts exactly one job`() {
        val context = contextWith(allProperties + ("app.vector.index.startup" to "rebuild"))

        try {
            publishReady(context)
            awaitNotRunning(context)

            Assertions.assertThat(jobCount(context)).isEqualTo(1L)
        } finally {
            context.close()
        }
    }

    @Test
    fun `an unknown trust value fails the context`() {
        Assertions
            .assertThatThrownBy {
                contextWith(allProperties + ("app.vector.index.trust" to "maybe")).close()
            }
            .hasMessageContaining("app.vector.index.trust")
    }
}
```

Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/InMemoryServiceBeans.kt`:

```kotlin
package com.demo.chat.test.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.service.core.*
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyKeyValueIndexService
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.test.service.composite.FakeKeyValueStore
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import com.demo.chat.test.service.composite.FakePubSub
import com.demo.chat.test.service.composite.FakeTopicIndex
import com.demo.chat.test.service.composite.FakeTopicPersistence

/**
 * The provider beans the vector configuration reads, backed by the doubles in
 * VectorTestFakes.
 *
 * The configuration calls six provider methods. Every other member returns an
 * existing dummy, because nothing under test reaches it.
 */
internal class InMemoryServiceBeans {
    val topics = FakeTopicPersistence()
    val topicIndex = FakeTopicIndex()
    val pubsub = FakePubSub()
    val messages = FakeMessagePersistence()
    val messageIndex = FakeMessageIndex()
    val keyValues = FakeKeyValueStore()

    fun persistence(): PersistenceServiceBeans<Long, String> =
        object : PersistenceServiceBeans<Long, String> {
            override fun userPersistence(): UserPersistence<Long> =
                object : DummyPersistenceStore<Long, User<Long>>(), UserPersistence<Long> {}

            override fun topicPersistence(): TopicPersistence<Long> = topics

            override fun messagePersistence(): MessagePersistence<Long, String> = messages

            override fun membershipPersistence(): MembershipPersistence<Long> =
                object : DummyPersistenceStore<Long, TopicMembership<Long>>(), MembershipPersistence<Long> {}

            override fun authMetaPersistence(): AuthMetaPersistence<Long> =
                object : DummyPersistenceStore<Long, AuthMetadata<Long>>(), AuthMetaPersistence<Long> {}

            override fun keyValuePersistence(): KeyValueStore<Long, Any> = keyValues
        }

    fun index(): IndexServiceBeans<Long, String, Map<String, String>> =
        object : IndexServiceBeans<Long, String, Map<String, String>> {
            override fun userIndex(): UserIndexService<Long, Map<String, String>> =
                object : DummyIndexService<Long, User<Long>, Map<String, String>>(),
                    UserIndexService<Long, Map<String, String>> {}

            override fun messageIndex(): MessageIndexService<Long, String, Map<String, String>> = messageIndex

            override fun topicIndex(): TopicIndexService<Long, Map<String, String>> = topicIndex

            override fun membershipIndex(): MembershipIndexService<Long, Map<String, String>> =
                object : DummyIndexService<Long, TopicMembership<Long>, Map<String, String>>(),
                    MembershipIndexService<Long, Map<String, String>> {
                    override fun size(query: Map<String, String>) = reactor.core.publisher.Mono.just(0L)
                }

            override fun authMetadataIndex(): AuthMetaIndex<Long, Map<String, String>> =
                object : DummyIndexService<Long, AuthMetadata<Long>, Map<String, String>>(),
                    AuthMetaIndex<Long, Map<String, String>> {}

            override fun KVPairIndex(): KeyValueIndexService<Long, Map<String, String>> =
                DummyKeyValueIndexService()
        }

    fun pubSub(): PubSubServiceBeans<Long, String> =
        object : PubSubServiceBeans<Long, String> {
            override fun pubSubService(): TopicPubSubService<Long, String> = pubsub
        }

    fun converters(): RequestToQueryConverters<Map<String, String>> =
        object : RequestToQueryConverters<Map<String, String>> {
            override fun topicNameToQuery(req: ByStringRequest) = mapOf("name" to req.name)
            override fun <T> topicIdToQuery(req: ByIdRequest<T>) = mapOf("topic" to req.id.toString())
            override fun userHandleToQuery(req: ByStringRequest) = mapOf("handle" to req.name)
            override fun <T> authPrincipalToQuery(req: ByIdRequest<T>) = mapOf("principal" to req.id.toString())
            override fun <T> authTargetToQuery(req: ByIdRequest<T>) = mapOf("target" to req.id.toString())
            override fun <T> membershipIdToQuery(req: ByIdRequest<T>) = mapOf("member" to req.id.toString())
            override fun <T> membershipRequestToQuery(req: MembershipRequest<T>) =
                mapOf("member" to req.uid.toString())
        }
}
```

The test holds one instance and registers its four provider objects:

```kotlin
    private val beans = InMemoryServiceBeans()
```

Then `contextWith` registers `beans.persistence()`, `beans.index()`,
`beans.pubSub()`, and `beans.converters()`. `MembershipIndexService` declares an
extra `size` member, which is why that one dummy overrides a method.

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
import org.springframework.test.annotation.DirtiesContext
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
// Each test needs an empty index, an empty store, and no prior job. The beans
// hold that state in memory, so a shared context would let test order decide
// the initial conditions.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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

`messagesOfTopic` reads a job topic the same way `MessagingServiceImpl.listenTopic`
reads a room. Add it to the class, with these two injected beans:

```kotlin
    @Autowired
    lateinit var messageIndex: MessageIndexService<Long, String, IndexSearchRequest>

    @Autowired
    lateinit var queryConverters: RequestToQueryConverters<IndexSearchRequest>

    private fun messagesOfTopic(topicId: Long): List<Message<Long, String>> =
        messageIndex
            .findBy(queryConverters.topicIdToQuery(ByIdRequest(topicId)))
            .collectList()
            .flatMapMany { keys -> persistence.byIds(keys) }
            .collectList()
            .block(Duration.ofSeconds(10))!!
            .map { message -> message as Message<Long, String> }
```

The memory composition binds `Q` to `IndexSearchRequest`. A cassandra composition
binds it to `Map<String, String>`, so this test stays on the memory deployment.

**The third test is the load-bearing one.** `attempted` stays at one after two
rebuilds. If the exclusion filter is missing, the second rebuild counts the job
records of the first.

- [ ] **Step 2: Run the test**

Run: `JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-core,chat-deploy-memory test -Dtest=VectorIndexRecoveryTests -Dsurefire.failIfNoSpecifiedTests=false`

**This task has no red step, and that is deliberate.** Tasks 1 to 12 already
built every part. This test only proves that the assembled parts recover a lost
index, so a passing first run is the expected result.

A failure here names a real gap in an earlier task. Fix it in that task, and
never with a special case in this test.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test: prove recall recovery after index loss (CHAT-neucngmw)"
```

---

### Task 14: Prove the job decode on each backend

**Files:**
- Create: `chat-persistence-redis/src/test/kotlin/com/demo/chat/test/persistence/redis/RedisIndexJobDecodeTests.kt`
- Create: `chat-persistence-cassandra/src/test/kotlin/com/demo/chat/test/persistence/integration/CassandraIndexJobDecodeTests.kt`
- Create: `chat-client-rsocket/src/test/kotlin/com/demo/chat/test/rsocket/controller/core/KeyValueJobDecodeRequesterTests.kt`

**No POM changes.** `IndexJobCodec` lives in `chat-core`, which all three modules
already depend on. Putting it beside the store in `chat-service-composite` would
have forced a test-scoped dependency into both persistence modules, and neither
declares one today.

**Why this task exists.** The spec requires that a known topic key supports an
`IndexJob` decode on each backend, and that the same decode works through the
RSocket key-value client. `IndexJobCodecTests` proves the branches against a
mapper. It does not prove that each backend hands back the shape that branch
expects.

**The three shapes, read from the store implementations:**

- Memory returns the stored object. `InMemoryKeyValueStore` holds it in a map.
- Redis returns a `LinkedHashMap`. `KeyValuePersistenceRedis.get` reads the JSON
  into a `KeyValuePair`, and `data` arrives as `Any`.
- Cassandra returns the JSON `String`. `KeyValuePersistenceCassandra.get` builds
  the pair from `kv.data`, which the table stores as text.

- [ ] **Step 1: Write the redis test**

```kotlin
package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import com.demo.chat.service.vector.IndexJobCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.util.UUID

/**
 * The redis shape. A JSON round trip returns the value as a map, so the codec
 * takes its map branch.
 */
@Extensions(ExtendWith(SpringExtension::class))
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisIndexJobDecodeTests(
    @Autowired private val keyValuePersistence: KeyValuePersistenceRedis<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
    // The deployed mapper, not a private one. A codec that only ever meets a
    // mapper the test built proves nothing about the mapper the deployment uses.
    @Autowired private val mapper: ObjectMapper,
) {
    private val codec = IndexJobCodec<UUID>(mapper)

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    @Test
    fun `a stored job reads back through the codec`() {
        val key = Key.funKey(UUID.randomUUID())
        val job = IndexJob(
            key = key,
            nodeId = 7,
            keyType = "uuid",
            incarnationId = "incarnation-a",
            startedBy = Key.funKey(UUID.randomUUID()),
            startedAt = Instant.parse("2026-09-12T12:00:00Z"),
            outcome = JobOutcome.SUCCEEDED,
            indexed = 3L,
        )

        keyValuePersistence.add(KeyValuePair.create(key, job as Any)).block()
        val stored = keyValuePersistence.get(key).block()!!

        Assertions.assertThat(stored.data).isInstanceOf(Map::class.java)

        val decoded = codec.decode(stored.data)

        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(decoded.indexed).isEqualTo(3L)
        Assertions.assertThat(decoded.nodeId).isEqualTo(7)
        Assertions.assertThat(decoded.startedAt).isEqualTo(job.startedAt)
        Assertions.assertThat(decoded.covers).isTrue()
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}
```

The container properties and the flush both come from
`RedisKeyValueTypedDomainTests`, which carries the same companion object and the
same cleanup. Without the property source the context has no `spring.redis.host`
and never reaches a container.

- [ ] **Step 2: Write the cassandra test**

`TypedKeyValueStoreTests` exposes its store as `store` and keeps its id counter
private, so this class builds its own store from the injected repository and
makes its own ids.

```kotlin
package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The cassandra shape. The kv_pair table stores text, so the codec takes its
 * string branch.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class CassandraIndexJobDecodeTests {

    @Autowired
    lateinit var repo: KeyValuePairRepository<Long>

    // The deployed mapper. TestObjectMapperConfiguration supplies it, and
    // RepositoryTestConfiguration imports that class.
    @Autowired
    lateinit var mapper: ObjectMapper

    private val ids = AtomicLong(9_000L)

    private fun codec() = IndexJobCodec<Long>(mapper)

    private fun store() = KeyValuePersistenceCassandra(TestLongKeyService(), repo, mapper)

    @Test
    fun `a stored job reads back through the codec`() {
        val store = store()
        val key = Key.funKey(ids.incrementAndGet())
        val job = IndexJob(
            key = key,
            nodeId = 7,
            keyType = "long",
            incarnationId = "incarnation-a",
            startedBy = Key.funKey(ids.incrementAndGet()),
            startedAt = Instant.parse("2026-09-12T12:00:00Z"),
            outcome = JobOutcome.FAILED,
            failed = 2L,
        )

        store.add(KeyValuePair.create(key, job as Any)).block()
        val stored = store.get(key).block()!!

        Assertions.assertThat(stored.data).isInstanceOf(String::class.java)

        val decoded = codec().decode(stored.data)

        Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.FAILED)
        Assertions.assertThat(decoded.failed).isEqualTo(2L)
        Assertions.assertThat(decoded.covers).isFalse()
    }
}
```

`RepositoryTestConfiguration` extends `CassandraTestContainerConfiguration`, so
the container and the keyspace come with it. This class adds nothing for them.

- [ ] **Step 3: Write the RSocket test**

`KeyValueIndexRequesterTests` installs an index controller and mocks a
`KeyValueIndexService`, which is the wrong pair here. This test follows
`UserPersistenceRequesterTests` instead: a controller that extends
`PersistenceServiceController`, a mocked store, and the routes with no prefix.

```kotlin
package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.client.rsocket.clients.core.KeyValueStoreClient
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * The client reads with get and decodes locally. It never calls typedGet,
 * whose route carries only the key, so the server receives no class and cannot
 * bind the value.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(KeyValueJobDecodeRequesterTests.KeyValueStoreTestConfiguration::class)
class KeyValueJobDecodeRequesterTests : RSocketTestBase() {

    @MockBean
    private lateinit var keyValueStore: KeyValueStore<Long, Any>

    private val svcPrefix = ""

    // The deployed mapper. RSocketServerTestConfiguration enables auto
    // configuration and imports TestModules, so this one carries the chat
    // Jackson modules exactly as a deployment does.
    @Autowired
    private lateinit var mapper: ObjectMapper

    private val key = Key.funKey(1000L)

    private val job = IndexJob(
        key = key,
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(2000L),
        startedAt = Instant.parse("2026-09-12T12:00:00Z"),
        outcome = JobOutcome.SUCCEEDED,
    )

    @Test
    fun `a job decodes through the key value client`() {
        BDDMockito
            .given(keyValueStore.get(anyObject()))
            .willReturn(Mono.just(KeyValuePair.create(key, job as Any)))

        val client = KeyValueStoreClient<Long>(svcPrefix, requester)

        StepVerifier
            .create(client.get(key))
            .assertNext { pair ->
                val decoded = IndexJobCodec<Long>(mapper).decode(pair.data)

                Assertions.assertThat(decoded.outcome).isEqualTo(JobOutcome.SUCCEEDED)
                Assertions.assertThat(decoded.nodeId).isEqualTo(7)
                Assertions.assertThat(decoded.incarnationId).isEqualTo("incarnation-a")
            }
            .verifyComplete()
    }

    // @TestConfiguration is required. A plain imported class does not have its
    // nested controller discovered, so the routes would never register.
    @TestConfiguration
    class KeyValueStoreTestConfiguration {
        @Controller
        class TestKeyValueController<T>(
            store: KeyValueStore<T, Any>,
        ) : PersistenceServiceController<T, KeyValuePair<T, Any>>(store)
    }
}
```

- [ ] **Step 4: Run the three suites**

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -B -DskipTests install -q
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-persistence-redis -Pintegration test \
  -Dtest=RedisIndexJobDecodeTests -Dsurefire.failIfNoSpecifiedTests=false
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-persistence-cassandra -Pintegration test \
  -Dtest=CassandraIndexJobDecodeTests -Dsurefire.failIfNoSpecifiedTests=false
JAVA_HOME=~/.sdkman/candidates/java/25.0.4-tem mvn -o -pl chat-client-rsocket -am test \
  -Dtest=KeyValueJobDecodeRequesterTests -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: PASS, one test in each suite.

The redis and cassandra suites start containers. `CHAT-sgyaaivp` records that the
cassandra integration job alternates red on unchanged code, so confirm a failure
twice before treating it as real.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "test: prove the job decode on each backend (CHAT-fpwpfrfj)"
```

---

### Task 15: Documentation and the full gate (`CHAT-jqvclfbd`)

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
