# Message Vector Reindex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans`. The repository forbids subagent-driven development.

**Goal:** Add a protected operator action that rebuilds message vectors and reports index completeness with every recall result.

**Architecture:** One in-process state object owns completeness, job exclusion, and invalidation generations. A background reindex service streams persistence through the existing vector indexer. REST, RSocket, and actuator adapters expose the new contracts.

**Tech Stack:** Kotlin 2.4.10, Java 25, Reactor, Spring AI 1.0.3, Spring Boot Actuator, JUnit 5, Mockito, AssertJ

**Issue:** `CHAT-oghjsnad`

**Design:** `docs/superpowers/specs/2026-09-10-vector-reindex-design.md`

---

## Execution Rules

Execute this plan inline.

Do not use subagents.

Use TDD for every behavior change.

Run each focused test before its implementation.

Commit each FP child issue separately.

Prefix every FP command with `FP_AGENT_NAME='sigma'`.

Use `drift refs` before each documentation edit.

Preserve the current unrelated policy-file changes.

The full diff check currently reports `.gitignore:57: new blank line at EOF`.

Do not change that file under this issue.

## Operational Constraints

The reindex service obtains persistence from `PersistenceServiceBeans<T, V>`.

The composite context exposes no `MessagePersistence<T, V>` bean by type.

The reindex service owns the one unchecked cast to `Message<T, String>`.

Every current composition binds `V` to `String`.

A split RSocket deployment streams the full corpus through route `persistence.all`.

The plan adds no batching, pagination, or remote scan limit.

## File Map

### New production files

- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallResult.kt`
- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt`
- `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/InMemoryVectorIndexState.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`
- `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt`

### New test files

- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/InMemoryVectorIndexStateTests.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt`
- `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt`
- `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/EmbeddedVectorReindexRecoveryTests.kt`

### Modified files

- `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`
- `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt`
- `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`
- `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`
- `chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt`
- `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`
- `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt`
- `docs/superpowers/specs/2026-09-01-message-vector-recall-design.md`
- `forward-register.md`

## Task 1: Add Vector Index State Contracts

**FP issue:** `CHAT-bvmevhpq`

**Files:**

- Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt`.
- Create `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/InMemoryVectorIndexState.kt`.
- Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/InMemoryVectorIndexStateTests.kt`.

### Step 1: Write the state transition tests

Add tests for initial state, exclusive claims, successful completion, failed completion, and concurrent invalidation.

Use fixed instants and this test shape:

```kotlin
class InMemoryVectorIndexStateTests {
    private val startedAt = Instant.parse("2026-09-10T12:00:00Z")
    private val finishedAt = Instant.parse("2026-09-10T12:00:01Z")

    @Test
    fun `fresh state is incomplete and idle`() {
        val status = InMemoryVectorIndexState().status()

        Assertions.assertThat(status.phase).isEqualTo(VectorIndexPhase.INCOMPLETE)
        Assertions.assertThat(status.complete).isFalse()
        Assertions.assertThat(status.running).isFalse()
        Assertions.assertThat(status.lastReport).isNull()
    }

    @Test
    fun `only one rebuild claim is accepted`() {
        val state = InMemoryVectorIndexState()

        val first = state.claim()
        val second = state.claim()

        Assertions.assertThat(first.accepted).isTrue()
        Assertions.assertThat(first.status.running).isTrue()
        Assertions.assertThat(second.accepted).isFalse()
        Assertions.assertThat(second.generation).isEqualTo(first.generation)
    }

    @Test
    fun `zero failures and unchanged generation mark complete`() {
        val state = InMemoryVectorIndexState()
        val claim = state.claim()
        val report = VectorRebuildReport(startedAt, finishedAt, 2L, 2L, 0L, 0L)

        val status = state.finish(claim, report, null)

        Assertions.assertThat(status.complete).isTrue()
        Assertions.assertThat(status.lastSuccessAt).isEqualTo(finishedAt)
        Assertions.assertThat(status.lastSuccessCount).isEqualTo(2)
    }

    @Test
    fun `live invalidation prevents a false complete state`() {
        val state = InMemoryVectorIndexState()
        val claim = state.claim()
        state.invalidate("live vector add failed")
        val report = VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L)

        val status = state.finish(claim, report, null)

        Assertions.assertThat(status.phase).isEqualTo(VectorIndexPhase.INCOMPLETE)
        Assertions.assertThat(status.lastFailure).isEqualTo("live vector add failed")
    }

    @Test
    fun `failed rebuild preserves the last successful values`() {
        val state = InMemoryVectorIndexState()
        val first = state.claim()
        state.finish(
            first,
            VectorRebuildReport(startedAt, finishedAt, 2L, 2L, 0L, 0L),
            null,
        )
        val second = state.claim()
        val failedAt = finishedAt.plusSeconds(1)

        val status = state.finish(
            second,
            VectorRebuildReport(finishedAt, failedAt, 2L, 1L, 0L, 1L),
            "IllegalStateException: vector down",
        )

        Assertions.assertThat(status.complete).isFalse()
        Assertions.assertThat(status.lastReport!!.failed).isEqualTo(1L)
        Assertions.assertThat(status.lastSuccessAt).isEqualTo(finishedAt)
        Assertions.assertThat(status.lastSuccessCount).isEqualTo(2L)
    }
}
```

### Step 2: Run the tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-service-composite \
  -Dtest=InMemoryVectorIndexStateTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the state types do not exist.

### Step 3: Add the state contracts

Create `VectorIndexState.kt` with these exact public shapes:

```kotlin
package com.demo.chat.service.vector

import java.time.Instant

enum class VectorIndexPhase {
    INCOMPLETE,
    REBUILDING,
    COMPLETE,
}

data class VectorRebuildReport(
    val startedAt: Instant,
    val finishedAt: Instant,
    val attempted: Long,
    val indexed: Long,
    val skipped: Long,
    val failed: Long,
)

data class VectorIndexStatus(
    val phase: VectorIndexPhase,
    val lastReport: VectorRebuildReport? = null,
    val lastSuccessAt: Instant? = null,
    val lastSuccessCount: Long? = null,
    val lastFailure: String? = null,
) {
    val complete: Boolean
        get() = phase == VectorIndexPhase.COMPLETE

    val running: Boolean
        get() = phase == VectorIndexPhase.REBUILDING
}

data class VectorIndexClaim(
    val accepted: Boolean,
    val generation: Long,
    val status: VectorIndexStatus,
)

interface VectorIndexState {
    fun status(): VectorIndexStatus
    fun claim(): VectorIndexClaim
    fun invalidate(reason: String)
    fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
    ): VectorIndexStatus
}
```

### Step 4: Add the atomic state implementation

Use one `AtomicReference` for the public status and invalidation generation.

Keep `REBUILDING` during a live invalidation.

This rule prevents a second job from claiming the active slot.

Use this implementation structure:

```kotlin
class InMemoryVectorIndexState : VectorIndexState {
    private data class Snapshot(
        val generation: Long,
        val status: VectorIndexStatus,
    )

    private val snapshot = AtomicReference(
        Snapshot(0, VectorIndexStatus(VectorIndexPhase.INCOMPLETE))
    )

    override fun status(): VectorIndexStatus = snapshot.get().status

    override fun claim(): VectorIndexClaim {
        while (true) {
            val current = snapshot.get()
            if (current.status.running) {
                return VectorIndexClaim(false, current.generation, current.status)
            }
            val running = current.copy(
                status = current.status.copy(phase = VectorIndexPhase.REBUILDING)
            )
            if (snapshot.compareAndSet(current, running)) {
                return VectorIndexClaim(true, running.generation, running.status)
            }
        }
    }

    override fun invalidate(reason: String) {
        snapshot.updateAndGet { current ->
            val phase = if (current.status.running) {
                VectorIndexPhase.REBUILDING
            } else {
                VectorIndexPhase.INCOMPLETE
            }
            current.copy(
                generation = current.generation + 1,
                status = current.status.copy(phase = phase, lastFailure = reason),
            )
        }
    }

    override fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
    ): VectorIndexStatus {
        val result = snapshot.updateAndGet { current ->
            val unchanged = current.generation == claim.generation
            val succeeded = failure == null && report.failed == 0L && unchanged
            val status = if (succeeded) {
                current.status.copy(
                    phase = VectorIndexPhase.COMPLETE,
                    lastReport = report,
                    lastSuccessAt = report.finishedAt,
                    lastSuccessCount = report.indexed,
                    lastFailure = null,
                )
            } else {
                val reason = failure ?: current.status.lastFailure ?: if (!unchanged) {
                    "Index changed during rebuild"
                } else {
                    "Rebuild reported ${report.failed} failed messages"
                }
                current.status.copy(
                    phase = VectorIndexPhase.INCOMPLETE,
                    lastReport = report,
                    lastFailure = reason,
                )
            }
            current.copy(status = status)
        }
        return result.status
    }
}
```

### Step 5: Run focused state tests

Run the command from Step 2.

Expected: all `InMemoryVectorIndexStateTests` pass.

### Step 6: Commit Task 1

Run:

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/VectorIndexState.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/InMemoryVectorIndexState.kt \
  chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/InMemoryVectorIndexStateTests.kt
git commit -m 'feat: add vector index state (CHAT-bvmevhpq)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-bvmevhpq \
  --comment 'Added atomic vector index state. Focused state tests pass.'
```

## Task 2: Implement the Full Message Reindex Service

**FP issue:** `CHAT-mwvhqoyt`

**Depends on:** `CHAT-bvmevhpq`

**Files:**

- Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt`.
- Create `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt`.
- Create `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt`.

### Step 1: Write reindex behavior tests

Use one mocked `MessagePersistence<Long, String>` and one test indexer.

The test indexer must record identifiers and allow selected failures.

Add these cases:

```kotlin
@Test
fun `rebuild indexes recorded messages and skips alerts`() {
    given(persistence.all()).willReturn(
        Flux.just(
            message(1, "apple", true),
            message(2, "joined", false),
            message(3, "banana", true),
        )
    )

    val final = runAndAwait(service)

    Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
    Assertions.assertThat(final.complete).isTrue()
    Assertions.assertThat(final.lastReport)
        .isEqualTo(VectorRebuildReport(startedAt, finishedAt, 3, 2, 1, 0))
}

@Test
fun `one message failure does not stop later messages`() {
    indexer.failOn.add(2L)
    given(persistence.all()).willReturn(
        Flux.just(message(1), message(2), message(3))
    )

    val final = runAndAwait(service)

    Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
    Assertions.assertThat(final.complete).isFalse()
    Assertions.assertThat(final.lastReport!!.failed).isEqualTo(1)
}

@Test
fun `persistence scan failure marks incomplete`() {
    given(persistence.all()).willReturn(
        Flux.concat(Flux.just(message(1)), Flux.error(Exception("scan failed")))
    )

    val final = runAndAwait(service)

    Assertions.assertThat(final.complete).isFalse()
    Assertions.assertThat(final.lastFailure).contains("scan failed")
}

@Test
fun `second start returns busy and starts no second scan`() {
    val release = Sinks.one<Void>()
    given(persistence.all()).willReturn(
        Flux.just(message(1)).concatWith(release.asMono().thenMany(Flux.empty()))
    )

    val first = service.start().block()!!
    val second = service.start().block()!!

    Assertions.assertThat(first.running).isTrue()
    Assertions.assertThat(second.running).isTrue()
    verify(persistence, timeout(1_000).times(1)).all()
    release.tryEmitEmpty()
}
```

Mock `Clock.instant()` with this exact sequence:

```kotlin
private val clock = mock<Clock>()

@BeforeEach
fun configureClock() {
    given(clock.instant()).willReturn(startedAt, finishedAt)
}
```

Use these exact job helpers:

```kotlin
private val scheduler = Schedulers.newSingle("reindex-test")

private val service = MessageReindexServiceImpl(
    persistence,
    indexer,
    state,
    clock,
    scheduler,
)

private fun runAndAwait(service: MessageReindexService<Long>): VectorIndexStatus {
    Assertions.assertThat(service.start().block()!!.running).isTrue()
    return Flux.interval(Duration.ZERO, Duration.ofMillis(10))
        .map { service.status() }
        .filter { !it.running }
        .next()
        .block(Duration.ofSeconds(10))!!
}

@AfterEach
fun closeScheduler() {
    scheduler.dispose()
}
```

Use a dedicated single scheduler and close it after each test.

### Step 2: Run the focused tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-service-composite \
  -Dtest=MessageReindexServiceImplTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the reindex service does not exist.

### Step 3: Add the reindex service contract

Create this interface:

```kotlin
package com.demo.chat.service.vector

import reactor.core.publisher.Mono

interface MessageReindexService<T> {
    fun start(): Mono<VectorIndexStatus>
    fun status(): VectorIndexStatus
}
```

### Step 4: Implement asynchronous rebuild execution

Use `MessagePersistence<T, V>` as the read boundary.

Use `Schedulers.boundedElastic()` as the default job scheduler.

Permit a dedicated scheduler in tests.

Implement these core methods:

```kotlin
class MessageReindexServiceImpl<T, V>(
    private val persistence: MessagePersistence<T, V>,
    private val indexer: MessageVectorIndexer<T>,
    private val state: VectorIndexState,
    private val clock: Clock = Clock.systemUTC(),
    private val scheduler: Scheduler = Schedulers.boundedElastic(),
) : MessageReindexService<T> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun status(): VectorIndexStatus = state.status()

    override fun start(): Mono<VectorIndexStatus> = Mono.fromSupplier {
        val claim = state.claim()
        if (claim.accepted) {
            rebuild(claim, clock.instant())
                .subscribeOn(scheduler)
                .subscribe(
                    {},
                    { error -> logger.error("Vector reindex terminated unexpectedly", error) },
                )
        }
        claim.status
    }

    private fun rebuild(claim: VectorIndexClaim, startedAt: Instant): Mono<Void> {
        val attempted = AtomicLong()
        val indexed = AtomicLong()
        val skipped = AtomicLong()
        val failed = AtomicLong()
        val lastFailure = AtomicReference<String?>()

        val scan = Flux.defer { persistence.all() }.concatMap { message ->
            attempted.incrementAndGet()
            if (!message.record) {
                skipped.incrementAndGet()
                Mono.empty()
            } else {
                Mono.defer { indexer.add(asText(message)) }
                    .doOnSuccess { indexed.incrementAndGet() }
                    .onErrorResume { error ->
                        failed.incrementAndGet()
                        lastFailure.set(summary(error))
                        logger.error("Vector reindex message failed", error)
                        Mono.empty()
                    }
            }
        }

        return scan
            .then(finish(claim, startedAt, attempted, indexed, skipped, failed, lastFailure))
            .onErrorResume { error ->
                lastFailure.set(summary(error))
                logger.error("Vector reindex scan failed", error)
                finish(claim, startedAt, attempted, indexed, skipped, failed, lastFailure)
            }
    }

    private fun finish(
        claim: VectorIndexClaim,
        startedAt: Instant,
        attempted: AtomicLong,
        indexed: AtomicLong,
        skipped: AtomicLong,
        failed: AtomicLong,
        lastFailure: AtomicReference<String?>,
    ): Mono<Void> = Mono.fromRunnable {
        val report = VectorRebuildReport(
            startedAt,
            clock.instant(),
            attempted.get(),
            indexed.get(),
            skipped.get(),
            failed.get(),
        )
        state.finish(claim, report, lastFailure.get())
        logger.info(
            "Vector reindex finished. attempted={}, indexed={}, skipped={}, failed={}",
            report.attempted,
            report.indexed,
            report.skipped,
            report.failed,
        )
    }.then()

    @Suppress("UNCHECKED_CAST")
    private fun asText(message: Message<T, V>): Message<T, String> =
        message as Message<T, String>

    private fun summary(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message ?: "No message"}"
}
```

Check the persistence failure path carefully.

It must not increment the message failure count.

It must still store the finished report and failure summary.

### Step 5: Run focused reindex tests

Run the command from Step 2.

Expected: every reindex service test passes.

### Step 6: Commit Task 2

Run:

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageReindexService.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageReindexServiceImpl.kt \
  chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageReindexServiceImplTests.kt
git commit -m 'feat: add message vector reindex service (CHAT-mwvhqoyt)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-mwvhqoyt \
  --comment 'Added full message reindex service. Focused rebuild tests pass.'
```

## Task 3: Add the Recall Completeness Contract

**FP issue:** `CHAT-twocpczd`

**Depends on:** `CHAT-bvmevhpq`

**Files:**

- Create `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallResult.kt`.
- Modify `chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt`.
- Modify `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt`.
- Modify `chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt`.
- Modify `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt`.
- Modify `chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt`.

### Step 1: Add failing recall result tests

Update the test service constructor to receive one `InMemoryVectorIndexState`.

Change the `hits` helper to unwrap the result:

```kotlin
private val state = InMemoryVectorIndexState()
private val service = MessageRecallServiceImpl<Long>(store, LongUtil(), "long", state)

private fun result(source: Mono<MessageRecallResult<Long>>): MessageRecallResult<Long> =
    source.block()!!
```

Add these assertions:

```kotlin
@Test
fun `empty recall reports incomplete before rebuild`() {
    val emptyService = MessageRecallServiceImpl<Long>(
        MockVectorStore(),
        LongUtil(),
        "long",
        state,
    )

    val found = result(emptyService.recallGlobal(GlobalRecallRequest("not present")))

    Assertions.assertThat(found.hits).isEmpty()
    Assertions.assertThat(found.indexComplete).isFalse()
}

@Test
fun `empty recall reports complete after successful state transition`() {
    val started = Instant.parse("2026-09-10T12:00:00Z")
    val claim = state.claim()
    state.finish(
        claim,
        VectorRebuildReport(started, started.plusSeconds(1), 4L, 4L, 0L, 0L),
        null,
    )
    val emptyService = MessageRecallServiceImpl<Long>(
        MockVectorStore(),
        LongUtil(),
        "long",
        state,
    )

    val found = result(emptyService.recallGlobal(GlobalRecallRequest("apple banana")))

    Assertions.assertThat(found.indexComplete).isTrue()
    Assertions.assertThat(found.hits).isEmpty()
}
```

Update existing assertions to read `found.hits`.

Replace each former `blockLast()` call with `block()`.

Keep the filter, threshold, limit, and bounded scheduler assertions.

Add this helper to the live-add test class:

```kotlin
private fun markComplete(state: VectorIndexState) {
    val started = Instant.parse("2026-09-10T12:00:00Z")
    val claim = state.claim()
    state.finish(
        claim,
        VectorRebuildReport(started, started.plusSeconds(1), 1L, 1L, 0L, 0L),
        null,
    )
}
```

### Step 2: Add a failing live-add invalidation test

Update the test indexer constructor to receive the state.

Use a `VectorStore` that throws from `add`.

Assert the original failure and the state change:

```kotlin
@Test
fun `live vector failure marks the index incomplete`() {
    markComplete(state)
    val failure = IllegalStateException("vector down")
    val store = mock<VectorStore>()
    BDDMockito.willThrow(failure).given(store).add(anyList())
    val indexer = VectorStoreMessageVectorIndexer<Long>(store, mapper, state)

    StepVerifier.create(indexer.add(recordedMessage))
        .expectErrorSatisfies { error -> Assertions.assertThat(error).isSameAs(failure) }
        .verify()

    Assertions.assertThat(state.status().complete).isFalse()
    Assertions.assertThat(state.status().lastFailure).contains("vector down")
}
```

### Step 3: Run the focused tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-service-composite \
  -Dtest=MessageRecallServiceImplTests,MessagingServiceVectorTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `MessageRecallResult` and new constructors do not exist.

### Step 4: Add the recall result and service signatures

Create this result:

```kotlin
package com.demo.chat.service.vector

data class MessageRecallResult<T>(
    val indexComplete: Boolean,
    val hits: List<MessageRecallHit<T>>,
)
```

Change each interface method to return `Mono<MessageRecallResult<T>>`.

Remove the `Flux` import from `MessageRecallService.kt`.

### Step 5: Wrap vector searches with completeness

Inject `VectorIndexState` into `MessageRecallServiceImpl`.

Keep request validation inside `Mono.defer`.

Change the shared search result:

```kotlin
private fun search(
    query: String,
    limit: Int,
    threshold: Double,
    filter: String,
): Mono<MessageRecallResult<T>> {
    val builder = SearchRequest.builder()
        .query(query)
        .topK(limit)
    if (threshold == 0.0) {
        builder.similarityThresholdAll()
    } else {
        builder.similarityThreshold(threshold)
    }
    val request = builder.filterExpression(filter).build()

    return Mono.fromCallable { vectorStore.similaritySearch(request) }
        .subscribeOn(Schedulers.boundedElastic())
        .map { documents ->
            MessageRecallResult(
                indexComplete = state.status().complete,
                hits = documents.map { toHit(it) },
            )
        }
}
```

Update all three public methods to use this `Mono` result.

Remove the stale no-backfill class comment.

### Step 6: Invalidate state on live vector failures

Inject `VectorIndexState` into `VectorStoreMessageVectorIndexer`.

Add this operator before `.then()`:

```kotlin
.doOnError { error ->
    state.invalidate("${error.javaClass.simpleName}: ${error.message ?: "No message"}")
}
```

Keep the original error signal unchanged.

Do not retry the vector write.

### Step 7: Run focused recall and indexer tests

Run the command from Step 3.

Expected: all updated service tests pass.

### Step 8: Commit Task 3

Run:

```bash
git add chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallResult.kt \
  chat-core/src/main/kotlin/com/demo/chat/service/vector/MessageRecallService.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/MessageRecallServiceImpl.kt \
  chat-service-composite/src/main/kotlin/com/demo/chat/service/composite/impl/VectorStoreMessageVectorIndexer.kt \
  chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessageRecallServiceImplTests.kt \
  chat-service-composite/src/test/kotlin/com/demo/chat/test/service/composite/MessagingServiceVectorTests.kt
git commit -m 'feat: report vector index completeness (CHAT-twocpczd)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-twocpczd \
  --comment 'Recall now returns bounded hits with completeness. Live add failures invalidate the state.'
```

## Task 4: Wire Vector State and Reindex Services

**FP issue:** `CHAT-awauccrm`

**Depends on:** `CHAT-mwvhqoyt`, `CHAT-twocpczd`

**Files:**

- Modify `chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt`.
- Modify `chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt`.

### Step 1: Extend the context tests

Register `TestPersistenceBeans<Long, String>` as `persistenceBeans` in every context.

Add assertions for all four conditional beans:

```kotlin
Assertions.assertThat(context.getBean(VectorIndexState::class.java)).isNotNull
Assertions.assertThat(context.getBean(MessageVectorIndexer::class.java)).isNotNull
Assertions.assertThat(context.getBean(MessageRecallService::class.java)).isNotNull
Assertions.assertThat(context.getBean(MessageReindexService::class.java)).isNotNull
```

For missing selectors, assert that all four bean-name arrays are empty.

Add a context without `app.service.composite`.

Assert that the configuration creates none of these beans.

### Step 2: Run the configuration tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-service-composite \
  -Dtest=VectorRecallServiceConfigurationTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new bean assertions fail.

### Step 3: Add the four conditional beans

Inject `PersistenceServiceBeans<T, V>` into the configuration constructor.

Use the existing selector annotation on every bean.

Implement this wiring:

```kotlin
@Bean
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
fun vectorIndexState(): VectorIndexState = InMemoryVectorIndexState()

@Bean
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
fun messageVectorIndexer(
    vectorStore: VectorStore,
    state: VectorIndexState,
    @Value("\${app.key.type}") keyType: String,
): MessageVectorIndexer<T> =
    VectorStoreMessageVectorIndexer(
        vectorStore,
        MessageDocumentMapper(typeUtil, keyType),
        state,
    )

@Bean
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
fun messageRecallService(
    vectorStore: VectorStore,
    state: VectorIndexState,
    @Value("\${app.key.type}") keyType: String,
): MessageRecallService<T> =
    MessageRecallServiceImpl(vectorStore, typeUtil, keyType, state)

@Bean
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
fun messageReindexService(
    indexer: MessageVectorIndexer<T>,
    state: VectorIndexState,
): MessageReindexService<T> =
    MessageReindexServiceImpl(
        persistenceBeans.messagePersistence(),
        indexer,
        state,
    )
```

Do not inject `MessagePersistence<T, V>` by type.

### Step 4: Run focused configuration and service tests

Run:

```bash
mvn -o -B -pl chat-core,chat-service-composite \
  -Dtest=VectorRecallServiceConfigurationTests,InMemoryVectorIndexStateTests,MessageReindexServiceImplTests,MessageRecallServiceImplTests,MessagingServiceVectorTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: every named test passes.

### Step 5: Commit Task 4

Run:

```bash
git add chat-service-composite/src/main/kotlin/com/demo/chat/config/service/composite/VectorRecallServiceConfiguration.kt \
  chat-service-composite/src/test/kotlin/com/demo/chat/test/config/VectorRecallServiceConfigurationTests.kt
git commit -m 'feat: wire message vector reindex (CHAT-awauccrm)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-awauccrm \
  --comment 'Wired state, recall, indexer, and reindex services through PersistenceServiceBeans.'
```

## Task 5: Change Recall Transports to One Result

**FP issue:** `CHAT-jddllrrx`

**Depends on:** `CHAT-twocpczd`

**Files:**

- Modify `chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt`.
- Modify `chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt`.
- Modify `chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt`.
- Modify `chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt`.

### Step 1: Change the RSocket test to request-response

Make the mock return one result:

```kotlin
BDDMockito
    .given(recallService.recallInTopic(anyObject()))
    .willReturn(
        Mono.just(
            MessageRecallResult(
                indexComplete = false,
                hits = listOf(MessageRecallHit(MessageKey.create(10L, 20L, 30L), 0.9)),
            )
        )
    )
```

Retrieve the parameterized result:

```kotlin
.retrieveMono(object : ParameterizedTypeReference<MessageRecallResult<Long>>() {})
```

Assert `indexComplete=false` and the existing key values.

Add one empty-hit response assertion.

### Step 2: Change the REST tests to one JSON object

Make every mock return `Mono<MessageRecallResult<Long>>`.

Assert the JSON media type and object fields:

```kotlin
client.post()
    .uri("/message/recall/topic")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue("""{"type":"TopicRecallRequest","topicId":30,"query":"apple"}""")
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    .expectBody()
    .jsonPath("$.indexComplete").isEqualTo(false)
    .jsonPath("$.hits[0].key.id").isEqualTo(10)
    .jsonPath("$.hits[0].score").isEqualTo(0.9)
```

Add an empty result and assert `$.hits` is an empty array.

### Step 3: Run both transport tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-service-controller,chat-webflux -am \
  -Dtest=MessageRecallControllerTests,MessageRecallRestTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because both controllers still return `Flux`.

### Step 4: Change the RSocket route signatures

Change every mapping method to return `Mono<MessageRecallResult<T>>`:

```kotlin
@MessageMapping("message-recall-topic")
override fun recallInTopic(req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>>

@MessageMapping("message-recall-user")
override fun recallByUser(req: UserRecallRequest<T>): Mono<MessageRecallResult<T>>

@MessageMapping("message-recall-global")
override fun recallGlobal(req: GlobalRecallRequest): Mono<MessageRecallResult<T>>
```

Remove the old `Flux` and `MessageRecallHit` imports.

### Step 5: Change REST from NDJSON to JSON

Change all three methods to this form:

```kotlin
@PostMapping("/topic", produces = [MediaType.APPLICATION_JSON_VALUE])
fun recallInTopic(@RequestBody req: TopicRecallRequest<T>): Mono<MessageRecallResult<T>> =
    recallService.recallInTopic(req)
```

Apply the same return type and media type to user and global recall.

Update the class comment to state that each route returns one bounded result.

### Step 6: Run the transport tests

Run the command from Step 3.

Expected: both named test classes pass.

### Step 7: Commit Task 5

Run:

```bash
git add chat-service-controller/src/main/kotlin/com/demo/chat/controller/composite/mapping/MessageRecallControllerMapping.kt \
  chat-service-controller/src/test/kotlin/com/demo/chat/test/recall/controller/MessageRecallControllerTests.kt \
  chat-webflux/src/main/kotlin/com/demo/chat/controller/webflux/ChatMessageRecallController.kt \
  chat-webflux/src/test/kotlin/com/demo/chat/test/controller/webflux/composite/MessageRecallRestTests.kt
git commit -m 'feat: return recall completeness on transports (CHAT-jddllrrx)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-jddllrrx \
  --comment 'REST and RSocket now return one bounded recall result with completeness.'
```

## Task 6: Add the Protected Vector Index Actuator Endpoint

**FP issue:** `CHAT-zepiarzb`

**Depends on:** `CHAT-awauccrm`

**Files:**

- Create `chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt`.
- Create `chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt`.

### Step 1: Write property-gate tests

Use `AnnotationConfigApplicationContext` with a mocked `MessageReindexService<Long>`.

Register `VectorIndexEndpoint` directly.

Add these contexts:

1. Composite and both selectors set.
2. Both selectors set without composite.
3. Composite set without both selectors.

Assert that only the first context contains `VectorIndexEndpoint`.

Use this helper:

```kotlin
private fun context(properties: Map<String, Any>): AnnotationConfigApplicationContext {
    val context = AnnotationConfigApplicationContext()
    context.environment.propertySources.addFirst(MapPropertySource("test", properties))
    context.beanFactory.registerSingleton("messageReindexService", reindexService)
    context.register(VectorIndexEndpoint::class.java)
    context.refresh()
    return context
}
```

### Step 2: Write endpoint delegation tests

Add one read test and one nonblocking write test:

```kotlin
@Test
fun `read returns the current status`() {
    given(reindexService.status()).willReturn(incompleteStatus)

    Assertions.assertThat(endpoint.status()).isEqualTo(incompleteStatus)
}

@Test
fun `write returns the running status`() {
    given(reindexService.start()).willReturn(Mono.just(runningStatus))

    StepVerifier.create(endpoint.rebuild())
        .expectNext(runningStatus)
        .verifyComplete()
}
```

### Step 3: Write the actuator security test

Start one minimal reactive Boot test application.

Import `VectorIndexEndpoint` and `ActuatorWebSecurityConfiguration`.

Supply a `PasswordEncoder` and a fixed reindex service from test configuration.

Set these properties:

```text
app.service.composite=true
app.service.core.vector=embedded
app.service.core.embedding=mock
app.actuator.username=actuator
app.actuator.password=actuator
management.endpoints.enabled-by-default=false
management.endpoint.vectorindex.enabled=true
management.endpoints.web.exposure.include=vectorindex
```

Assert the HTTP boundary:

```kotlin
client.get().uri("/actuator/vectorindex")
    .exchange()
    .expectStatus().isUnauthorized

client.get().uri("/actuator/vectorindex")
    .headers { it.setBasicAuth("actuator", "actuator") }
    .exchange()
    .expectStatus().isOk
    .expectBody()
    .jsonPath("$.phase").isEqualTo("INCOMPLETE")
```

Add an authenticated POST assertion for the write operation.

Use this exact test application structure:

```kotlin
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [VectorIndexEndpointTestApplication::class],
    properties = [
        "app.service.composite=true",
        "app.service.core.vector=embedded",
        "app.service.core.embedding=mock",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
        "management.endpoints.enabled-by-default=false",
        "management.endpoint.vectorindex.enabled=true",
        "management.endpoints.web.exposure.include=vectorindex",
    ],
)
class VectorIndexEndpointHttpTests(
    @Autowired private val client: WebTestClient,
) {
    @Test
    fun `actuator security protects vector index operations`() {
        client.get().uri("/actuator/vectorindex")
            .exchange()
            .expectStatus().isUnauthorized

        client.get().uri("/actuator/vectorindex")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.phase").isEqualTo("INCOMPLETE")

        client.post().uri("/actuator/vectorindex")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.phase").isEqualTo("REBUILDING")
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(
    VectorIndexEndpoint::class,
    ActuatorWebSecurityConfiguration::class,
    VectorIndexEndpointTestBeans::class,
)
class VectorIndexEndpointTestApplication

@TestConfiguration(proxyBeanMethods = false)
class VectorIndexEndpointTestBeans {
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun messageReindexService(): MessageReindexService<Long> =
        object : MessageReindexService<Long> {
            override fun start(): Mono<VectorIndexStatus> = Mono.just(runningStatus)

            override fun status(): VectorIndexStatus = incompleteStatus
        }
}
```

Define these top-level private values:

```kotlin
private val incompleteStatus = VectorIndexStatus(VectorIndexPhase.INCOMPLETE)
private val runningStatus = VectorIndexStatus(VectorIndexPhase.REBUILDING)
```

### Step 4: Run the endpoint tests and confirm the red state

Run:

```bash
mvn -o -B -pl chat-core,chat-deploy -am \
  -Dtest=VectorIndexEndpointTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `VectorIndexEndpoint` does not exist.

### Step 5: Implement the endpoint and both property gates

Create this component:

```kotlin
package com.demo.chat.config.deploy.actuator

import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexStatus
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@Endpoint(id = "vectorindex", enableByDefault = true)
@ConditionalOnProperty("app.service.composite")
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
class VectorIndexEndpoint<T>(
    private val reindexService: MessageReindexService<T>,
) {
    @ReadOperation
    fun status(): VectorIndexStatus = reindexService.status()

    @WriteOperation
    fun rebuild(): Mono<VectorIndexStatus> = reindexService.start()
}
```

Do not use `@ConditionalOnBean`.

Do not add deployment exposure properties.

### Step 6: Run endpoint tests

Run the command from Step 4.

Expected: property, delegation, and security tests pass.

### Step 7: Commit Task 6

Run:

```bash
git add chat-deploy/src/main/kotlin/com/demo/chat/config/deploy/actuator/VectorIndexEndpoint.kt \
  chat-deploy/src/test/kotlin/com/demo/chat/deploy/test/VectorIndexEndpointTests.kt
git commit -m 'feat: add vector index actuator endpoint (CHAT-zepiarzb)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-zepiarzb \
  --comment 'Added protected actuator status and rebuild operations with deterministic property gates.'
```

## Task 7: Prove Recovery After Embedded Index Loss

**FP issue:** `CHAT-neucngmw`

**Depends on:** `CHAT-awauccrm`, `CHAT-jddllrrx`, `CHAT-zepiarzb`

**Files:**

- Create `chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/EmbeddedVectorReindexRecoveryTests.kt`.

### Step 1: Write the exact storage-loss test

Keep one in-memory persistence instance across two vector-store contexts.

Use `@TempDir` for the configured embedded storage path.

Create each vector context with this helper:

```kotlin
private fun vectorContext(storage: Path): AnnotationConfigApplicationContext {
    val context = AnnotationConfigApplicationContext()
    context.environment.propertySources.addFirst(
        MapPropertySource(
            "test",
            mapOf(
                "app.service.core.vector" to "embedded",
                "app.service.core.vector.embedded.path" to storage.toString(),
            ),
        )
    )
    context.beanFactory.registerSingleton("embeddingModel", DummyEmbeddingModel())
    context.register(EmbeddedVectorStoreConfiguration::class.java)
    context.refresh()
    return context
}
```

Create retained persistence with `MessagePersistenceInMemory`:

```kotlin
val keyService = mock<IKeyService<Long>>()
val persistence = MessagePersistenceInMemory<Long, String>(keyService) { it.key }
val message = Message.create(MessageKey.create(1L, 10L, 100L), "apple banana", true)
persistence.add(message).block()
```

Run the two storage lifecycles:

```kotlin
vectorContext(storage).use { first ->
    first.getBean(VectorStore::class.java).add(
        listOf(Document.builder().id("old").text("old vector").build())
    )
}

Assertions.assertThat(storage.toFile().deleteRecursively()).isTrue()

vectorContext(storage).use { second ->
    val state = InMemoryVectorIndexState()
    val store = second.getBean(VectorStore::class.java)
    val mapper = MessageDocumentMapper<Long>(LongUtil(), "long")
    val indexer = VectorStoreMessageVectorIndexer<Long>(store, mapper, state)
    val reindex = MessageReindexServiceImpl(persistence, indexer, state)
    val recall = MessageRecallServiceImpl<Long>(store, LongUtil(), "long", state)

    Assertions.assertThat(recall.recallGlobal(GlobalRecallRequest("apple")).block()!!.hits)
        .isEmpty()

    Assertions.assertThat(reindex.start().block()!!.running).isTrue()
    val final = awaitFinished(reindex)

    Assertions.assertThat(final.complete).isTrue()
    val result = recall.recallGlobal(GlobalRecallRequest("apple")).block()!!
    Assertions.assertThat(result.indexComplete).isTrue()
    Assertions.assertThat(result.hits.map { it.key.id }).containsExactly(1L)
}
```

Implement `awaitFinished` with a Reactor interval and a ten-second timeout.

Do not delete the directory while the first context is active.

### Step 2: Run the integrated recovery test

Run:

```bash
mvn -o -B -pl chat-core,chat-deploy-memory -am \
  -Dtest=EmbeddedVectorReindexRecoveryTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the integrated recovery test passes with the prior implementation tasks.

### Step 3: Complete the test helper and imports

Use this wait helper:

```kotlin
private fun awaitFinished(service: MessageReindexService<Long>): VectorIndexStatus =
    Flux.interval(Duration.ZERO, Duration.ofMillis(10))
        .map { service.status() }
        .filter { !it.running }
        .next()
        .block(Duration.ofSeconds(10))!!
```

Keep the Vector API module flag from the existing module configuration.

Do not add a second Surefire flag.

### Step 4: Run the focused recovery test

Run the command from Step 2.

Expected: the test closes the first mapping, deletes storage, rebuilds, and recalls one message.

### Step 5: Commit Task 7

Run:

```bash
git add chat-deploy-memory/src/test/kotlin/com/demo/chat/test/deploy/memory/EmbeddedVectorReindexRecoveryTests.kt
git commit -m 'test: prove vector recovery after index loss (CHAT-neucngmw)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-neucngmw \
  --comment 'Proved recall recovery after closed embedded storage deletion and full reindex.'
```

## Task 8: Update Documentation and Run Final Verification

**FP issue:** `CHAT-jqvclfbd`

**Depends on:** `CHAT-neucngmw`

**Files:**

- Modify `docs/superpowers/specs/2026-09-01-message-vector-recall-design.md`.
- Modify `forward-register.md`.

### Step 1: Check documentation bindings

Run:

```bash
drift refs docs/superpowers/specs/2026-09-01-message-vector-recall-design.md
drift refs forward-register.md
```

Read every reported target before a documentation change.

### Step 2: Update the historical recall design

Keep its original out-of-scope decision as history.

Add a dated supersession note near `Open Follow-Up`:

```markdown
### Reindex follow-up completed

Issue `CHAT-oghjsnad` later added the operator-controlled full reindex path.

Recall now returns one bounded result with an in-process completeness flag.

The original sprint still delivered no reindex path.
```

### Step 3: Update the forward register

Replace the current no-rebuild statements with verified implementation facts.

Record these boundaries:

- Operator-triggered actuator action.
- Process-local completeness state.
- Full persistence scan.
- Whole-corpus RSocket cost for split deployments.
- One unchecked `V` to `String` cast.
- Accepted invalidation false negative.
- Focused and full verification results.

Do not update verification claims before each command passes.

### Step 4: Run focused module tests

Run:

```bash
mvn -o -B \
  -pl chat-core,chat-service-composite,chat-service-controller,chat-webflux,chat-deploy,chat-deploy-memory \
  -am \
  -Dtest=InMemoryVectorIndexStateTests,MessageReindexServiceImplTests,MessageRecallServiceImplTests,MessagingServiceVectorTests,VectorRecallServiceConfigurationTests,MessageRecallControllerTests,MessageRecallRestTests,VectorIndexEndpointTests,EmbeddedVectorReindexRecoveryTests \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: every named test passes.

### Step 5: Run the default build-health gate

Run:

```bash
./shell-scripts/build-health.sh
```

Expected: the default mode reports that reality matches `docs/BUILD-HEALTH.md`.

Do not start the integration mode unless the user requests it.

### Step 6: Run structural and drift checks

Run:

```bash
drift check
git diff --check -- \
  chat-core chat-service-composite chat-service-controller chat-webflux \
  chat-deploy chat-deploy-memory docs/superpowers/specs forward-register.md
```

Expected: both scoped checks pass.

Run `git diff --check` once for the whole tree.

Record the unrelated `.gitignore` baseline separately if it remains.

### Step 7: Commit Task 8

Run:

```bash
git add docs/superpowers/specs/2026-09-01-message-vector-recall-design.md forward-register.md
git commit -m 'docs: record message vector reindex (CHAT-jqvclfbd)'
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-jqvclfbd \
  --comment 'Updated reindex documentation. Focused tests, default build health, and drift checks pass.'
```

### Step 8: Close the parent issue

Inspect the cumulative issue diff first:

```bash
FP_AGENT_NAME='sigma' fp issue diff CHAT-oghjsnad --stat
FP_AGENT_NAME='sigma' fp issue files CHAT-oghjsnad
```

Confirm that all eight child issues are done.

Then close the parent:

```bash
FP_AGENT_NAME='sigma' fp issue update --status done CHAT-oghjsnad \
  --comment 'Message vector reindex is complete. The operator trigger, completeness contract, storage-loss proof, and default build verification pass.'
```

Do not close `CHAT-ruduojeu`.

That issue still owns general fanout retry, compensation, and repair.

## Final Acceptance Map

| Requirement | Proof |
|---|---|
| One rebuild job at a time | Task 1 state tests and Task 2 busy test |
| Full recorded-message scan | Task 2 reindex tests |
| Individual failure continuation | Task 2 failure-count test |
| Scan failure stays incomplete | Task 2 scan test |
| Live add invalidates completeness | Task 3 indexer test |
| Empty recall carries completeness | Task 3 service test and Task 5 transport tests |
| Deterministic endpoint gates | Task 6 property tests |
| Existing actuator security protects writes | Task 6 HTTP security test |
| Lost embedded storage can recover | Task 7 two-lifecycle test |
| Documentation matches behavior | Task 8 drift check |
