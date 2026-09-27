package com.demo.chat.test.service.composite

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.VectorCoveragePolicyImpl
import com.demo.chat.service.composite.impl.VectorIndexJobStoreImpl
import com.demo.chat.service.composite.impl.VectorIndexStartupAction
import com.demo.chat.service.vector.IndexJobCodec
import com.demo.chat.service.vector.JobLookupException
import com.demo.chat.service.vector.JobLookupFault
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import com.demo.chat.service.vector.VectorTrust
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * Every reader of a job by its topic fails closed on a lookup fault. See
 * `CHAT-avduuqwp`, D3.
 *
 * Each case builds its fault in the real store or the real index. No reader
 * may skip the broken topic, because that would expose an older job.
 */
class VectorIndexJobLookupFaultTests {
    private val t0 = Instant.parse("2026-09-25T12:00:00Z")
    private val t1 = t0.plusSeconds(60)
    private val t2 = t0.plusSeconds(120)

    private val keyValues = FakeKeyValueStore()
    private val keyValueIndex = FakeKeyValueIndex()
    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    /** The store writes jobs as an earlier incarnation. The startup action runs as a later one. */
    private val jobStore = VectorIndexJobStoreImpl(
        topicPersistence = FakeTopicPersistence(),
        topicIndex = FakeTopicIndex(),
        pubsub = FakePubSub(),
        keyValueStore = keyValues,
        keyValueIndex = keyValueIndex,
        topicIdQuery = { value -> mapOf(VectorIndexJobStoreImpl.TOPIC_ID to value) },
        codec = IndexJobCodec(mapper),
        nodeId = 7,
        keyType = "long",
        embeddingIdentity = EmbeddingIdentity("acme-e5-small-v2"),
        incarnationId = "incarnation-a",
        workerKey = Mono.just(Key.of(1000L, fakeRoot(ChatDomain.USER))),
    )

    private val policy = VectorCoveragePolicyImpl(
        jobStore = jobStore,
        trust = VectorTrust.STORED,
        incarnationId = "incarnation-b",
        nodeId = 7,
        keyType = "long",
        embeddingIdentity = EmbeddingIdentity("acme-e5-small-v2"),
        typeUtil = LongUtil(),
    )

    private val state = InMemoryVectorIndexState<Long>()
    private val startupLog = ListAppender<ILoggingEvent>()
    private val startupLogger = LoggerFactory.getLogger(VectorIndexStartupAction::class.java) as Logger

    @BeforeEach
    fun attachLog() {
        startupLog.start()
        startupLogger.addAppender(startupLog)
    }

    @AfterEach
    fun detachLog() {
        startupLogger.detachAppender(startupLog)
    }

    private fun errors(): List<String> =
        startupLog.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }

    private fun job(at: Instant, outcome: JobOutcome): IndexJob<Long> {
        val created = jobStore.createJob(at).block()!!
        val changed = created.copy(outcome = outcome)
        jobStore.write(changed).block()
        return changed
    }

    private fun inject(fault: JobLookupFault, job: IndexJob<Long>) {
        when (fault) {
            JobLookupFault.MISSING -> keyValueIndex.entries.remove(job.key)
            JobLookupFault.DUPLICATE -> keyValueIndex.entries[Key.of(4343L, fakeRoot(ChatDomain.KEY_VALUE_PAIR))] =
                mapOf(VectorIndexJobStoreImpl.TOPIC_ID to job.topicKey.id.toString())
            JobLookupFault.DANGLING -> keyValues.values.remove(job.key.id)
            JobLookupFault.STORED_KEY_MISMATCH -> keyValues.values[job.key.id] = KeyValuePair.create(
                job.key,
                job.copy(key = Key.of(4242L, fakeRoot(ChatDomain.KEY_VALUE_PAIR))) as Any,
            )
            // The id is right and the root is another domain root.
            JobLookupFault.TOPIC_MISMATCH -> keyValues.values[job.key.id] = KeyValuePair.create(
                job.key,
                job.copy(topicKey = Key.of(job.topicKey.id, fakeRoot(ChatDomain.MESSAGE))) as Any,
            )
        }
    }

    private inner class RecordingReindex : MessageReindexService<Long> {
        var starts = 0

        override fun start(): Mono<VectorIndexTriggerResult<Long>> = Mono.fromSupplier {
            starts++
            VectorIndexTriggerResult(true, state.status())
        }

        override fun status(): VectorIndexStatus<Long> = state.status()
    }

    private fun startup(reindex: MessageReindexService<Long>, startRebuild: Boolean) = VectorIndexStartupAction(
        jobStore = jobStore,
        policy = policy,
        state = state,
        reindex = reindex,
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-b",
        startRebuild = startRebuild,
    )

    @ParameterizedTest
    @EnumSource(JobLookupFault::class)
    fun `coverage fails on every lookup fault, and never selects an older job`(fault: JobLookupFault) {
        job(t0, JobOutcome.SUCCEEDED)
        val newer = job(t1, JobOutcome.SUCCEEDED)
        inject(fault, newer)

        // An error signal carries no job, so the older job was not selected.
        StepVerifier.create(policy.selectCoveringJob())
            .verifyErrorSatisfies { error ->
                assertThat(error).isInstanceOf(JobLookupException::class.java)
                assertThat((error as JobLookupException).fault).isEqualTo(fault)
                assertThat(error).hasMessageContaining(fault.description)
            }
    }

    @ParameterizedTest
    @EnumSource(JobLookupFault::class)
    fun `the startup sweep logs a lookup fault and releases every other stale job`(fault: JobLookupFault) {
        val broken = job(t0, JobOutcome.RUNNING)
        val healthy = job(t1, JobOutcome.RUNNING)
        inject(fault, broken)

        startup(RecordingReindex(), startRebuild = false).run().block()

        assertThat(jobStore.readJob(healthy.key).block()!!.outcome).isEqualTo(JobOutcome.RELEASED)
        assertThat(errors()).anyMatch { it.contains(fault.description) }
    }

    @ParameterizedTest
    @EnumSource(JobLookupFault::class)
    fun `startup treats a lookup fault as no coverage, and starts the requested rebuild`(fault: JobLookupFault) {
        val older = job(t0, JobOutcome.SUCCEEDED)
        val newer = job(t1, JobOutcome.SUCCEEDED)
        inject(fault, newer)
        val reindex = RecordingReindex()

        startup(reindex, startRebuild = true).run().block()

        // The older job succeeded and is intact, and run() still adopts no job.
        assertThat(jobStore.readJob(older.key).block()!!.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        assertThat(state.status().coveringJob).isNull()
        assertThat(reindex.starts).isEqualTo(1)
        assertThat(errors()).anyMatch { it.contains(fault.description) }
    }

    /** A control. Without a fault the same setup adopts the newer job. */
    @Test
    fun `startup adopts the newer job when no fault exists`() {
        job(t0, JobOutcome.SUCCEEDED)
        val newer = job(t2, JobOutcome.SUCCEEDED)

        startup(RecordingReindex(), startRebuild = false).run().block()

        assertThat(state.status().coveringJob).isEqualTo(newer.key)
        assertThat(errors()).isEmpty()
    }
}
