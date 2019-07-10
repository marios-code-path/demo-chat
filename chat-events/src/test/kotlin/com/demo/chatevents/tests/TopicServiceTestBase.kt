package com.demo.chatevents.tests

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.JoinAlert
import com.demo.chat.service.ChatTopicService
import com.demo.chat.service.ChatTopicServiceAdmin
import com.demo.chatevents.testRoomId
import com.demo.chatevents.testUserId
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*


open class TopicServiceTestBase {

    val logger = LoggerFactory.getLogger(this::class.java)

    lateinit var topicService: ChatTopicService

    lateinit var topicAdmin : ChatTopicServiceAdmin

    @Test
    fun `cannot subscribe to non existent topic`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService
                .subscribeToTopic(userId, testRoom)

        StepVerifier
                .create(steps)
                .verifyError()
    }

    @Test
    fun `validate user unsubscribe`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.createTopic(testRoom)
                .then(topicService.subscribeToTopic(userId, testRoom))
                .then(topicService.unSubscribeFromTopic(userId, testRoom))
                .thenMany(topicService.getTopicMembers(testRoom))
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
                .createTopic(testRoom)
                .then(topicService.subscribeToTopic(userId, testRoom))
                .thenMany(topicService.getTopicMembers(testRoom))
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
                .sendMessageToTopic(JoinAlert.create(UUID.randomUUID(), testRoom, userId))

        StepVerifier
                .create(steps)
                .verifyError()
    }


    @Test
    fun `should send message to created topic`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.subscribeToTopic(userId, testRoom)

        StepVerifier
                .create(steps)
                .then {
                    topicService
                            .sendMessageToTopic(JoinAlert.create(UUID.randomUUID(), testRoom, userId))
                }
                .verifyError()
    }


    @Test
    fun `validate user subscription`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService.createTopic(testRoom)
                .then(topicService.subscribeToTopic(userId, testRoom))
                .thenMany(topicService.getTopicMembers(testRoom))
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
                .createTopic(topicId)
                .then(topicService.topicExists(topicId))

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
                .createTopic(topicId)

        val subscriber = topicService
                .subscribeToTopic(userId, topicId)

        val unsubscribe = topicService
                .unSubscribeFromTopic(userId, topicId)

        val stream = Flux
                .from(createTopic)
                .then(subscriber)
                .then(unsubscribe)

        StepVerifier
                .create(stream)
                .expectSubscription()
                .verifyComplete()
    }

}