package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.composite.command.publication.PublicationBookkeeping
import com.demo.chat.service.composite.command.publication.RoomPublications
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class RoomPublicationsTests {
    private val room = 100L
    private val pubsub = MemoryTopicPubSubService<Long, String>().also { it.open(room).block() }
    private fun message(id: Long) = Message.create(SimpleMessageKey(id, 9L, 10L, room), "m$id", true)

    @Test
    fun `a committed publication enters the replay of a later listener`() {
        val publications = RoomPublications(pubsub)
        publications.publish("c-1", message(1L)).block()
        val live = publications.subscribe(room).block()!!
        assertThat(live.replay.map { it.key.id }).containsExactly(1L)
        live.close()
    }

    @Test
    fun `a failed emit leaves the log and the marker unchanged`() {
        val publications = RoomPublications(pubsub)
        val closedRoom = Message.create(SimpleMessageKey(2L, 9L, 10L, 404L), "lost", true)
        assertThatThrownBy { publications.publish("c-2", closedRoom).block() }
        assertThat(publications.publicationCount(404L)).isZero()
        assertThat(publications.isPublished(404L, "c-2")).isFalse()
    }

    @Test
    fun `case 14 - a repeated publication of one command emits once`() {
        val publications = RoomPublications(pubsub)
        val heard = AtomicInteger()
        pubsub.listenTo(room).subscribe { heard.incrementAndGet() }
        publications.publish("c-3", message(3L)).block()
        publications.publish("c-3", message(3L)).block()
        CommandFixtures.waitUntil { heard.get() >= 1 }
        Thread.sleep(200)
        assertThat(heard.get()).isEqualTo(1)
        assertThat(publications.publicationCount(room)).isEqualTo(1)
    }

    @Test
    fun `case 31 - the record and the marker commit together after OK`() {
        val publications = RoomPublications(pubsub)
        publications.publish("c-4", message(4L)).block()
        assertThat(publications.isPublished(room, "c-4")).isTrue()
        assertThat(publications.publicationCount(room)).isEqualTo(1)
    }

    @Test
    fun `case 31 - a bookkeeping failure after OK is uncertain, and a repeat commits both`() {
        val failOnce = AtomicBoolean(true)
        val bookkeeping = object : PublicationBookkeeping {
            override fun beforeCommit() {
                if (failOnce.getAndSet(false)) throw IllegalStateException("injected bookkeeping failure")
            }
        }
        val publications = RoomPublications(pubsub, bookkeeping = bookkeeping)
        val heard = AtomicInteger()
        pubsub.listenTo(room).subscribe { heard.incrementAndGet() }

        StepVerifier.create(publications.publish("c-5", message(5L)))
            .expectError(UncertainOutcomeException::class.java)
            .verify(Duration.ofSeconds(5))
        assertThat(publications.isPublished(room, "c-5")).isFalse()
        assertThat(publications.publicationCount(room)).isZero()

        publications.publish("c-5", message(5L)).block()
        assertThat(publications.isPublished(room, "c-5")).isTrue()
        assertThat(publications.publicationCount(room)).isEqualTo(1)
        CommandFixtures.waitUntil { heard.get() == 2 }
    }

    @Test
    fun `review 5 - the records and the markers of a room always agree`() {
        val flaky = AtomicInteger()
        val bookkeeping = object : PublicationBookkeeping {
            override fun beforeCommit() {
                if (flaky.incrementAndGet() % 3 == 0) throw IllegalStateException("injected bookkeeping failure")
            }
        }
        val publications = RoomPublications(pubsub, bookkeeping = bookkeeping)
        val stop = AtomicBoolean(false)
        val disagreements = AtomicInteger()
        val reader = Thread {
            while (!stop.get()) {
                val state = publications.snapshot(room)
                if (state.records.map { it.commandId }.toSet() != state.commandIds) disagreements.incrementAndGet()
            }
        }.also { it.start() }
        (1L..60L).forEach { n -> runCatching { publications.publish("c-agree-$n", message(100L + n)).block() } }
        stop.set(true)
        reader.join(5000)
        assertThat(disagreements.get()).isZero()
        assertThat(publications.publicationCount(room)).isEqualTo(40)
    }

    @Test
    fun `case 32 - a listener created inside another listener's callback gets the message once and nothing deadlocks`() {
        val publications = RoomPublications(pubsub)
        val first = publications.subscribe(room).block()!!
        val nested = CompletableFuture<List<Long>>()
        first.messages.subscribe {
            val inner = publications.subscribe(room).block(Duration.ofSeconds(2))!!
            nested.complete(inner.replay.map { m -> m.key.id })
            inner.close()
        }
        publications.publish("c-6", message(6L)).block(Duration.ofSeconds(5))
        assertThat(nested.get(5, TimeUnit.SECONDS)).containsExactly(6L)
        first.close()
    }
}
