package com.demo.chat

import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.repository.cassandra.ChatRoomRepository
import io.rsocket.RSocket
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.transport.netty.client.TcpClientTransport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@SpringBootTest(classes = [ChatServiceRsocketApplication::class])
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatRSocketRoomTests {


    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester
    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    @MockBean
    private lateinit var roomRepo: ChatRoomRepository

    val randomName = randomAlphaNumeric(6) + "Room"
    val randomRoomId = UUID.randomUUID()
    val randomUserId = UUID.randomUUID()
    val room = com.demo.chat.domain.ChatRoom(
            com.demo.chat.domain.ChatRoomKey(randomRoomId, randomName),
            emptySet(), true, Instant.now())

    @BeforeEach
    fun setUp() {
        requestor = builder.connect(TcpClientTransport.create(7070)).block()!!
        socket = requestor.rsocket()

        BDDMockito
                .given(roomRepo.saveRoom(anyObject()))
                .willReturn(Mono.just(room))

        BDDMockito
                .given(roomRepo.joinRoom(anyObject(), anyObject()))
                .willReturn(Mono.empty())
    }

    @Test
    fun `should create a room receive response`() {
        StepVerifier.create(
                requestor.route("room-create")
                        .data(TestRoomCreateRequest(randomName))
                        .retrieveMono(TestRoomCreateResponse::class.java)
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.roomKey)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Test
    fun `should receive list of rooms`() {
        BDDMockito
                .given(roomRepo.findAll())
                .willReturn(Flux.just(room))

        StepVerifier
                .create(
                        requestor
                                .route("room-list")
                                .data(RoomRequest(UUID.randomUUID()))
                                .retrieveFlux(TestRoomResponse::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.room)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("members")

                    Assertions
                            .assertThat(it.room.key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("name", randomName)
                            .hasFieldOrPropertyWithValue("roomId", randomRoomId)
                }
                .verifyComplete()


    }

    @Test
    fun `should not join a non existent Room`() {
        BDDMockito
                .given(roomRepo.findByKeyRoomId(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("room-join")
                                .data(RoomJoinRequest(randomUserId, randomRoomId))
                                .retrieveMono(Void::class.java)
                )
                .expectSubscription()
                .expectError(ApplicationErrorException::class.java)
                .verify()
    }
}