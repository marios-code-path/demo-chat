package com.demo.chat.service

import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import com.demo.chat.repository.ChatMessageRepository
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CassandraConfiguration::class, ChatServiceApplication::class)
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-message.cql")
class ChatMessageRepositoryTests {

    @Autowired
    lateinit var repo: ChatMessageRepository

    @Test
    fun testShouldSaveFindByRoomId() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val msgId = UUID.randomUUID()

        val saveMsg = repo.insert(ChatMessage(ChatMessageKey(msgId, userId, roomId, Instant.now()), "Welcome", true))
        val findMsg = repo.findByKeyRoomId(roomId)

        val composite = Flux
                .from(saveMsg)
                .thenMany(findMsg)

        StepVerifier
                .create(composite)
                .assertNext(this::chatMessageAssertion)
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

        StepVerifier.withVirtualTime (testPublisherSupplier)
                .then { VirtualTimeScheduler.get().advanceTimeTo(Instant.ofEpochMilli(current))}
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
                    assertTrue( it.key.timestamp.toEpochMilli() >= current)
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
                    assertTrue( it.key.timestamp.toEpochMilli() > current)
                }
                .verifyComplete()

    }

    @Test
    fun testShouldSaveFindMessagesByUserId() {
        val userId = UUID.randomUUID()

        val chatMessageFlux = Flux
                .just(
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), userId, UUID.randomUUID(), Instant.now()), "Welcome", true),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome", true),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), userId, UUID.randomUUID(), Instant.now()), "Welcome", true),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome", false),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), userId, UUID.randomUUID(), Instant.now()), "Welcome", true),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()), "Welcome", true),
                        ChatMessage(ChatMessageKey(UUID.randomUUID(), userId, UUID.randomUUID(), Instant.now()), "Welcome", false)
                ).delayElements(Duration.ofSeconds(2))

        val saveMessages = repo.insert(chatMessageFlux)
        val findMessages = repo.findByKeyUserId(userId)
        val composite = Flux
                .from(saveMessages)
                .thenMany(findMessages)

        StepVerifier
                .create(composite)
                .expectSubscription()
                .expectNextCount(4)
                .verifyComplete()
    }

    fun chatMessageAssertion(msg: ChatMessage) {
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
}