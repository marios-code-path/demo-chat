package com.demo.chat.test.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.KeyService
import com.demo.chat.service.persistence.RoomPersistenceCassandra
import com.demo.chat.test.TestKeyService
import com.demo.chat.test.anyObject
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
class EventTopicPersistenceTests {

    val ROOMNAME = "test-room"

    lateinit var roomSvc: RoomPersistenceCassandra

    @MockBean
    lateinit var roomByNameRepo: ChatRoomNameRepository

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    private val keyService: KeyService = TestKeyService

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatEventTopic(ChatTopicKey(rid), ROOMNAME, true)
        val roomNameRoom = ChatEventTopicName(ChatRoomNameKey(rid, ROOMNAME), true)
        val roomTwo = ChatEventTopic(ChatTopicKey(UUID.randomUUID()), randomAlphaNumeric(6), true)


        BDDMockito.given(roomRepo.join(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.findAll())
                .willReturn(Flux.just(newRoom, roomTwo))

        BDDMockito.given(roomRepo.findByKeyId(anyObject()))
                .willReturn(Mono.just(newRoom))

        BDDMockito.given(roomRepo.leave(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.rem(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.messageCount(anyObject()))
                .willReturn(Mono.just(1))

        roomSvc = RoomPersistenceCassandra(keyService, roomRepo)
    }

    @Test
    fun `should create some rooms then get a list`() {
        val names = setOf(randomAlphaNumeric(5), randomAlphaNumeric(5))

        val roomStore = Flux
                .fromStream(names.stream())
                .map { name ->
                    EventTopic.create(TopicKey.create(UUIDs.timeBased()), name)
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

    fun roomAssertions(eventTopic: EventTopic) {
        assertAll("room state test",
                { Assertions.assertNotNull(eventTopic) },
                { Assertions.assertNotNull(eventTopic.key.id) },
                { Assertions.assertNotNull(eventTopic.name) }
        )
    }

}