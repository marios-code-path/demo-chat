package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.Message
import com.demo.chat.domain.cassandra.*
import com.demo.chat.repository.cassandra.ChatMessageByTopicRepository
import com.demo.chat.repository.cassandra.ChatMessageByUserRepository
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.test.context.junit.jupiter.SpringExtension
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class])
class MessageRepositoryTests : CassandraSchemaTest(){

    private val MSGTEXT = "SUP TEST"

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Value("classpath:simple-message.cql")
    override lateinit var cqlFile: Resource

    @Autowired
    lateinit var repo: ChatMessageRepository<UUID>

    @Autowired
    lateinit var byTopicRepo: ChatMessageByTopicRepository<UUID>

    @Autowired
    lateinit var byUserRepo: ChatMessageByUserRepository<UUID>

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
    fun `should save single message find in topic by topic id`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = Flux
                .just(ChatMessageByTopicKey(msgId, userId, topicId, Instant.now()))
                .flatMap { key ->
                    byTopicRepo.save(ChatMessageByTopic(key, MSGTEXT, true))
                }

        val findMsg = byTopicRepo.findByKeyDest(topicId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageTopicAssertion)
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

        val findMsg = byUserRepo.findByKeyFrom(userId)

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
                .generate<ChatMessageByTopic<UUID>> {
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
                .thenMany(byTopicRepo.findByKeyDest(roomSelection))

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

        val testPublisher = TestPublisher.create<ChatMessageById<UUID>>()

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
                .thenMany(byUserRepo.findByKeyFrom(userId))

        StepVerifier
                .create(saveFindOps)
                .expectSubscription()
                .expectNextCount(4)
                .verifyComplete()
    }

    fun chatMessageAssertion(msg: Message<UUID, String>) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.dest) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.record) }
    )

    fun chatMessageUserAssertion(msg: ChatMessageByUser<UUID>) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.dest) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.record) }
    )

    fun chatMessageTopicAssertion(msg: ChatMessageByTopic<UUID>) = assertAll("message state test",
            { assertNotNull(msg) },
            { assertNotNull(msg.key.id) },
            { assertNotNull(msg.key.dest) },
            { assertNotNull(msg.data) },
            { assertEquals(msg.data, MSGTEXT) },
            { assertTrue(msg.record) }
    )
}