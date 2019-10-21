package com.demo.chat.service

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.service.persistence.ChatRoomPersistenceCassandra
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatRoomPersistenceTests {

    lateinit var roomSvc: ChatRoomPersistenceCassandra

    @MockBean
    lateinit var roomByNameRepo: ChatRoomNameRepository

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    private val keyService: KeyService = TestKeyService

    private val rid: UUID = UUID.randomUUID()

    private val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatRoom(ChatRoomKey(rid, "test-room"), emptySet(), true, Instant.now())
        val roomNameRoom = ChatRoomName(ChatRoomNameKey(rid, "test-room"), emptySet(), true, Instant.now())
        val roomTwo = ChatRoom(ChatRoomKey(UUID.randomUUID(), randomAlphaNumeric(6)), emptySet(), true, Instant.now())

        BDDMockito.given(roomByNameRepo.findByKeyName(anyObject()))
                .willReturn(Mono.just(roomNameRoom))

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

        roomSvc = ChatRoomPersistenceCassandra(keyService, roomRepo, roomByNameRepo)
    }

    @Test
    fun `should create some rooms then get a list`() {
        StepVerifier
                .create(
                        Flux.just(randomAlphaNumeric(5), randomAlphaNumeric(5))
                                .flatMap { name ->
                                    roomSvc.key()
                                            .flatMap { key ->
                                                roomSvc.add(Room.create(
                                                        RoomKey.create(key.id, name),
                                                        setOf()
                                                )
                                                )
                                            }
                                }
                                .thenMany(roomSvc.all())
                )
                .expectSubscription()
                .assertNext(this::roomAssertions)
                .assertNext(this::roomAssertions)
                .verifyComplete()
    }

    fun roomAssertions(room: Room) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.key.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }

}