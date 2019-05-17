package com.demo.chat.service

import com.demo.chat.domain.ChatRoom
import com.demo.chat.domain.ChatRoomKey
import com.demo.chat.repository.cassandra.ChatRoomRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatRoomServiceCassandra::class])
@OverrideAutoConfiguration(enabled = true)
@ImportAutoConfiguration(classes = [ChatRoomServiceCassandra::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ChatRoomServiceTests {

    lateinit var roomSvc: ChatRoomServiceCassandra

    @MockBean
    lateinit var roomRepo: ChatRoomRepository

    val rid: UUID = UUID.randomUUID()

    val uid: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val newRoom = ChatRoom(ChatRoomKey(rid, "test-room"), emptySet(), Instant.now())

        Mockito.`when`(roomRepo.joinRoom(anyObject(), anyObject()))
                .thenReturn(Mono.empty())

        Mockito.`when`(roomRepo.findByKeyRoomId(anyObject()))
                .thenReturn(Mono.just(newRoom))

        Mockito.`when`(roomRepo.leaveRoom(anyObject(), anyObject()))
                .thenReturn(Mono.empty())

        roomSvc = ChatRoomServiceCassandra(roomRepo)
    }

    @Test
    fun `should join and leave a ficticious room`() {
        val serviceFlux = roomSvc
                .joinRoom(uid, rid)
                .thenMany(roomSvc.leaveRoom(uid, rid))

        StepVerifier
                .create(serviceFlux)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete()
    }
}