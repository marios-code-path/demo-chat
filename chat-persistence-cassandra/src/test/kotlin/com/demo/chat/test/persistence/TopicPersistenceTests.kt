package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopic
import com.demo.chat.persistence.cassandra.domain.ChatTopicKey
import com.demo.chat.persistence.cassandra.repository.TopicRepository
import com.demo.chat.service.IKeyService
import com.demo.chat.persistence.cassandra.impl.TopicPersistenceCassandra
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import com.datastax.oss.driver.api.core.uuid.Uuids as UUIDs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class TopicPersistenceTests {

    val ROOMNAME = "test-room"

    lateinit var roomSvc: TopicPersistenceCassandra<UUID>

    @MockBean
    lateinit var roomRepo: TopicRepository<UUID>

    private val keyService: IKeyService<UUID> = TestUUIDKeyService()

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatTopic(ChatTopicKey(rid), ROOMNAME, true)
        val roomTwo = ChatTopic(ChatTopicKey(UUID.randomUUID()), TestBase.randomAlphaNumeric(6), true)

        BDDMockito.given(roomRepo.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.findAll())
                .willReturn(Flux.just(newRoom, roomTwo))

        BDDMockito.given(roomRepo.findByKeyId(TestBase.anyObject()))
                .willReturn(Mono.just(newRoom))

        BDDMockito.given(roomRepo.rem(TestBase.anyObject()))
                .willReturn(Mono.empty())

        roomSvc = TopicPersistenceCassandra(keyService, roomRepo)
    }

    @Test
    fun `should create some rooms then get a list`() {
        val names = setOf(TestBase.randomAlphaNumeric(5), TestBase.randomAlphaNumeric(5))

        val roomStore = Flux
                .fromStream(names.stream())
                .map { name ->
                    MessageTopic.create(Key.funKey(UUIDs.timeBased()), name)
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