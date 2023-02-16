package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.index.cassandra.domain.ChatMessageByTopic
import com.demo.chat.index.cassandra.domain.ChatMessageByTopicKey
import com.demo.chat.index.cassandra.domain.ChatMessageByUser
import com.demo.chat.index.cassandra.domain.ChatMessageByUserKey
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestUUIDKeyGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*
import java.util.function.Supplier
import kotlin.streams.asSequence

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
class MessageIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    private val MSGTEXT = "SUP TEST"

    @Autowired
    lateinit var byTopicRepo: ChatMessageByTopicRepository<UUID>

    @Autowired
    lateinit var byUserRepo: ChatMessageByUserRepository<UUID>

    @Test
    fun `should save single message find in topic by topic id`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        val msgId = Uuids.timeBased()

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
        val msgId = Uuids.timeBased()

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
        val getUser = Supplier { Uuids.timeBased() }
        val getMsg = Supplier { Uuids.timeBased() }

        val messages = Flux
            .generate<ChatMessageByTopic<UUID>> {
                it.next(
                    ChatMessageByTopic(
                        ChatMessageByTopicKey(getMsg.get(), getUser.get(), roomIds.random(), Instant.now()),
                        MSGTEXT, true
                    )
                )
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

    @Test
    fun `should save many find four by user id`() {
        val userId = Uuids.timeBased()

        val messages = Flux
            .just(
                ChatMessageByUser(
                    ChatMessageByUserKey(Uuids.timeBased(), userId, UUID.randomUUID(), Instant.now()),
                    "Welcome1",
                    true
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(
                        Uuids.timeBased(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now()
                    ), "Welcome2", true
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(Uuids.timeBased(), userId, UUID.randomUUID(), Instant.now()),
                    "Welcome3",
                    true
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(
                        Uuids.timeBased(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now()
                    ), "Welcome4", false
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(Uuids.timeBased(), userId, UUID.randomUUID(), Instant.now()),
                    "Welcome5",
                    true
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(
                        Uuids.timeBased(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now()
                    ), "Welcome6", true
                ),
                ChatMessageByUser(
                    ChatMessageByUserKey(Uuids.timeBased(), userId, UUID.randomUUID(), Instant.now()),
                    "Welcome7",
                    false
                )
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

    fun chatMessageUserAssertion(msg: ChatMessageByUser<UUID>) = assertAll("message state test",
        { Assertions.assertNotNull(msg) },
        { Assertions.assertNotNull(msg.key.id) },
        { Assertions.assertNotNull(msg.key.dest) },
        { Assertions.assertNotNull(msg.data) },
        { Assertions.assertEquals(msg.data, MSGTEXT) },
        { Assertions.assertTrue(msg.record) }
    )

    fun chatMessageTopicAssertion(msg: ChatMessageByTopic<UUID>) = assertAll("message state test",
        { Assertions.assertNotNull(msg) },
        { Assertions.assertNotNull(msg.key.id) },
        { Assertions.assertNotNull(msg.key.dest) },
        { Assertions.assertNotNull(msg.data) },
        { Assertions.assertEquals(msg.data, MSGTEXT) },
        { Assertions.assertTrue(msg.record) }
    )
}