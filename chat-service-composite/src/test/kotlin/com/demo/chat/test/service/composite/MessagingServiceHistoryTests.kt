package com.demo.chat.test.service.composite

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Message
import com.demo.chat.test.service.composite.command.MessagingStack
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Persisted history through the composite service.
 *
 * The service reads the message index with the production converter, and it
 * then resolves those keys through persistence. A converter that names the
 * wrong field returns no history, and live delivery hides that, because
 * `listenTopic` concatenates the pub/sub stream after the history.
 *
 * `FakeMessageIndex` matches on the message destination field, which is what
 * both real backends do. So a converter that names another field finds nothing
 * here, exactly as it finds nothing on lucene and on cassandra.
 */
class MessagingServiceHistoryTests {
    /** The stack registers room 100 in MESSAGE_TOPIC. Room 999 is registered here. See CHAT-avduuqwp, D7. */
    private val stack = MessagingStack().apply { registry.register(999L, ChatDomain.MESSAGE_TOPIC) }
    private val pubsub = stack.pubsub
    private val service = stack.service

    @AfterEach
    fun close() = stack.close()

    private fun store(id: Long, topic: Long, text: String) {
        val message = Message.create(TestKeys.message(id, 10L, topic), text, true)
        stack.persistence.add(message).block()
        stack.index.add(message).block()
    }

    @Test
    fun `listenTopic emits the persisted history of that topic`() {
        store(1L, 100L, "apple")
        store(2L, 100L, "banana")
        store(3L, 200L, "cherry")
        pubsub.open(100L).block()

        val history = service
            .listenTopic(ByIdRequest(100L))
            .take(Duration.ofMillis(500))
            .collectList()
            .block()!!

        Assertions.assertThat(history.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `listenTopic emits nothing for a topic with no message`() {
        store(1L, 100L, "apple")
        pubsub.open(999L).block()

        val history = service
            .listenTopic(ByIdRequest(999L))
            .take(Duration.ofMillis(500))
            .collectList()
            .block()!!

        Assertions.assertThat(history).isEmpty()
    }

    /**
     * **A list ends after the history.** `listenTopic` stays open for live
     * messages, so only a timeout ends it. A list that waited for the listener
     * would never complete, and the shell would hang. See `CHAT-rghaeqsa`.
     */
    @Test
    fun `listMessages emits the history of that topic and completes`() {
        store(1L, 100L, "apple")
        store(2L, 100L, "banana")
        store(3L, 200L, "cherry")
        pubsub.open(100L).block()

        val history = service
            .listMessages(ByIdRequest(100L))
            .collectList()
            .block(Duration.ofSeconds(5))!!

        Assertions.assertThat(history.map { it.key.id }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `listMessages refuses an id that the registry does not hold`() {
        reactor.test.StepVerifier.create(service.listMessages(ByIdRequest(555L)))
            .expectErrorMatches { it is com.demo.chat.domain.KeyVerificationException && it.message == "Key 555 is not in the registry." }
            .verify(Duration.ofSeconds(5))
    }
}
