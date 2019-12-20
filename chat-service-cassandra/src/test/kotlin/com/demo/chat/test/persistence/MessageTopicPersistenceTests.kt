package com.demo.chat.test.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.domain.cassandra.ChatTopic
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicNameKey
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.TopicPersistenceCassandra
import com.demo.chat.test.TestKeyService
import com.demo.chat.test.domain.anyObject
import com.demo.chat.test.randomAlphaNumeric
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class MessageTopicPersistenceTests {

    val ROOMNAME = "test-room"

    lateinit var roomSvc: TopicPersistenceCassandra<UUID>

    @MockBean
    lateinit var roomByNameRepo: TopicByNameRepository<UUID>

    @MockBean
    lateinit var roomRepo: TopicRepository<UUID>

    private val keyService: IKeyService<UUID> = TestKeyService

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatTopic(ChatTopicKey(rid), ROOMNAME, true)
        val roomNameRoom = ChatTopicName(ChatTopicNameKey(rid, ROOMNAME), true)
        val roomTwo = ChatTopic(ChatTopicKey(UUID.randomUUID()), randomAlphaNumeric(6), true)

        BDDMockito.given(roomRepo.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.findAll())
                .willReturn(Flux.just(newRoom, roomTwo))

        BDDMockito.given(roomRepo.findByKeyId(anyObject()))
                .willReturn(Mono.just(newRoom))

        BDDMockito.given(roomRepo.rem(anyObject()))
                .willReturn(Mono.empty())

        roomSvc = TopicPersistenceCassandra(keyService, roomRepo)
    }

    @Test
    fun `should create some rooms then get a list`() {
        val names = setOf(randomAlphaNumeric(5), randomAlphaNumeric(5))

        val roomStore = Flux
                .fromStream(names.stream())
                .map { name ->
                    MessageTopic.create(Key.eventKey(UUIDs.timeBased()), name)
                }
                .flatMap(roomSvc::add)

        StepVerifier
                .create(
                        Flux.from(roomStore).thenMany(roomSvc.all())
                )
                .expectSubscription()
                .assertNext(this::roomAssertions)
                .assertNext(this::roomAssertions)
                .verifyComplete()
    }

    fun roomAssertions(messageTopic: MessageTopic<UUID>) {
        assertAll("room state test",
                { Assertions.assertNotNull(messageTopic) },
                { Assertions.assertNotNull(messageTopic.key.id) },
                { Assertions.assertNotNull(messageTopic.data) }
        )
    }

}