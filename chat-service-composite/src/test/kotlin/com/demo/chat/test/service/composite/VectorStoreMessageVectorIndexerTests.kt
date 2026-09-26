package com.demo.chat.test.service.composite

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.IndexJob
import com.demo.chat.domain.JobOutcome
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.VectorWriteMode
import com.demo.chat.test.vector.MockVectorStore
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.ai.vectorstore.SearchRequest
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
    private val coveringKey = TestKeys.key(500L)
    private val vectorStore = MockVectorStore()
    private val jobStore = FakeVectorIndexJobStore()
    private val state = InMemoryVectorIndexState<Long>()
    private val message = Message.create(TestKeys.message(1L, 10L, 100L), "apple", true)

    private fun indexer(writeMode: VectorWriteMode = VectorWriteMode.DELETE_THEN_ADD) =
        VectorStoreMessageVectorIndexer(
            vectorStore = vectorStore,
            mapper = MessageDocumentMapper(LongUtil(), "long"),
            state = state,
            jobStore = jobStore,
            writeMode = writeMode,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    /**
     * A mapper that fails inside toDocument and not inside documentId.
     *
     * It throws for the topic value alone. documentId converts the message id,
     * so it still answers, and only the wider mapping breaks. A double that
     * threw for every value would fail in both methods, and the order of the
     * mapping and the removal would then make no difference to the test.
     */
    private fun failingMapperIndexer() =
        VectorStoreMessageVectorIndexer(
            vectorStore = vectorStore,
            mapper = MessageDocumentMapper(
                object : TypeUtil<Long> by LongUtil() {
                    override fun toString(t: Long): String =
                        if (t == 100L) {
                            throw IllegalStateException("the mapper is broken")
                        } else {
                            t.toString()
                        }
                },
                "long",
            ),
            state = state,
            jobStore = jobStore,
            writeMode = VectorWriteMode.DELETE_THEN_ADD,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun coveringJob(): IndexJob<Long> = IndexJob(
        key = coveringKey,
        topicKey = TestKeys.key((coveringKey).id + 1_000_000L),
        nodeId = 7,
        keyType = "long",
        incarnationId = "incarnation-a",
        startedBy = TestKeys.key(1000L),
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

    // The document id comes from the message id, so a rebuild meets an id it
    // already wrote. The write must replace, not refuse.
    @Test
    fun `a first add succeeds when the store holds no document`() {
        vectorStore.rejectDuplicateId = true
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)

        indexer().add(message).block()

        Assertions.assertThat(vectorStore.ids).containsExactly("message:long:1")
        Assertions.assertThat(state.coveringJob()).isEqualTo(coveringKey)
    }

    @Test
    fun `the removal runs before the write`() {
        indexer().add(message).block()

        Assertions.assertThat(vectorStore.calls).containsExactly("delete", "add")
    }

    // The store that refuses a repeat is the one this repair exists for. A
    // double that overwrites would pass with no replacement at all.
    @Test
    fun `a repeated add replaces the document instead of failing`() {
        vectorStore.rejectDuplicateId = true
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)

        indexer().add(message).block()
        indexer().add(Message.create(TestKeys.message(1L, 10L, 100L), "pear", true)).block()

        Assertions.assertThat(vectorStore.ids).containsExactly("message:long:1")
        Assertions.assertThat(state.coveringJob()).isEqualTo(coveringKey)
    }

    @Test
    fun `a repeated add stores the new content`() {
        vectorStore.rejectDuplicateId = true

        indexer().add(message).block()
        indexer().add(Message.create(TestKeys.message(1L, 10L, 100L), "pear", true)).block()

        val hits = vectorStore.similaritySearch(
            SearchRequest.builder().query("pear").topK(1).similarityThresholdAll().build()
        )
        Assertions.assertThat(hits.single().text).isEqualTo("pear")
    }

    // The mapping runs before the removal, so a mapping failure leaves the
    // stored document in place.
    @Test
    fun `a mapping failure keeps the prior document`() {
        indexer().add(message).block()
        state.adoptCoveringJob(coveringKey)

        StepVerifier
            .create(failingMapperIndexer().add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(vectorStore.ids).containsExactly("message:long:1")
        Assertions.assertThat(vectorStore.calls).containsExactly("delete", "add")
    }

    // A removal failure stops the write and returns the removal error. It
    // promises nothing about the old document, because a provider can remove
    // the document and then fail while it commits. This double keeps the
    // document, and the test asserts only the two guarantees.
    @Test
    fun `a removal failure stops the write`() {
        indexer().add(message).block()
        vectorStore.calls.clear()
        vectorStore.failDelete = true

        StepVerifier
            .create(indexer().add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(vectorStore.calls).containsExactly("delete")
    }

    // The other removal failure. This provider drops the document and then
    // throws, which is the case the earlier comment wrongly ruled out. The
    // write still must not run, and the caller still must see the error.
    @Test
    fun `a removal that drops the document and then fails stops the write`() {
        indexer().add(message).block()
        vectorStore.calls.clear()
        vectorStore.dropThenFailDelete = true
        state.adoptCoveringJob(coveringKey)

        StepVerifier
            .create(indexer().add(message))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error).hasMessage("vector store cannot delete")
            }

        Assertions.assertThat(vectorStore.calls).containsExactly("delete")
        Assertions.assertThat(vectorStore.ids).isEmpty()
        // The guarantee that does hold. The index lost the document, so the
        // coverage goes with it.
        Assertions.assertThat(state.coveringJob()).isNull()
    }

    // The upsert mode writes once. A removal before it would be wasted work on
    // the simple provider, and redis logs an error when a delete removes
    // nothing.
    @Test
    fun `the upsert mode writes without a removal`() {
        indexer(VectorWriteMode.UPSERT).add(message).block()

        Assertions.assertThat(vectorStore.calls).containsExactly("add")
        Assertions.assertThat(vectorStore.ids).containsExactly("message:long:1")
    }

    @Test
    fun `the upsert mode overwrites a repeated id`() {
        indexer(VectorWriteMode.UPSERT).add(message).block()
        indexer(VectorWriteMode.UPSERT)
            .add(Message.create(TestKeys.message(1L, 10L, 100L), "pear", true))
            .block()

        Assertions.assertThat(vectorStore.calls).containsExactly("add", "add")
        Assertions.assertThat(vectorStore.ids).containsExactly("message:long:1")
    }

    @Test
    fun `a write failure inside a replacement removes coverage`() {
        jobStore.write(coveringJob()).block()
        state.adoptCoveringJob(coveringKey)
        vectorStore.failNextAdd = true

        StepVerifier
            .create(indexer().add(message))
            .verifyError(IllegalStateException::class.java)

        Assertions.assertThat(state.coveringJob()).isNull()
        Assertions.assertThat(jobStore.jobs[coveringKey.id]!!.invalidationCount).isEqualTo(1L)
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
