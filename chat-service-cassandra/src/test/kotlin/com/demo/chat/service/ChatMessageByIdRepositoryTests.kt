package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatMessageById
import com.demo.chat.domain.ChatMessageByIdKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.TextMessageKey
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import reactor.test.scheduler.VirtualTimeScheduler
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.streams.asSequence

@ExtendWith(SpringExtension::class)
@CassandraUnit
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class, TestClusterConfiguration::class])
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-message.cql")
class ChatMessageByIdRepositoryTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var repo: ChatMessageRepository

    @Autowired
    lateinit var byTopicRepo: ChatMessageByTopicRepository

    @Autowired
    lateinit var byUserRepo: ChatMessageByUserRepository

    @Test
    fun `should save single message find by message id`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = Flux.just(TextMessage.create(TextMessageKey.create(msgId, roomId, userId), "Welcome", true))
                .flatMap {
                    repo.add(it)
                }

        val findMsg = repo.findByKeyMsgId(msgId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageAssertion)
                .verifyComplete()
    }

    @Test
    fun `should save single message find in room by room id`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = repo.add(TextMessage
                .create(TextMessageKey.create(msgId, roomId, userId),
                        "Welcome", true))

        val findMsg = byTopicRepo.findByKeyTopicId(roomId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageRoomAssertion)
                .verifyComplete()
    }

    @Test
    fun `should save single message find message by userId`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = repo.add(
                TextMessage.create(TextMessageKey.create(msgId, roomId, userId),
                        "Welcome", true))

        val findMsg = byUserRepo.findByKeyUserId(userId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageUserAssertion)
                .verifyComplete()
    }

    @Test
    fun `should save many messages and find by roomId`() {
        val roomIds = listOf<UUID>(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val getUser = Supplier { UUIDs.timeBased() }
        val getMsg = Supplier { UUIDs.timeBased() }

        val messages = Flux
                .generate<ChatMessageById> {
                    it.next(ChatMessageById(
                            ChatMessageByIdKey(getMsg.get(), getUser.get(), roomIds.random(), Instant.now()),
                            "Random Message", true))
                }
                .take(10)
                .cache()

        val roomSelection = roomIds.random()

        val countOfMessagesInSelectedRoom = messages
                .toStream()
                .asSequence()
                .count { it.key.topicId == roomSelection }

        val saveMessageFlux = Flux.from(messages)
                .flatMap {
                    repo.add(it)
                }
                .thenMany(byTopicRepo.findByKeyTopicId(roomSelection))

        // Expecting : ${countOfMessagesInSelectedRoom.toLong()} msgs for room ${roomSelection}
        StepVerifier
                .create(saveMessageFlux)
                .expectSubscription()
                .expectNextCount(countOfMessagesInSelectedRoom.toLong())
                .verifyComplete()
    }

    @Test // TODO WORKING WITH TIME
    fun `verify key can propagate specific time state`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        val testPublisher = TestPublisher.create<ChatMessageById>()

        val testPublisherSupplier = Supplier {
            testPublisher.flux()
        }

        val current = Instant.now().toEpochMilli()

        StepVerifier
                .withVirtualTime(testPublisherSupplier)
                .then { VirtualTimeScheduler.get().advanceTimeTo(Instant.ofEpochMilli(current)) }
                .then {
                    testPublisher
                            .next(
                                    ChatMessageById(ChatMessageByIdKey(
                                            UUID.randomUUID(),
                                            userId,
                                            roomId,
                                            Instant.ofEpochMilli(VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS))
                                    ),
                                            "Hello",
                                            true)
                            )
                }
                .assertNext {
                    assertNotNull(it)
                    assertTrue(it.key.timestamp.toEpochMilli() >= current)
                }
                .thenAwait(Duration.ofSeconds(5))
                .then {
                    testPublisher
                            .emit(
                                    ChatMessageById(ChatMessageByIdKey(
                                            UUID.randomUUID(),
                                            userId,
                                            roomId,
                                            Instant.ofEpochMilli(VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS))
                                    ),
                                            "Hello",
                                            true)
                            )

                }
                .assertNext {
                    assertNotNull(it)
                    assertTrue(it.key.timestamp.toEpochMilli() > current)
                }
                .verifyComplete()

    }

    @Test
    fun `should save many find four by user id`() {
        val userId = UUIDs.timeBased()

        val messages = Flux
                .just(
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome1", true),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome2", true),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome3", true),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome4", false),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome5", true),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome6", true),
                        ChatMessageById(ChatMessageByIdKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome7", false)
                )
                .flatMap {
                    repo.add(it)
                }

        val saveFindOps = Flux.from(messages)
                .thenMany(byUserRepo.findByKeyUserId(userId))

        StepVerifier
                .create(saveFindOps)
                .expectSubscription()
                .expectNextCount(4)
                .verifyComplete()
    }

    fun chatMessageAssertion(msg: TextMessage) = assertAll("message contents in tact",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.msgId) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.value) },
            { assertEquals(msg.value, "Welcome") },
            { assertTrue(msg.visible) }
    )

    fun chatMessageUserAssertion(msg: TextMessage) = assertAll("message contents in tact",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.msgId) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.value) },
            { assertEquals(msg.value, "Welcome") },
            { assertTrue(msg.visible) }
    )

    fun chatMessageRoomAssertion(msg: TextMessage) = assertAll("message contents in tact",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.msgId) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.value) },
            { assertEquals(msg.value, "Welcome") },
            { assertTrue(msg.visible) }
    )
}