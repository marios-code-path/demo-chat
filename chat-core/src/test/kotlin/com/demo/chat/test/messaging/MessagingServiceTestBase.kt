package com.demo.chat.test.messaging

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatTopicMessagingService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

data class JoinAlert<T>(override val key: MessageKey<T>) : Message<T, String> {
    override val record: Boolean
        get() = false
    override val data: String
        get() = ""
}

class KeyStringEncoder<T> : Codec<T, String> {
    override fun decode(record: T): String {
        return record.toString()
    }
}

open class MessagingServiceTestBase {

    lateinit var topicService: ChatTopicMessagingService<UUID, String>

    fun testRoomId(): UUID = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")
    fun testUserId(): UUID = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

    @Test
    fun `cannot subscribe to non existent topic`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService
                .subscribe(userId, testRoom)

        StepVerifier
                .create(steps)
                .verifyError()
    }

    @Test
    fun `validate user unsubscribe`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.add(testRoom)
                .then(topicService.subscribe(userId, testRoom))
                .then(topicService.unSubscribe(userId, testRoom))
                .thenMany(topicService.getUsersBy(testRoom))

        StepVerifier
                .create(steps)
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }

    @Test
    fun `validate topic has members`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService
                .add(testRoom)
                .then(topicService.subscribe(userId, testRoom))
                .thenMany(topicService.getUsersBy(testRoom))
                .map {
                    KeyStringEncoder<Any>().decode(it)
                }

        StepVerifier
                .create(steps)
                .expectSubscription()
                .expectNext("ecb2cb88-5dd1-44c3-b818-133730000000")
                .verifyComplete()
    }

    @Test
    fun `cannot send a message to non existent topic`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService
                .sendMessage(JoinAlert(MessageKey.create(UUID.randomUUID(), userId, testRoom)))

        StepVerifier
                .create(steps)
                .verifyError()
    }


    @Test
    fun `cannot subscribe to non existent topic `() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.subscribe(userId, testRoom)

        StepVerifier
                .create(steps)
                .then {
                    topicService
                            .sendMessage(JoinAlert(MessageKey.create(UUID.randomUUID(), UUID.randomUUID(), testRoom)))
                }
                .verifyError()
    }


    @Test
    fun `validate user subscription`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.add(testRoom)
                .then(topicService.subscribe(userId, testRoom))
                .thenMany(topicService.getUsersBy(testRoom))
                .map {
                    KeyStringEncoder<Any>().decode(it)
                }

        StepVerifier
                .create(steps)
                .expectSubscription()
                .expectNext("ecb2cb88-5dd1-44c3-b818-133730000000")
                .verifyComplete()
    }

    @Test
    fun `should create a topic`() {
        val topicId = UUID.randomUUID()

        val stream = topicService
                .add(topicId)
                .then(topicService.exists(topicId))

        StepVerifier
                .create(stream)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .`as`("topic created, exists")
                            .isTrue()
                }
                .verifyComplete()
    }

    @Test
    fun `should create, subscribe and unsubscribe to a topic`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()

        val createTopic = topicService
                .add(topicId)

        val subscriber = topicService
                .subscribe(userId, topicId)

        val unsubscribe = topicService
                .unSubscribe(userId, topicId)

        val stream = Flux
                .from(createTopic)
                .then(subscriber)
                .then(unsubscribe)

        StepVerifier
                .create(stream)
                .expectSubscription()
                .verifyComplete()
    }


    @Test
    fun `should send message to created topic and verify reception`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        StepVerifier
                .create(topicService.add(testRoom))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        StepVerifier
                .create(topicService.subscribe(userId, testRoom))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        StepVerifier
                .create(topicService
                        .sendMessage(Message.create(
                                MessageKey.create(UUID(0L, 0L), userId, testRoom),
                                "ALERT",
                                false)))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))
    }
}