package com.demo.chat.test.service.composite

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.VectorIndexStartupAction
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.VectorCoveragePolicy
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * The startup order, and the best-effort rule of the release sweep.
 */
class VectorIndexStartupActionTests {
    private val thisIncarnation = "incarnation-b"
    private val earlierIncarnation = "incarnation-a"
    private val start = Instant.parse("2026-09-12T12:00:00Z")

    private val store = FakeVectorIndexJobStore()
    private val state = InMemoryVectorIndexState<Long>()

    /** Every step this run performed, in the order it performed them. */
    private val calls = mutableListOf<String>()

    private fun job(
        id: Long,
        outcome: JobOutcome = JobOutcome.SUCCEEDED,
        incarnationId: String = earlierIncarnation,
        invalidations: Long = 0L,
        nodeId: Int = 7,
        keyType: String = "long",
    ): IndexJob<Long> = IndexJob(
        key = TestKeys.key(id),
        topicKey = TestKeys.key((id) + 1_000_000L),
        nodeId = nodeId,
        keyType = keyType,
        incarnationId = incarnationId,
        startedBy = TestKeys.key(1000L),
        startedAt = start,
        outcome = outcome,
        invalidationCount = invalidations,
    )

    /** Answers [covering] after [delay], and records when it ran. */
    private inner class RecordingPolicy(
        private val covering: IndexJob<Long>?,
        private val delay: Duration = Duration.ZERO,
    ) : VectorCoveragePolicy<Long> {
        override fun selectCoveringJob(): Mono<IndexJob<Long>> =
            Mono.delay(delay)
                .doOnNext { calls.add("select") }
                .then(Mono.justOrEmpty(covering))
    }

    /** Records the coverage the state held at the moment the rebuild started. */
    private inner class RecordingReindex : MessageReindexService<Long> {
        val coveringAtStart = mutableListOf<Key<Long>?>()

        override fun start(): Mono<VectorIndexTriggerResult<Long>> = Mono.fromSupplier {
            calls.add("rebuild")
            coveringAtStart.add(state.coveringJob())
            // The startup action discards this answer through then(). The fake
            // reports an accepted trigger, which is what a real start reports
            // when no other run holds the claim.
            VectorIndexTriggerResult(true, state.status())
        }

        override fun status(): VectorIndexStatus<Long> = state.status()
    }

    private fun action(
        policy: VectorCoveragePolicy<Long>,
        reindex: MessageReindexService<Long> = RecordingReindex(),
        startRebuild: Boolean = false,
    ) = VectorIndexStartupAction(
        jobStore = store,
        policy = policy,
        state = state,
        reindex = reindex,
        nodeId = 7,
        keyType = "long",
        incarnationId = thisIncarnation,
        startRebuild = startRebuild,
    )

    @Test
    fun `a stale running job of an earlier incarnation becomes released`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING)).block()

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RELEASED)
    }

    // The release must not lower a durable invalidation the crashed process
    // recorded. finishJob holds that rule, and a plain write would not.
    @Test
    fun `a release keeps the stored invalidation count`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING, invalidations = 2L)).block()

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.invalidationCount).isEqualTo(2L)
    }

    @Test
    fun `a running job of this incarnation stays running`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING, incarnationId = thisIncarnation)).block()

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
    }

    @Test
    fun `a finished job is never released`() {
        store.write(job(1L)).block()
        store.write(job(2L, outcome = JobOutcome.FAILED)).block()

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.SUCCEEDED)
        Assertions.assertThat(store.jobs[2L]!!.outcome).isEqualTo(JobOutcome.FAILED)
    }

    // Another node's job belongs to another process. This sweep must not touch
    // it, because that process can still be running the job.
    @Test
    fun `another node's stale job is never released`() {
        val other = job(1L, outcome = JobOutcome.RUNNING)
        store.write(other).block()
        store.names[1L] = JobTopicNames.nameFor(9, "long", start, earlierIncarnation)

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
    }

    // The name and the record are two stored things. This test gives a local
    // topic a foreign record, which is the direction the topic filter alone
    // cannot catch. Another node's process can still be running that job.
    @Test
    fun `a local topic with another node's record stays unchanged`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING, nodeId = 9)).block()
        store.names[1L] = JobTopicNames.nameFor(7, "long", start, earlierIncarnation)

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
    }

    @Test
    fun `a local topic with another key type's record stays unchanged`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING, keyType = "uuid")).block()
        store.names[1L] = JobTopicNames.nameFor(7, "long", start, earlierIncarnation)

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
    }

    // A mismatch skips one job and startup continues. The sweep is best
    // effort, so a foreign record must not stop the rest of it.
    @Test
    fun `a mismatched record does not stop the sweep`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING, nodeId = 9)).block()
        store.names[1L] = JobTopicNames.nameFor(7, "long", start, earlierIncarnation)
        store.write(job(2L, outcome = JobOutcome.RUNNING)).block()

        action(RecordingPolicy(job(3L))).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
        Assertions.assertThat(store.jobs[2L]!!.outcome).isEqualTo(JobOutcome.RELEASED)
        Assertions.assertThat(state.coveringJob()).isEqualTo(TestKeys.key(3L))
    }

    // The sweep runs first. A released job must not reach the policy as a
    // running one.
    @Test
    fun `the sweep runs before coverage selection`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING)).block()
        store.onFinish = { calls.add("release") }

        action(RecordingPolicy(job(2L))).run().block()

        Assertions.assertThat(calls).containsExactly("release", "select")
    }

    // The two steps must not race. A rebuild that finished before a slow
    // coverage read would install its own job, and the late read would then
    // replace it with an older job.
    @Test
    fun `the rebuild starts only after coverage is adopted`() {
        val reindex = RecordingReindex()

        action(
            RecordingPolicy(job(1L), delay = Duration.ofMillis(200)),
            reindex = reindex,
            startRebuild = true,
        ).run().block()

        Assertions.assertThat(reindex.coveringAtStart.single()).isEqualTo(TestKeys.key(1L))
        Assertions.assertThat(calls).containsExactly("select", "rebuild")
    }

    @Test
    fun `the report action starts no rebuild`() {
        val reindex = RecordingReindex()

        action(RecordingPolicy(job(1L)), reindex = reindex).run().block()

        Assertions.assertThat(reindex.coveringAtStart).isEmpty()
        Assertions.assertThat(state.coveringJob()).isEqualTo(TestKeys.key(1L))
    }

    @Test
    fun `no covering job leaves the index incomplete`() {
        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(state.coveringJob()).isNull()
        Assertions.assertThat(state.status().phase).isEqualTo(VectorIndexPhase.INCOMPLETE)
    }

    // The sweep is best effort. A durable job is evidence and never a lock, so
    // a job left running blocks nothing, and startup must continue.
    @Test
    fun `a failed listing does not stop coverage selection`() {
        store.failListing = true

        action(RecordingPolicy(job(1L))).run().block()

        Assertions.assertThat(state.coveringJob()).isEqualTo(TestKeys.key(1L))
    }

    @Test
    fun `one failed release does not stop the sweep`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING)).block()
        store.write(job(2L, outcome = JobOutcome.RUNNING)).block()
        store.failFinishFor = 1L

        action(RecordingPolicy(null)).run().block()

        Assertions.assertThat(store.jobs[1L]!!.outcome).isEqualTo(JobOutcome.RUNNING)
        Assertions.assertThat(store.jobs[2L]!!.outcome).isEqualTo(JobOutcome.RELEASED)
    }

    @Test
    fun `a failed release still lets the rebuild start`() {
        store.write(job(1L, outcome = JobOutcome.RUNNING)).block()
        store.failFinishFor = 1L
        val reindex = RecordingReindex()

        action(RecordingPolicy(job(2L)), reindex = reindex, startRebuild = true).run().block()

        Assertions.assertThat(reindex.coveringAtStart.single()).isEqualTo(TestKeys.key(2L))
    }
}
