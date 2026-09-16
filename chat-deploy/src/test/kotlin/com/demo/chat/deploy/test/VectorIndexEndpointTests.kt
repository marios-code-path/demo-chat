package com.demo.chat.deploy.test

import com.demo.chat.config.deploy.actuator.VectorIndexEndpoint
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexJobStore
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import reactor.test.StepVerifier
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class VectorIndexEndpointTests {
    private val start = Instant.parse("2026-09-12T12:00:00Z")

    /**
     * Records how many starts reached the service. The endpoint must start one
     * job for one write, and it must return at once rather than wait.
     */
    private class RecordingReindexService : MessageReindexService<Long> {
        val starts = AtomicInteger()
        var running = false

        override fun start(): Mono<VectorIndexTriggerResult<Long>> = Mono.fromSupplier {
            // A second start finds a run in progress, so the claim is
            // rejected. The endpoint answers with that fact now.
            val accepted = !running
            if (accepted) {
                starts.incrementAndGet()
                running = true
            }
            VectorIndexTriggerResult(accepted, status())
        }

        override fun status(): VectorIndexStatus<Long> =
            VectorIndexStatus(
                phase = if (running) VectorIndexPhase.REBUILDING else VectorIndexPhase.INCOMPLETE
            )
    }

    private class FakeJobStore : VectorIndexJobStore<Long> {
        val jobs = linkedMapOf<Long, IndexJob<Long>>()
        val names = mutableMapOf<Long, String>()
        var failListing = false
        var hangListing = false

        /** Counts every call of listJobTopics, at assembly time or later. */
        var listings = 0

        override fun createJob(startedAt: Instant) =
            Mono.error<IndexJob<Long>>(UnsupportedOperationException("the endpoint never creates a job"))

        override fun write(job: IndexJob<Long>): Mono<Void> =
            Mono.fromRunnable { jobs[job.key.id] = job }

        override fun finishJob(job: IndexJob<Long>): Mono<Void> = write(job)

        val reads = mutableListOf<Long>()

        override fun readJob(topicKey: Key<Long>): Mono<IndexJob<Long>> =
            Mono.defer {
                reads.add(topicKey.id)
                Mono.justOrEmpty(jobs[topicKey.id])
            }

        override fun listJobTopics(): Flux<out MessageTopic<Long>> {
            listings += 1
            return if (hangListing) {
                Flux.never()
            } else if (failListing) {
                Flux.error(IllegalStateException("topic listing failed"))
            } else {
                Flux.fromIterable(
                    jobs.values.map { job ->
                        MessageTopic.create(
                            job.key,
                            names[job.key.id] ?: JobTopicNames.nameFor(
                                job.nodeId,
                                job.keyType,
                                job.startedAt,
                                job.incarnationId,
                            )
                        )
                    }
                )
            }
        }

        override fun invalidate(jobKey: Key<Long>, at: Instant): Mono<Void> = Mono.empty()
    }

    private val service = RecordingReindexService()
    private val store = FakeJobStore()

    private fun job(
        id: Long,
        startedAt: Instant = start,
        nodeId: Int = 7,
        keyType: String = "long",
    ): IndexJob<Long> = IndexJob(
        key = Key.funKey(id),
        nodeId = nodeId,
        keyType = keyType,
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(1000L),
        startedAt = startedAt,
        outcome = JobOutcome.SUCCEEDED,
    )

    private fun endpoint() = VectorIndexEndpoint(service, store, LongUtil(), 7, "long")

    @Test
    fun `the read operation returns the status and no job`() {
        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.status.running).isFalse()
        Assertions.assertThat(report.status.complete).isFalse()
        Assertions.assertThat(report.jobs).isEmpty()
        Assertions.assertThat(service.starts.get()).isEqualTo(0)
    }

    @Test
    fun `the read operation returns the jobs of this node`() {
        store.write(job(1L)).block()

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(1L)
    }

    // Newest first. An operator reads the last run at the top.
    @Test
    fun `the read sorts by instant, then by the typed root key`() {
        store.write(job(9L, startedAt = start)).block()
        store.write(job(10L, startedAt = start)).block()
        store.write(job(3L, startedAt = start.plusSeconds(60))).block()

        val report = endpoint().readVectorIndex().block()!!

        // 10 above 9 proves the number compare. A text compare inverts them.
        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(3L, 10L, 9L)
    }

    @Test
    fun `the read returns at most fifty jobs`() {
        (1L..60L).forEach { id -> store.write(job(id, startedAt = start.plusSeconds(id))).block() }

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs).hasSize(50)
        // The newest survives the bound, and the oldest falls off it.
        Assertions.assertThat(report.jobs.first().key.id).isEqualTo(60L)
        Assertions.assertThat(report.jobs.map { it.key.id }).doesNotContain(1L)
    }

    @Test
    fun `the read skips another node's job topic`() {
        store.write(job(1L)).block()
        store.write(job(2L, nodeId = 9)).block()

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(1L)
    }

    // The name and the record are two stored things. A local name over a
    // foreign record must not reach an operator as this deployment's job.
    @Test
    fun `the read skips a record that disagrees with its topic name`() {
        store.write(job(1L, nodeId = 9)).block()
        store.names[1L] = JobTopicNames.nameFor(7, "long", start, "incarnation-a")
        store.write(job(2L)).block()

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(2L)
    }

    // The inverse of the test above. A foreign topic name over a local record
    // must not appear either. Another node named that topic, so the record is
    // not this deployment's to report.
    @Test
    fun `the read skips a foreign topic name over a local record`() {
        store.write(job(1L)).block()
        store.names[1L] = JobTopicNames.nameFor(9, "long", start, "incarnation-a")
        store.write(job(2L)).block()

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(2L)
    }

    @Test
    fun `the read skips a topic name of another key type`() {
        store.write(job(1L)).block()
        store.names[1L] = JobTopicNames.nameFor(7, "uuid", start, "incarnation-a")
        store.write(job(2L)).block()

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.jobs.map { it.key.id }).containsExactly(2L)
    }

    // The status is in process and always available. A store failure must not
    // take the whole operation away from an operator.
    @Test
    fun `a failed listing keeps the status and empties the jobs`() {
        store.failListing = true

        val report = endpoint().readVectorIndex().block()!!

        Assertions.assertThat(report.status.running).isFalse()
        Assertions.assertThat(report.jobs).isEmpty()
    }

    // An accepted trigger never promises the job key. The run creates its job
    // after the claim, and on another scheduler. A client polls the read.
    @Test
    fun `the write operation starts one job and reports no active job`() {
        val result = endpoint().startVectorIndexRebuild().block()!!

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        Assertions.assertThat(result.accepted).isTrue()
        Assertions.assertThat(result.status.running).isTrue()
        Assertions.assertThat(result.status.activeJob).isNull()
    }

    @Test
    fun `a second write returns busy and starts no second job`() {
        val endpoint = endpoint()

        endpoint.startVectorIndexRebuild().block()
        val second = endpoint.startVectorIndexRebuild().block()!!

        Assertions.assertThat(service.starts.get()).isEqualTo(1)
        // The status cannot carry this fact. Both answers report running=true,
        // because the first run holds the claim.
        Assertions.assertThat(second.accepted).isFalse()
        Assertions.assertThat(second.status.running).isTrue()
    }

    // A store that never answers must not hold the operation open. Virtual
    // time moves past the bound without a real wait. The status survives,
    // exactly as it does for a store error.
    @Test
    fun `a hanging store returns the status and no job after the bound`() {
        store.hangListing = true

        StepVerifier
            .withVirtualTime { endpoint().readVectorIndex() }
            .thenAwait(VectorIndexEndpoint.READ_TIMEOUT)
            .assertNext { report ->
                Assertions.assertThat(report.status.running).isFalse()
                Assertions.assertThat(report.jobs).isEmpty()
            }
            // A real bound, beside the virtual one. A chain that loses its
            // timeout then fails this test rather than hanging the build.
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    // The read answers with a publisher, and nothing runs inside it until a
    // subscriber arrives. The listing counts too, because a store that reads at
    // assembly time would run before that moment.
    @Test
    fun `the read runs nothing before a subscriber arrives`() {
        store.write(job(1L)).block()

        endpoint().readVectorIndex()

        Assertions.assertThat(store.listings).isEqualTo(0)
        Assertions.assertThat(store.reads).isEmpty()
    }

    private fun runnerWithBeans() = ApplicationContextRunner()
        .withBean(MessageReindexService::class.java, { service })
        .withBean(VectorIndexJobStore::class.java, { store })
        .withBean(TypeUtil::class.java, { LongUtil() })
        .withUserConfiguration(VectorIndexEndpoint::class.java)

    // Both gates matter. VectorRecallServiceConfiguration carries the composite
    // gate at class level and the selectors at bean level, so a selector-only
    // gate would expose this endpoint where no composite service exists.
    @Test
    fun `the endpoint is absent without the composite gate`() {
        runnerWithBeans()
            .withPropertyValues(
                "app.nodeid=7",
                "app.key.type=long",
                "app.service.core.vector=embedded",
                "app.service.core.embedding=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).doesNotHaveBean(VectorIndexEndpoint::class.java)
            }
    }

    @Test
    fun `the endpoint is absent when one recall selector is missing`() {
        runnerWithBeans()
            .withPropertyValues(
                "app.nodeid=7",
                "app.key.type=long",
                "app.service.composite=true",
                "app.service.core.vector=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).doesNotHaveBean(VectorIndexEndpoint::class.java)
            }
    }

    @Test
    fun `the endpoint exists with both gates`() {
        runnerWithBeans()
            .withPropertyValues(
                "app.nodeid=7",
                "app.key.type=long",
                "app.service.composite=true",
                "app.service.core.vector=embedded",
                "app.service.core.embedding=embedded",
            )
            .run { context ->
                Assertions.assertThat(context).hasSingleBean(VectorIndexEndpoint::class.java)
            }
    }
}
