package com.demo.chat.test.service.composite

import com.demo.chat.domain.Key
import com.demo.chat.service.vector.VectorFinishResult
import com.demo.chat.service.vector.VectorInvalidation
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorRebuildReport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryVectorIndexStateTests {
    private val startedAt = Instant.parse("2026-09-10T12:00:00Z")
    private val finishedAt = Instant.parse("2026-09-10T12:00:01Z")

    @Test
    fun `fresh state is incomplete and idle`() {
        val status = InMemoryVectorIndexState<Long>().status()

        Assertions.assertThat(status.phase).isEqualTo(VectorIndexPhase.INCOMPLETE)
        Assertions.assertThat(status.complete).isFalse()
        Assertions.assertThat(status.running).isFalse()
        Assertions.assertThat(status.lastReport).isNull()
    }

    @Test
    fun `only one rebuild claim is accepted`() {
        val state = InMemoryVectorIndexState<Long>()

        val first = state.claim()
        val second = state.claim()

        Assertions.assertThat(first.accepted).isTrue()
        Assertions.assertThat(first.status.running).isTrue()
        Assertions.assertThat(second.accepted).isFalse()
        Assertions.assertThat(second.generation).isEqualTo(first.generation)
    }

    @Test
    fun `zero failures and unchanged generation mark complete`() {
        val state = InMemoryVectorIndexState<Long>()
        val claim = state.claim()
        val report = VectorRebuildReport(startedAt, finishedAt, 2L, 2L, 0L, 0L)

        val result = state.finish(claim, report, null, Key.funKey(11L))

        Assertions.assertThat(result.succeeded).isTrue()
        Assertions.assertThat(result.status.complete).isTrue()
        Assertions.assertThat(result.status.lastSuccessAt).isEqualTo(finishedAt)
        Assertions.assertThat(result.status.lastSuccessCount).isEqualTo(2L)
    }

    @Test
    fun `live invalidation prevents a false complete state`() {
        val state = InMemoryVectorIndexState<Long>()
        val claim = state.claim()
        state.invalidate("live vector add failed")
        val report = VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L)

        val result = state.finish(claim, report, null, Key.funKey(11L))

        Assertions.assertThat(result.succeeded).isFalse()
        Assertions.assertThat(result.status.phase).isEqualTo(VectorIndexPhase.INCOMPLETE)
        Assertions.assertThat(result.status.lastFailure).isEqualTo("live vector add failed")
    }

    @Test
    fun `failed rebuild preserves the last successful values`() {
        val state = InMemoryVectorIndexState<Long>()
        val first = state.claim()
        state.finish(
            first,
            VectorRebuildReport(startedAt, finishedAt, 2L, 2L, 0L, 0L),
            null,
            Key.funKey(11L),
        )
        val second = state.claim()
        val failedAt = finishedAt.plusSeconds(1)

        val result = state.finish(
            second,
            VectorRebuildReport(finishedAt, failedAt, 2L, 1L, 0L, 1L),
            "IllegalStateException: vector down",
            Key.funKey(12L),
        )

        Assertions.assertThat(result.succeeded).isFalse()
        Assertions.assertThat(result.status.complete).isFalse()
        Assertions.assertThat(result.status.lastReport!!.failed).isEqualTo(1L)
        Assertions.assertThat(result.status.lastSuccessAt).isEqualTo(finishedAt)
        Assertions.assertThat(result.status.lastSuccessCount).isEqualTo(2L)
    }

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
}
