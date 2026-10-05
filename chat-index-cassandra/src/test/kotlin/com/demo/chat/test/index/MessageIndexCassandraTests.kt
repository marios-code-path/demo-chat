package com.demo.chat.test.index

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.index.cassandra.impl.MessageIndex
import com.demo.chat.index.cassandra.repository.ChatMessageByTopicRepository
import com.demo.chat.index.cassandra.repository.ChatMessageByUserRepository
import com.demo.chat.index.cassandra.repository.ChatMessageIndexByIdRepository
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.MessageIndexService.Companion.TOPIC
import com.demo.chat.service.core.MessageIndexService.Companion.USER
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import java.util.function.Function

/**
 * The Cassandra message index against a real store, for one key type. Before
 * `CHAT-xcmpudyb`, every message index test used `UUID` repositories alone,
 * so no test wrote a `Long` id, read a stored time, or removed a message.
 */
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@Tag("integration")
abstract class MessageIndexCassandraTests<T : Any>(
    keyGenerator: IKeyGenerator<T>,
    private val stringToKey: Function<String, T>,
    private val rootKeys: RootKeys<T>,
) : CassandraSchemaTest<T>(keyGenerator) {

    @Autowired
    lateinit var byUserRepo: ChatMessageByUserRepository<T>

    @Autowired
    lateinit var byTopicRepo: ChatMessageByTopicRepository<T>

    @Autowired
    lateinit var byIdRepo: ChatMessageIndexByIdRepository<T>

    private lateinit var index: MessageIndex<T>

    // A fixed past time. A row that carries the time of the write cannot equal it.
    private val sent: Instant = Instant.parse("2025-01-02T03:04:05.678Z")

    @BeforeAll
    fun setUp() {
        index = MessageIndex(stringToKey, byUserRepo, byTopicRepo, byIdRepo, rootKeys)
    }

    private fun message(): Message<T, String> = Message.create(
        SimpleMessageKey(
            keyGenerator.nextId(),
            rootKeys.of(ChatDomain.MESSAGE).id,
            keyGenerator.nextId(),
            keyGenerator.nextId(),
            sent
        ),
        "indexed message",
        true
    )

    private fun idsBy(msg: Message<T, String>): Flux<T> = Flux.concat(
        index.findBy(mapOf(TOPIC to msg.key.dest.toString())).map { it.id },
        index.findBy(mapOf(USER to msg.key.from.toString())).map { it.id },
    )

    @Test
    fun `should find a message by its room with the time it was sent`() {
        val msg = message()

        StepVerifier
            .create(index.add(msg).thenMany(index.findBy(mapOf(TOPIC to msg.key.dest.toString()))))
            .assertNext {
                assertThat(it.id).isEqualTo(msg.key.id)
                assertThat(it.from).isEqualTo(msg.key.from)
                assertThat(it.timestamp).isEqualTo(sent)
            }
            .verifyComplete()
    }

    @Test
    fun `should find a message by its sender with the time it was sent`() {
        val msg = message()

        StepVerifier
            .create(index.add(msg).thenMany(index.findBy(mapOf(USER to msg.key.from.toString()))))
            .assertNext {
                assertThat(it.id).isEqualTo(msg.key.id)
                assertThat(it.dest).isEqualTo(msg.key.dest)
                assertThat(it.timestamp).isEqualTo(sent)
            }
            .verifyComplete()
    }

    @Test
    fun `should find no message by room or sender after a removal by key`() {
        val msg = message()

        StepVerifier
            .create(index.add(msg).then(index.rem(TestKeys.key(msg.key.id))).thenMany(idsBy(msg)))
            .verifyComplete()
    }

    // The control. A removal that deleted every row would also pass the test above.
    @Test
    fun `should keep the other message of the room after a removal by key`() {
        val removed = message()
        val kept = Message.create(
            SimpleMessageKey(keyGenerator.nextId(), removed.key.root, removed.key.from, removed.key.dest, sent),
            "kept message",
            true
        )

        StepVerifier
            .create(
                index.add(removed).then(index.add(kept))
                    .then(index.rem(TestKeys.key(removed.key.id)))
                    .thenMany(idsBy(removed))
            )
            .expectNext(kept.key.id, kept.key.id)
            .verifyComplete()
    }

    @Test
    fun `should complete a removal of a message that the index does not hold`() {
        StepVerifier
            .create(index.rem(TestKeys.key(keyGenerator.nextId())))
            .verifyComplete()
    }
}

@TestPropertySource(properties = ["app.key.type=long"])
class LongMessageIndexCassandraTests :
    MessageIndexCassandraTests<Long>(TestLongKeyGenerator(), String::toLong, FakeKeyServices.longRoots())

/** A `TIMEUUID` column accepts only a time based id, so the generator makes those. */
@TestPropertySource(properties = ["app.key.type=uuid"])
class UUIDMessageIndexCassandraTests : MessageIndexCassandraTests<UUID>(
    object : IKeyGenerator<UUID> {
        override fun nextId(): UUID = Uuids.timeBased()
    },
    UUID::fromString,
    FakeKeyServices.uuidRoots()
)
