package com.demo.chat.test.service.composite

import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorRebuildReport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

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
        Assertions.assertThat(status.lastSuccessCount).isEqualTo(2L)
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
