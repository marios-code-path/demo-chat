package com.demo.chat.test.service.composite

import com.demo.chat.domain.EmbeddingIdentity
import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.service.composite.impl.VectorCoveragePolicyImpl
import com.demo.chat.service.vector.JobTopicNames
import com.demo.chat.service.vector.VectorTrust
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Instant

class VectorCoveragePolicyImplTests {
    private val thisIncarnation = "incarnation-a"
    private val otherIncarnation = "incarnation-b"
    private val start = Instant.parse("2026-09-11T12:00:00Z")
    private val thisIdentity = EmbeddingIdentity("acme-e5")

    private val store = FakeVectorIndexJobStore()

    private fun job(
        id: Long,
        outcome: JobOutcome = JobOutcome.SUCCEEDED,
        incarnationId: String = thisIncarnation,
        invalidations: Long = 0L,
        startedAt: Instant = start,
        embeddingIdentity: String? = thisIdentity.value,
    ): IndexJob<Long> = IndexJob(
        key = Key.funKey(id),
        nodeId = 7,
        keyType = "long",
        embeddingIdentity = embeddingIdentity,
        incarnationId = incarnationId,
        startedBy = Key.funKey(1000L),
        startedAt = startedAt,
        outcome = outcome,
        invalidationCount = invalidations,
    )

    private fun policy(trust: VectorTrust) =
        VectorCoveragePolicyImpl(
            store,
            trust,
            thisIncarnation,
            nodeId = 7,
            keyType = "long",
            embeddingIdentity = thisIdentity,
            typeUtil = LongUtil(),
        )

    @Test
    fun `a job of another identity does not cover`() {
        store.write(job(1L, embeddingIdentity = "other-model")).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a job written before this change does not cover`() {
        // A legacy record carries null. Null names no model, and the vectors
        // it wrote came from a model that has no name.
        store.write(job(1L, embeddingIdentity = null)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    @Test
    fun `a newer job of a foreign identity does not hide a current covering job`() {
        // The identity filter runs before the sort and before next(). A newer
        // foreign job would otherwise reach next() first. The policy would
        // select it, reject it, and report no coverage. The valid older job
        // would then be hidden, and the index would rebuild for no reason.
        store.write(job(1L, startedAt = start)).block()
        store.write(
            job(2L, startedAt = start.plusSeconds(60), embeddingIdentity = "other-model")
        ).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }

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
    fun `another node's job is never read`() {
        store.write(job(1L).copy(nodeId = 8)).block()

        StepVerifier
            .create(policy(VectorTrust.STORED).selectCoveringJob())
            .verifyComplete()

        // No coverage is not enough. The name decides before the read, so a
        // foreign job is never read at all, and a policy that filtered after
        // the read would record this key.
        Assertions.assertThat(store.readKeys).isEmpty()
    }

    // A mismatch fails the read. Dropping the job would let the older clean
    // job below it cover the index, which is the opposite of fail closed.
    @Test
    fun `a record that disagrees with its topic name reports no covering job`() {
        store.write(job(1L, startedAt = start)).block()
        store.write(job(2L, startedAt = start.plusSeconds(60)).copy(keyType = "uuid")).block()
        // The topic says this node and key type, and the record says another.
        // Without the override the name would say uuid too, the topic filter
        // would drop it, and this would test exclusion rather than a mismatch.
        store.names[2L] = JobTopicNames.nameFor(7, "long", start.plusSeconds(60), thisIncarnation)

        StepVerifier
            .create(policy(VectorTrust.STORED).selectCoveringJob())
            .verifyComplete()
    }

    // A repair rebuild keeps prior coverage while it runs. The running job is
    // the newest job, so the policy must drop it before the sort. A policy
    // that dropped it after the sort would select the repair, find that it
    // does not cover, and report no coverage during every repair.
    @Test
    fun `a running repair keeps the coverage of the older successful job`() {
        store.write(job(1L, startedAt = start)).block()
        store.write(job(2L, outcome = JobOutcome.RUNNING, startedAt = start.plusSeconds(60))).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }

    // A failed repair keeps prior coverage when no invalidation happened. The
    // failure removed no document, so the older job still describes the index.
    @Test
    fun `a failed repair keeps the coverage of the older successful job`() {
        store.write(job(1L, startedAt = start)).block()
        store.write(job(2L, outcome = JobOutcome.FAILED, startedAt = start.plusSeconds(60))).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .assertNext { found -> Assertions.assertThat(found.key.id).isEqualTo(1L) }
            .verifyComplete()
    }

    // Equal timestamps must not leave the choice to whichever read finished
    // first. The root key decides, and the higher one wins.
    @Test
    fun `two jobs of one instant order by root key`() {
        store.write(job(1L, startedAt = start)).block()
        store.write(job(2L, startedAt = start, invalidations = 1L)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
            .verifyComplete()
    }

    // Keys 9 and 10 separate a number order from a text order. As text, "9"
    // sorts above "10", so a text order would select job 9 and report
    // coverage. As a number, job 10 wins and it does not cover.
    @Test
    fun `an equal instant orders root keys as numbers, not as text`() {
        store.write(job(9L, startedAt = start)).block()
        store.write(job(10L, startedAt = start, invalidations = 1L)).block()

        StepVerifier
            .create(policy(VectorTrust.NONE).selectCoveringJob())
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
