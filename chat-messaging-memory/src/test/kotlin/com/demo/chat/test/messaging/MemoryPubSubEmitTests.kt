package com.demo.chat.test.messaging

import com.demo.chat.domain.Message
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.pubsub.memory.impl.PublicationRetryableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.reactivestreams.Subscription
import reactor.core.Exceptions
import reactor.core.publisher.BaseSubscriber
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MemoryPubSubEmitTests {
    private val pubsub = MemoryTopicPubSubService<Long, String>()
    private fun message(id: Long, room: Long = 1L) = Message.create(SimpleMessageKey(id, 9L, 10L, room), "m$id", true)

    @AfterEach
    fun shutdown() = pubsub.close()

    @Test
    fun `a subscriber callback runs off the emitting thread`() {
        pubsub.open(1L).block()
        val callbackThread = CompletableFuture<String>()
        pubsub.listenTo(1L).subscribe { callbackThread.complete(Thread.currentThread().name) }
        val emitter = Executors.newSingleThreadExecutor { Thread(it, "emitter-thread") }
        emitter.submit { pubsub.sendMessage(message(1L)).block() }.get(5, TimeUnit.SECONDS)
        assertThat(callbackThread.get(5, TimeUnit.SECONDS)).isNotEqualTo("emitter-thread")
        emitter.shutdownNow()
    }

    @Test
    fun `a send to a room that is not open fails with not found`() {
        StepVerifier.create(pubsub.sendMessage(message(2L, room = 404L))).expectError(NotFoundException::class.java).verify()
    }

    @Test
    fun `a listener that reconnects after the last listener left receives new messages`() {
        pubsub.open(5L).block()
        val first = pubsub.listenTo(5L).map { it.key.id }.next().toFuture()
        pubsub.sendMessage(message(51L, room = 5L)).block()
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(51L)

        val second = pubsub.listenTo(5L).map { it.key.id }.next().toFuture()
        Thread.sleep(100)
        StepVerifier.create(pubsub.sendMessage(message(52L, room = 5L))).verifyComplete()
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(52L)
    }

    @Test
    fun `an empty room accepts more than 256 messages, and a later listener receives only new ones`() {
        pubsub.open(6L).block()
        (1L..1000L).forEach { id ->
            StepVerifier.create(pubsub.sendMessage(message(id, room = 6L))).verifyComplete()
        }
        val later = pubsub.listenTo(6L).map { it.key.id }.next().toFuture()
        Thread.sleep(100)
        pubsub.sendMessage(message(1001L, room = 6L)).block()
        assertThat(later.get(5, TimeUnit.SECONDS)).isEqualTo(1001L)
    }

    @Test
    fun `a slow external listener overflows the room with a retryable error`() {
        pubsub.open(3L).block()
        pubsub.listenTo(3L).subscribe(object : BaseSubscriber<Message<Long, String>>() {
            override fun hookOnSubscribe(subscription: Subscription) = Unit
        })
        val errors = (1L..2000L).mapNotNull { id ->
            runCatching { pubsub.sendMessage(message(id, room = 3L)).block() }.exceptionOrNull()?.let(Exceptions::unwrap)
        }
        assertThat(errors).isNotEmpty
        assertThat(errors.first()).isInstanceOf(PublicationRetryableException::class.java)
        assertThat(errors.first().message).contains("FAIL_OVERFLOW")
    }

    @Test
    fun `closing a room releases its internal subscriber, and shutdown releases every room`() {
        pubsub.open(7L).block()
        pubsub.open(8L).block()
        assertThat(pubsub.hasRoomSink(7L)).isTrue()
        val drain = pubsub.drainOf(7L)!!

        pubsub.close(7L).block(Duration.ofSeconds(5))
        assertThat(pubsub.hasRoomSink(7L)).isFalse()
        assertThat(drain.isDisposed).isTrue()
        StepVerifier.create(pubsub.sendMessage(message(71L, room = 7L))).expectError(NotFoundException::class.java).verify()

        pubsub.close()
        assertThat(pubsub.roomSinkCount()).isZero()
    }
}
