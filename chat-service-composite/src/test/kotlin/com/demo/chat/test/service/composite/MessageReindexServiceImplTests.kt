package com.demo.chat.test.service.composite

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.MessageReindexServiceImpl
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorRebuildReport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.timeout
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration
import java.time.Instant

class MessageReindexServiceImplTests {
    private val startedAt = Instant.parse("2026-09-10T12:00:00Z")
    private val finishedAt = Instant.parse("2026-09-10T12:00:01Z")
    private val persistence = mock<MessagePersistence<Long, String>>()
    private val indexer = RecordingIndexer()
    private val state = InMemoryVectorIndexState<Long>()
    private val clock = mock<Clock>()
    private val jobStore = FakeJobStore()
    private val jobTopicName = JobTopicNames.nameFor(7, "long", Instant.EPOCH, "incarnation-a")
    private lateinit var scheduler: Scheduler
    private lateinit var service: MessageReindexServiceImpl<Long, String>

    @BeforeEach
    fun configureService() {
        given(clock.instant()).willReturn(startedAt, finishedAt)
        scheduler = Schedulers.newSingle("reindex-test")
        service = MessageReindexServiceImpl(
            persistence,
            indexer,
            state,
            jobStore,
            clock,
            scheduler,
        )
    }

    @AfterEach
    fun closeScheduler() {
        scheduler.dispose()
    }

    @Test
    fun `rebuild indexes recorded messages and skips alerts`() {
        given(persistence.all()).willReturn(
            Flux.just(
                message(1L, "apple", true),
                message(2L, "joined", false),
                message(3L, "banana", true),
            )
        )

        val final = runAndAwait(service)

        Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
        Assertions.assertThat(final.complete).isTrue()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 3L, 2L, 1L, 0L)
        )
    }

    @Test
    fun `one message failure does not stop later messages`() {
        indexer.failOn.add(2L)
        given(persistence.all()).willReturn(
            Flux.just(message(1L), message(2L), message(3L))
        )

        val final = runAndAwait(service)

        Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport!!.failed).isEqualTo(1L)
        Assertions.assertThat(final.lastFailure).contains("vector failure for 2")
    }

    @Test
    fun `persistence scan failure marks incomplete`() {
        given(persistence.all()).willReturn(
            Flux.concat(
                Flux.just(message(1L)),
                Flux.error(IllegalStateException("scan failed")),
            )
        )

        val final = runAndAwait(service)

        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L)
        )
        Assertions.assertThat(final.lastFailure).contains("scan failed")
    }

    @Test
    fun `synchronous persistence failure marks incomplete`() {
        given(persistence.all()).willThrow(IllegalStateException("scan assembly failed"))

        val final = runAndAwait(service)

        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 0L, 0L, 0L, 0L)
        )
        Assertions.assertThat(final.lastFailure).contains("scan assembly failed")
    }

    @Test
    fun `second start returns busy and starts no second scan`() {
        val release = Sinks.empty<Void>()
        given(persistence.all()).willReturn(
            Flux.just(message(1L))
                .concatWith(release.asMono().thenMany(Flux.empty()))
        )

        val first = service.start().block()!!
        val second = service.start().block()!!

        Assertions.assertThat(first.running).isTrue()
        Assertions.assertThat(second.running).isTrue()
        verify(persistence, timeout(1_000).times(1)).all()

        release.tryEmitEmpty()
        Assertions.assertThat(awaitFinished(service).complete).isTrue()
    }

    @Test
    fun `a job that cannot start releases the claim`() {
        given(persistence.all()).willReturn(Flux.just(message(1L)))
        scheduler.dispose()

        service.start().block()!!

        val released = awaitFinished(service)
        Assertions.assertThat(released.running).isFalse()
        Assertions.assertThat(released.complete).isFalse()
        Assertions.assertThat(released.lastFailure).isNotNull()
        Assertions.assertThat(service.start().block()!!.running).isTrue()
    }

    private fun runAndAwait(service: MessageReindexService<Long>): VectorIndexStatus {
        Assertions.assertThat(service.start().block()!!.running).isTrue()
        return awaitFinished(service)
    }

    private fun awaitFinished(service: MessageReindexService<Long>): VectorIndexStatus =
        Flux.interval(Duration.ZERO, Duration.ofMillis(10))
            .map { service.status() }
            .filter { !it.running }
            .next()
            .block(Duration.ofSeconds(10))!!

    private fun message(
        id: Long,
        text: String = "message $id",
        record: Boolean = true,
    ): Message<Long, String> =
        Message.create(MessageKey.create(id, 10L, 20L), text, record)


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

        override fun finishJob(job: IndexJob<Long>): Mono<Void> = Mono.fromRunnable { written.add(job) }

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

    private class RecordingIndexer : MessageVectorIndexer<Long> {
        val ids = mutableListOf<Long>()
        val failOn = mutableSetOf<Long>()

        override fun add(message: Message<Long, String>): Mono<Void> = Mono.defer {
            val id = message.key.id
            if (failOn.contains(id)) {
                Mono.error(IllegalStateException("vector failure for $id"))
            } else {
                ids.add(id)
                Mono.empty()
            }
        }

        override fun remove(key: Key<Long>): Mono<Void> = Mono.empty()
    }

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
    fun `a failed topic listing stops the scan`() {
        jobStore.failListing = true
        given(persistence.all()).willReturn(Flux.just(message(1L)))

        val status = runAndAwait(service)

        Assertions.assertThat(indexer.ids).isEmpty()
        Assertions.assertThat(status.complete).isFalse()
        verify(persistence, never()).all()
    }
}
