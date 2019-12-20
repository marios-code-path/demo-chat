package com.demo.chatevents.tests

import com.demo.chat.domain.JoinAlert
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.testRoomId
import com.demo.chatevents.testUserId
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*


open class MessageTopicServiceTestBase {

    val logger = LoggerFactory.getLogger(this::class.java)

    lateinit var topicService: ChatTopicService

    lateinit var topicAdmin : ChatTopicServiceAdmin

    object configProps : ConfigurationPropertiesTopicRedis  {
        override val port: Int = 6374
        override val host: String = "127.0.0.1"
    }

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
                .map(UUID::toString)

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
                .map(UUID::toString)

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
                .sendMessage(JoinAlert.create(UUID.randomUUID(), testRoom, userId))

        StepVerifier
                .create(steps)
                .verifyError()
    }


    @Test
    fun `should send message to created topic`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.subscribe(userId, testRoom)

        StepVerifier
                .create(steps)
                .then {
                    topicService
                            .sendMessage(JoinAlert.create(UUID.randomUUID(), testRoom, userId))
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
                .map(UUID::toString)

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
                .create(topicService.sendMessage(JoinAlert.create(UUID.randomUUID(), testRoom, userId)))
                .expectSubscription()
                .expectComplete()
                .verify(Duration.ofSeconds(1))

        val uFlux = topicService.receiveOn(userId).doOnNext { logger.info("GOT: ${it.key.topicId}") }

        StepVerifier
                .create(uFlux)
                .expectSubscription()
                .then {
                    Assertions
                            .assertThat(topicAdmin.getProcessor(userId).downstreamCount())
                            .isGreaterThanOrEqualTo(1)
                }

                .then {
                    StepVerifier
                            .create(Flux.merge(
                                    topicService.unSubscribe(userId, testRoom),
                                    topicService.rem(userId),
                                    topicService.rem(testRoom)
                            ))
                            .expectSubscription()
                            .verifyComplete()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(3))
    }
}