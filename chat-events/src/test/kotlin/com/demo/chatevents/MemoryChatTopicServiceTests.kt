package com.demo.chatevents

import com.demo.chat.service.ChatTopicService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream

class MemoryChatTopicServiceTests {

    val log = LoggerFactory.getLogger(this::class.java)

    val topicService: ChatTopicService = MemoryChatTopicService()

    fun testRoomId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")//UUID.randomUUID()

    fun testUserId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

    fun randomUserId(): UUID {
        val lastDigit = Integer.toHexString(Random().nextInt(16))
        return UUID.fromString("ecb2cb88-5dd1-44c3-b818-13373000000$lastDigit")
    }

    fun randomText() =
            "Text ${Random().nextLong()}"


    @Test
    fun `validate user subscription`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val subscription = topicService.subscribeMember(userId, testRoom)

        val steps = Mono.from(subscription)
                .thenMany(
                        Flux.defer {
                            Flux.fromStream(
                                    topicService.getTopicMembers(testRoom).stream()
                                            .map(UUID::toString)
                            )
                        }
                )

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

        val subscription = topicService.subscribeMember(userId, testRoom)

        val steps = Mono.from(subscription)
                .then(topicService.unsubscribeMember(userId, testRoom))
                .thenMany(
                        Flux.defer {
                            Flux.fromStream(
                                    topicService.getTopicMembers(testRoom).stream()
                                            .map(UUID::toString)
                            )
                        }
                )

        StepVerifier
                .create(steps)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `validate feed has consumers`() {
        val userId = testUserId()
        val testRoom = testRoomId()

        val subscription = topicService.subscribeMember(userId, testRoom)

        val steps = Mono.from(subscription)
                .thenMany(
                        Flux.defer {
                            Flux.fromStream(
                                    topicService.getMemberTopics(userId).stream()
                                            .map(UUID::toString)
                            )
                        }
                )

        StepVerifier
                .create(steps)
                .expectSubscription()
                .expectNext("ecb2cb88-5dd1-44c3-b818-301000000000")
                .verifyComplete()
    }

    @Test
    fun `verify Feed is Consumable by Members`() {
        val userId = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")
        val testRoom = testRoomId()

        val messageSendSupplier = Supplier {
            topicService.sendMessageToTopic(TestTextMessage(
                    TestTextMessageKey(UUID.randomUUID(),
                            userId, testRoom, Instant.now()), randomText(), true
            ))
        }

        StepVerifier
                .create(
                        topicService
                                .subscribeMember(userId, testRoom)
                                .thenMany(topicService.getTopicStream(userId))
                )
                .then {
                    messageSendSupplier.get().subscribe()
                    Assertions.assertThat(topicService.getTopicMembers(testRoom))
                            .isNotNull
                            .isNotEmpty
                }
                .assertNext {
                    when (it) {
                        is TestTextMessage -> textMessageAssertion(it)
                        else -> {
                            log.info("Some other kind of message: ${it}")
                        }
                    }
                }
                .then {
                    messageSendSupplier.get().subscribe()
                    Assertions.assertThat(topicService.getMemberTopics(userId))
                            .isNotNull
                            .isNotEmpty
                }
                .assertNext {
                    when (it) {
                        is TestTextMessage -> textMessageAssertion(it)
                        else -> {
                            log.info("Some other kind of message: ${it}")
                        }
                    }
                }
                .then {
                    topicService.unsubscribeMemberAllTopics(userId).subscribe()
                }
                .expectComplete()
                .verify(Duration.ofMillis(2000))
    }

    @Test
    fun `ensure topic cleanup`() {
        val testRoom = testRoomId()

        val users: List<UUID> = Stream.generate { randomUserId() }
                .limit(5)
                .collect(Collectors.toList())

        val subs = Flux
                .fromStream(users.stream())
                .flatMap { topicService.subscribeMember(it, testRoom) }

        StepVerifier
                .create(subs)
                .verifyComplete()

        Assertions.assertThat(topicService.getTopicMembers(testRoom))
                .isNotNull
                .isNotEmpty
                .containsAll(users)

        StepVerifier
                .create(
                        topicService.unsubscribeTopicAllMembers(testRoom)
                )
                .verifyComplete()

        Assertions.assertThat(topicService.getTopicMembers(testRoom))
                .isNotNull
                .isEmpty()
    }

}