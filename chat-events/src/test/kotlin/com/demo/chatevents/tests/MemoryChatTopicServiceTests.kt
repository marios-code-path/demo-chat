package com.demo.chatevents.tests

import com.demo.chat.domain.JoinAlert
import com.demo.chat.service.ChatTopicService
import com.demo.chatevents.TopicServiceMemory
import com.demo.chatevents.testRoomId
import com.demo.chatevents.testUserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

class MemoryChatTopicServiceTests {

    val logger = LoggerFactory.getLogger(this::class.java)

    val topicService: ChatTopicService = TopicServiceMemory()

    @BeforeEach
    fun setUp() {
        Hooks.onOperatorDebug()
    }

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
    fun `should send message to created topic and verify reception`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val steps = topicService
                .createTopic(testRoom)
                .then(topicService.subscribeToTopic(userId, testRoom))
                .thenMany(topicService.receiveEvents(userId))

        StepVerifier
                .create(steps)
                .then {
                    topicService.sendMessageToTopic(JoinAlert.create(UUID.randomUUID(), testRoom, userId))
                            .block()
                }
                .expectNextCount(1)
                .then {
                    Flux.merge(
                            topicService.unSubscribeFromTopic(userId, testRoom),
                            topicService.closeTopic(testRoom),
                            topicService.closeTopic(userId))
                            .blockLast()
                }
                .expectComplete()
                .verify(Duration.ofSeconds(2))
    }
}