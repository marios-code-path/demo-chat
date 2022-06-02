package com.demo.chat.test.repository

import com.demo.chat.domain.Message
import com.demo.chat.domain.cassandra.ChatMessageById
import com.demo.chat.domain.cassandra.ChatMessageByIdKey
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
import com.datastax.oss.driver.api.core.uuid.Uuids as UUIDs

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class])
class MessageRepositoryTests : CassandraSchemaTest() {

    private val MSGTEXT = "SUP TEST"

    @Autowired
    lateinit var repo: ChatMessageRepository<UUID>

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
                        ChatMessageById(
                            ChatMessageByIdKey(
                                UUID.randomUUID(),
                                userId,
                                roomId,
                                Instant.ofEpochMilli(VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS))
                            ),
                            MSGTEXT,
                            true
                        )
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
                        ChatMessageById(
                            ChatMessageByIdKey(
                                UUID.randomUUID(),
                                userId,
                                roomId,
                                Instant.ofEpochMilli(VirtualTimeScheduler.get().now(TimeUnit.MILLISECONDS))
                            ),
                            MSGTEXT,
                            true
                        )
                    )
            }
            .assertNext {
                assertNotNull(it)
                assertTrue(it.key.timestamp.toEpochMilli() > current)
            }
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
}