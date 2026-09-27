package com.demo.chat.test.vector

import com.demo.chat.test.key.TestKeys

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
            key = TestKeys.key(1L),
            topicKey = TestKeys.key(900900L),
            nodeId = 7,
            keyType = "long",
            incarnationId = "abc",
            startedBy = TestKeys.key(2L),
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
            key = TestKeys.key(1L),
            topicKey = TestKeys.key(900900L),
            nodeId = 7,
            keyType = "long",
            incarnationId = "abc",
            startedBy = TestKeys.key(2L),
            startedAt = start,
            outcome = JobOutcome.SUCCEEDED,
        )

        Assertions.assertThat(job.covers).isTrue()
        Assertions.assertThat(job.copy(invalidationCount = 1L).covers).isFalse()
        Assertions.assertThat(job.copy(outcome = JobOutcome.FAILED).covers).isFalse()
    }
}
