package com.demo.chat.test.service.composite

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.composite.impl.InMemoryVectorIndexState
import com.demo.chat.service.composite.impl.MessageReindexServiceImpl
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.vector.MessageReindexService
import com.demo.chat.service.vector.MessageVectorIndexer
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorRebuildReport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Clock
import java.time.Duration
import java.time.Instant

class MessageReindexServiceImplTests {
    private val startedAt = Instant.parse("2026-09-10T12:00:00Z")
    private val finishedAt = Instant.parse("2026-09-10T12:00:01Z")
    private val persistence = mock<MessagePersistence<Long, String>>()
    private val indexer = RecordingIndexer()
    private val state = InMemoryVectorIndexState<Long>()
    private val clock = mock<Clock>()
    private lateinit var scheduler: Scheduler
    private lateinit var service: MessageReindexServiceImpl<Long, String>

    @BeforeEach
    fun configureService() {
        given(clock.instant()).willReturn(startedAt, finishedAt)
        scheduler = Schedulers.newSingle("reindex-test")
        service = MessageReindexServiceImpl(
            persistence,
            indexer,
            state,
            clock,
            scheduler,
        )
    }

    @AfterEach
    fun closeScheduler() {
        scheduler.dispose()
    }

    @Test
    fun `rebuild indexes recorded messages and skips alerts`() {
        given(persistence.all()).willReturn(
            Flux.just(
                message(1L, "apple", true),
                message(2L, "joined", false),
                message(3L, "banana", true),
            )
        )

        val final = runAndAwait(service)

        Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
        Assertions.assertThat(final.complete).isTrue()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 3L, 2L, 1L, 0L)
        )
    }

    @Test
    fun `one message failure does not stop later messages`() {
        indexer.failOn.add(2L)
        given(persistence.all()).willReturn(
            Flux.just(message(1L), message(2L), message(3L))
        )

        val final = runAndAwait(service)

        Assertions.assertThat(indexer.ids).containsExactly(1L, 3L)
        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport!!.failed).isEqualTo(1L)
        Assertions.assertThat(final.lastFailure).contains("vector failure for 2")
    }

    @Test
    fun `persistence scan failure marks incomplete`() {
        given(persistence.all()).willReturn(
            Flux.concat(
                Flux.just(message(1L)),
                Flux.error(IllegalStateException("scan failed")),
            )
        )

        val final = runAndAwait(service)

        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 1L, 1L, 0L, 0L)
        )
        Assertions.assertThat(final.lastFailure).contains("scan failed")
    }

    @Test
    fun `synchronous persistence failure marks incomplete`() {
        given(persistence.all()).willThrow(IllegalStateException("scan assembly failed"))

        val final = runAndAwait(service)

        Assertions.assertThat(final.complete).isFalse()
        Assertions.assertThat(final.lastReport).isEqualTo(
            VectorRebuildReport(startedAt, finishedAt, 0L, 0L, 0L, 0L)
        )
        Assertions.assertThat(final.lastFailure).contains("scan assembly failed")
    }

    @Test
    fun `second start returns busy and starts no second scan`() {
        val release = Sinks.empty<Void>()
        given(persistence.all()).willReturn(
            Flux.just(message(1L))
                .concatWith(release.asMono().thenMany(Flux.empty()))
        )

        val first = service.start().block()!!
        val second = service.start().block()!!

        Assertions.assertThat(first.running).isTrue()
        Assertions.assertThat(second.running).isTrue()
        verify(persistence, timeout(1_000).times(1)).all()

        release.tryEmitEmpty()
        Assertions.assertThat(awaitFinished(service).complete).isTrue()
    }

    @Test
    fun `a job that cannot start releases the claim`() {
        given(persistence.all()).willReturn(Flux.just(message(1L)))
        scheduler.dispose()

        service.start().block()!!

        val released = awaitFinished(service)
        Assertions.assertThat(released.running).isFalse()
        Assertions.assertThat(released.complete).isFalse()
        Assertions.assertThat(released.lastFailure).isNotNull()
        Assertions.assertThat(service.start().block()!!.running).isTrue()
    }

    private fun runAndAwait(service: MessageReindexService<Long>): VectorIndexStatus {
        Assertions.assertThat(service.start().block()!!.running).isTrue()
        return awaitFinished(service)
    }

    private fun awaitFinished(service: MessageReindexService<Long>): VectorIndexStatus =
        Flux.interval(Duration.ZERO, Duration.ofMillis(10))
            .map { service.status() }
            .filter { !it.running }
            .next()
            .block(Duration.ofSeconds(10))!!

    private fun message(
        id: Long,
        text: String = "message $id",
        record: Boolean = true,
    ): Message<Long, String> =
        Message.create(MessageKey.create(id, 10L, 20L), text, record)

    private class RecordingIndexer : MessageVectorIndexer<Long> {
        val ids = mutableListOf<Long>()
        val failOn = mutableSetOf<Long>()

        override fun add(message: Message<Long, String>): Mono<Void> = Mono.defer {
            val id = message.key.id
            if (failOn.contains(id)) {
                Mono.error(IllegalStateException("vector failure for $id"))
            } else {
                ids.add(id)
                Mono.empty()
            }
        }

        override fun remove(key: Key<Long>): Mono<Void> = Mono.empty()
    }
}
