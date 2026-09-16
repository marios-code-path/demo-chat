package com.demo.chat.test.service.composite

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import org.assertj.core.api.Assertions
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

    private val messageIndex = FakeMessageIndex()
    private val persistence = FakeMessagePersistence()
    private val pubsub = FakePubSub()

    private val service = MessagingServiceImpl(
        messageIndex = messageIndex,
        messagePersistence = persistence,
        pubsub = pubsub,
        topicIdToQuery = MapRequestConverters()::topicIdToQuery,
    )

    private fun store(id: Long, topic: Long, text: String) {
        val message = Message.create(MessageKey.create(id, 10L, topic), text, true)
        persistence.add(message).block()
        messageIndex.add(message).block()
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
}
