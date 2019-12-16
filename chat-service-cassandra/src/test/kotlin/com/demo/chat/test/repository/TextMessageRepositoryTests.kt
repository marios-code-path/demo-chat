package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.*
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.test.TestClusterConfiguration
import com.demo.chat.test.TestConfiguration
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
class TextMessageRepositoryTests {

    private val MSGTEXT = "SUP TEST"

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

        val saveMsg = Flux
                .just(ChatMessageByIdKey(msgId, roomId, userId, Instant.now()))
                .map { key ->
                    ChatMessageById(key, MSGTEXT, true)
                }
                .flatMap(repo::add)

        val findMsg = repo.findByKeyId(msgId)

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

        val saveMsg = Flux
                .just(ChatMessageByTopicKey(msgId, userId, roomId, Instant.now()))
                .flatMap { key ->
                    byTopicRepo.save(ChatMessageByTopic(key, MSGTEXT, true))
                }

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

        val saveMsg = Flux
                .just(ChatMessageByUserKey(msgId, userId, roomId, Instant.now()))
                .flatMap { key ->
                    byUserRepo.save(ChatMessageByUser(key, MSGTEXT, true))
                }

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
    fun `should save many messages into random rooms and find by specific roomId`() {
        val roomIds = listOf<UUID>(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val getUser = Supplier { UUIDs.timeBased() }
        val getMsg = Supplier { UUIDs.timeBased() }

        val messages = Flux
                .generate<ChatMessageByTopic> {
                    it.next(ChatMessageByTopic(
                            ChatMessageByTopicKey(getMsg.get(), getUser.get(), roomIds.random(), Instant.now()),
                            MSGTEXT, true))
                }
                .take(10)
                .cache()

        val roomSelection = roomIds.random()

        val countOfMessagesInSelectedRoom = messages
                .toStream()
                .asSequence()
                .count { it.key.dest == roomSelection }

        val saveMessageFlux = Flux
                .from(messages)
                .flatMap {
                    byTopicRepo.save(it)
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
                                            MSGTEXT,
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
                                            MSGTEXT,
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
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome1", true),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome2", true),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome3", true),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome4", false),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome5", true),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome6", true),
                        ChatMessageByUser(ChatMessageByUserKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome7", false)
                )
                .flatMap {
                    byUserRepo.save(it)
                }

        val saveFindOps = Flux.from(messages)
                .thenMany(byUserRepo.findByKeyUserId(userId))

        StepVerifier
                .create(saveFindOps)
                .expectSubscription()
                .expectNextCount(4)
                .verifyComplete()
    }

    fun chatMessageAssertion(msg: TextMessage) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.visible) }
    )

    fun chatMessageUserAssertion(msg: TextMessage) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.visible) }
    )

    fun chatMessageRoomAssertion(msg: TextMessage) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.userId) },
            { assertNotNull(msg.key.topicId) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.visible) }
    )
}