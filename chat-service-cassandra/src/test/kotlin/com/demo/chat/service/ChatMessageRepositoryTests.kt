package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.domain.ChatMessageRoom
import com.demo.chat.domain.ChatMessageUser
import com.demo.chat.repository.ChatMessageRepository
import com.demo.chat.repository.ChatMessageRoomRepository
import com.demo.chat.repository.ChatMessageUserRepository
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
import org.springframework.context.annotation.Import
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CassandraConfiguration::class, ChatServiceApplication::class)
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-message.cql")
class ChatMessageRepositoryTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var repo: ChatMessageRepository

    @Autowired
    lateinit var byRoomRepo: ChatMessageRoomRepository

    @Autowired
    lateinit var byUserRepo: ChatMessageUserRepository

    @Test
    fun `should save single msg find by message id`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = repo.saveMessages(Flux.just(ChatMessage(
                ChatMessageKey(msgId, userId, roomId, Instant.now()), "Welcome", true)))
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
    fun `should save single msg find in Room by room id`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = repo.saveMessages(Flux.just(ChatMessage(
                ChatMessageKey(msgId, userId, roomId, Instant.now()), "Welcome", true)))
        val findMsg = byRoomRepo.findByKeyRoomId(roomId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageRoomAssertion)
                .verifyComplete()
    }

    @Test
    fun `should save single msg find message by Userid`() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUIDs.timeBased()

        val saveMsg = repo.saveMessages(Flux.just(ChatMessage(
                ChatMessageKey(msgId, userId, roomId, Instant.now()), "Welcome", true)))
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
                .generate<ChatMessage> {
                    it.next(ChatMessage(
                            ChatMessageKey(getMsg.get(), getUser.get(), roomIds.random(), Instant.now()),
                            "Random Message", true))
                }
                .take(10)
                .cache()

        val roomSelection = roomIds.random()

        val countOfMessagesInSelectedRoom = messages.toStream().asSequence()
                .count { it.key.roomId == roomSelection }

        val saveMessageFlux = repo
                .saveMessages(messages)
                .thenMany(byRoomRepo.findByKeyRoomId(roomSelection))

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

        val testPublisher = TestPublisher.create<ChatMessage>()

        val testPublisherSupplier = Supplier {
            testPublisher.flux()
        }

        val current = Instant.now().toEpochMilli()

        StepVerifier.withVirtualTime(testPublisherSupplier)
                .then { VirtualTimeScheduler.get().advanceTimeTo(Instant.ofEpochMilli(current)) }
                .then {
                    testPublisher
                            .next(
                                    ChatMessage(ChatMessageKey(
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
                                    ChatMessage(ChatMessageKey(
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

        val chatMessageFlux = Flux
                .just(
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome1", true),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome2", true),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome3", true),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome4", false),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome5", true),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome6", true),
                        ChatMessage(ChatMessageKey(UUIDs.timeBased(), userId, UUID.randomUUID(), Instant.now()), "Welcome7", false)
                )

        val saveFindOps = repo.saveMessages(chatMessageFlux)
                .thenMany(byUserRepo.findByKeyUserId(userId))

        StepVerifier
                .create(saveFindOps)
                .expectSubscription()
                .expectNextCount(4)
                .verifyComplete()
    }

    fun chatMessageAssertion(msg: ChatMessage) =
            assertAll("message contents in tact",
                    { assertNotNull(msg) },
                    { assertNotNull(msg.key.id) },
                    { assertNotNull(msg.key.userId) },
                    { assertNotNull(msg.key.roomId) },
                    { assertNotNull(msg.text) },
                    { assertEquals(msg.text, "Welcome") },
                    { assertTrue(msg.visible) }
            )

    fun chatMessageUserAssertion(msg: ChatMessageUser) =
            assertAll("message contents in tact",
                    { assertNotNull(msg) },
                    { assertNotNull(msg.key.id) },
                    { assertNotNull(msg.key.userId) },
                    { assertNotNull(msg.key.roomId) },
                    { assertNotNull(msg.text) },
                    { assertEquals(msg.text, "Welcome") },
                    { assertTrue(msg.visible) }
            )

    fun chatMessageRoomAssertion(msg: ChatMessageRoom) =
            assertAll("message contents in tact",
                    { assertNotNull(msg) },
                    { assertNotNull(msg.key.id) },
                    { assertNotNull(msg.key.userId) },
                    { assertNotNull(msg.key.roomId) },
                    { assertNotNull(msg.text) },
                    { assertEquals(msg.text, "Welcome") },
                    { assertTrue(msg.visible) }
            )
}