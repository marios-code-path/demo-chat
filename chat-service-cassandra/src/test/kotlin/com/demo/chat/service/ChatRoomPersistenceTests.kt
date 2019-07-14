package com.demo.chat.service

import com.demo.chat.domain.ChatRoom
import com.demo.chat.domain.ChatRoomKey
import com.demo.chat.domain.Room
import com.demo.chat.repository.cassandra.ChatRoomRepository
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
    lateinit var roomRepo: ChatRoomRepository

    val rid: UUID = UUID.randomUUID()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatRoom(ChatRoomKey(rid, "test-room"), emptySet(), true, Instant.now())
        val roomTwo = ChatRoom(ChatRoomKey(UUID.randomUUID(), randomAlphaNumeric(6)), emptySet(), true, Instant.now())

        BDDMockito.given(roomRepo.joinRoom(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.saveRoom(anyObject()))
                .willReturn(Mono.just(newRoom))

        BDDMockito.given(roomRepo.findAll())
                .willReturn(Flux.just(newRoom, roomTwo))

        BDDMockito.given(roomRepo.findByKeyRoomId(anyObject()))
                .willReturn(Mono.just(newRoom))

        BDDMockito.given(roomRepo.leaveRoom(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.remRoom(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomRepo.messageCount(anyObject()))
                .willReturn(Mono.just(1))

        roomSvc = ChatRoomPersistenceCassandra(roomRepo)
    }

    // TODO - check for nullable return types in  Room-Service.
    @Test
    fun `should join and leave a ficticious room`() {
        val serviceFlux = roomSvc
                .addMember(uid, rid)
                .thenMany(roomSvc.remMember(uid, rid))

        StepVerifier
                .create(serviceFlux)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete()
    }

    @Test
    fun `should create some rooms then get a list`() {
        StepVerifier
                .create(
                        roomSvc
                                .add(randomAlphaNumeric(5))
                                .then(roomSvc.add(randomAlphaNumeric(5)))
                                .thenMany(roomSvc.getAll(true))
                )
                .expectSubscription()
                .assertNext(this::roomAssertions)
                .assertNext(this::roomAssertions)
                .verifyComplete()
    }

    fun roomAssertions(room: Room) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.roomId) },
                { Assertions.assertNotNull(room.key.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }
}
