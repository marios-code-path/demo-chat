package com.demo.chat.test.service.composite

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The live add path. A failed add removes coverage in process, and it raises
 * the durable count on the covering job.
 */
class VectorStoreMessageVectorIndexerTests {
    private val now = Instant.parse("2026-09-11T12:00:00Z")
    private val coveringKey = Key.funKey(500L)
    private val vectorStore = MockVectorStore()
    private val jobStore = FakeVectorIndexJobStore()
    private val state = InMemoryVectorIndexState<Long>()
    private val message = Message.create(MessageKey.create(1L, 10L, 100L), "apple", true)

    private fun indexer() =
        VectorStoreMessageVectorIndexer(
            vectorStore = vectorStore,
            mapper = MessageDocumentMapper(LongUtil(), "long"),
            state = state,
            jobStore = jobStore,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun coveringJob(): IndexJob<Long> = IndexJob(
        key = coveringKey,
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = Key.funKey(1000L),
        startedAt = now,
        outcome = JobOutcome.SUCCEEDED,
    )

    @Test
    fun `a live add failure raises the durable count and removes coverage`() {
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer().add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(jobStore.jobs[coveringKey.id]!!.invalidationCount).isEqualTo(1L)
        Assertions.assertThat(jobStore.jobs[coveringKey.id]!!.lastInvalidationAt).isEqualTo(now)
        Assertions.assertThat(state.coveringJob()).isNull()
    }

    // The indexer records the failure and then returns the error. A caller must
    // still see the vector error, because the send chain decides what to do
    // with it.
    @Test
    fun `a live add failure returns the original error to the sender`() {
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer().add(message))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error)
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessage("vector store is down")
            }
    }

    // A failed durable write must not replace the vector error, and it must not
    // stop the send chain from seeing that error.
    @Test
    fun `a failed invalidation write keeps the original error`() {
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true
        jobStore.failWrites = true

        StepVerifier
            .create(indexer().add(message))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error).hasMessage("vector store is down")
            }
    }

    // No job covers, so there is no durable target. The process generation
    // still moves, and the read that follows reports an incomplete index.
    @Test
    fun `a failure with no covering job writes no durable count`() {
        jobStore.write(coveringJob()).block()
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer().add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(jobStore.jobs[coveringKey.id]!!.invalidationCount).isEqualTo(0L)
    }

    @Test
    fun `a successful add keeps the coverage and writes no count`() {
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)

        indexer().add(message).block()

        Assertions.assertThat(vectorStore.ids).hasSize(1)
        Assertions.assertThat(jobStore.jobs[coveringKey.id]!!.invalidationCount).isEqualTo(0L)
        Assertions.assertThat(state.coveringJob()).isEqualTo(coveringKey)
    }
}
