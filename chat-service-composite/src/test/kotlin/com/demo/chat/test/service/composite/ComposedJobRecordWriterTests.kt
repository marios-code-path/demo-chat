package com.demo.chat.test.service.composite

import com.demo.chat.domain.JobRecord
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.vector.JobRecordCodec
import com.demo.chat.service.vector.JobRecordWriter
import com.demo.chat.service.composite.impl.ComposedJobRecordWriter
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant

class ComposedJobRecordWriterTests {

    private val calls = mutableListOf<String>()
    private val persistence = FakeMessagePersistence(calls)
    private val pubsub = FakePubSub(calls)

    private val record = JobRecord(
        key = Key.funKey(7L),
        jobKey = Key.funKey(500L),
        workerKey = Key.funKey(1000L),
        at = Instant.parse("2026-09-11T12:00:00Z"),
        message = "rebuild started",
    )

    private fun writerUnderTest(failOn: String? = null): JobRecordWriter<Long> =
        ComposedJobRecordWriter(
            messagePersistence = persistence,
            messageIndex = FakeMessageIndex(calls, failOn),
            pubsub = pubsub,
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )

    // The exact sequence is the boundary. A writer that called the composite
    // send would show four steps, and the vector indexer would be one of them.
    @Test
    fun `the writer calls persistence, then the index, then pub sub`() {
        val writer = writerUnderTest()

        StepVerifier.create(writer.write(record)).verifyComplete()

        Assertions.assertThat(calls).containsExactly("persistence", "index", "pubsub")
    }

    @Test
    fun `a message carries the record id and the job topic`() {
        val writer = writerUnderTest()

        writer.write(record).block()

        val message = persistence.added.single()
        Assertions.assertThat(message.key.id).isEqualTo(record.key.id)
        Assertions.assertThat(message.key.dest).isEqualTo(record.jobKey.id)
        Assertions.assertThat(message.record).isTrue()
        Assertions.assertThat(message.data).contains("\"version\"")
    }

    // Subscription order is not execution order. A chain that starts every step
    // at once subscribes them in the same sequence, so a synchronous double
    // reports the same call list either way. A slow first step separates them.
    @Test
    fun `a slow first step still completes before the second starts`() {
        val slowCalls = mutableListOf<String>()
        val slowPersistence = FakeMessagePersistence(slowCalls, Duration.ofMillis(60))
        val writer = ComposedJobRecordWriter(
            messagePersistence = slowPersistence,
            messageIndex = FakeMessageIndex(slowCalls),
            pubsub = FakePubSub(slowCalls),
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )

        StepVerifier.create(writer.write(record)).verifyComplete()

        Assertions.assertThat(slowCalls).containsExactly("persistence", "index", "pubsub")
    }

    // The spec says any failed step stops the steps after it. The index case
    // alone would leave the first step unproven.
    @Test
    fun `a failed persistence write stops the index and pub sub`() {
        val failedCalls = mutableListOf<String>()
        val index = FakeMessageIndex(failedCalls)
        val sink = FakePubSub(failedCalls)
        val writer = ComposedJobRecordWriter(
            messagePersistence = FakeMessagePersistence(
                failedCalls,
                failure = IllegalStateException("persistence is down"),
            ),
            messageIndex = index,
            pubsub = sink,
            codec = JobRecordCodec(ObjectMapper().findAndRegisterModules()),
            asValue = { text -> text },
        )

        StepVerifier
            .create(writer.write(record))
            .verifyErrorSatisfies { error ->
                Assertions.assertThat(error).hasMessage("persistence is down")
            }

        Assertions.assertThat(index.added).isEmpty()
        Assertions.assertThat(sink.sent).isEmpty()
        Assertions.assertThat(failedCalls).containsExactly("persistence")
    }

    @Test
    fun `a failed index write stops pub sub and keeps the persisted record`() {
        val writer = writerUnderTest(failOn = "index")

        StepVerifier.create(writer.write(record)).verifyError(IllegalStateException::class.java)

        Assertions.assertThat(persistence.added).hasSize(1)
        Assertions.assertThat(pubsub.sent).isEmpty()
    }
}
