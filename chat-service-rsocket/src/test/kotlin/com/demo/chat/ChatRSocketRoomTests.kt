package com.demo.chat


import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.domain.RoomMemberships
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.service.ChatRoomServiceCassandra
import com.demo.chat.service.ChatUserServiceCassandra
import io.rsocket.RSocket
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.transport.netty.client.TcpClientTransport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
@Import(TestSetupConfig::class)
class ChatRSocketRoomTests {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var rsboot: RSocketServerBootstrap

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    @Autowired
    lateinit var roomService: ChatRoomServiceCassandra

    @Autowired
    lateinit var userService: ChatUserServiceCassandra

    val randomUserHandle = randomAlphaNumeric(4) + "User"
    val randomUserId: UUID = UUID.randomUUID()

    val randomRoomName = randomAlphaNumeric(6) + "Room"
    val randomRoomId: UUID = UUID.randomUUID()

    val room = com.demo.chat.domain.ChatRoom(
            com.demo.chat.domain.ChatRoomKey(randomRoomId, randomRoomName),
            emptySet(), true, Instant.now())

    val roomWithMembers = com.demo.chat.domain.ChatRoom(
            com.demo.chat.domain.ChatRoomKey(randomRoomId, randomRoomName),
            setOf(randomUserId), true, Instant.now())

    @BeforeEach
    fun setUp() {
        when (rsboot.isRunning) {
            false -> {
                log.warn("RSocket Service is not already running");
                rsboot.start()
            }
            else -> log.warn("RSocket Service is already running")
        }
        requestor = builder.connect(TcpClientTransport.create(7070)).block()!!
        socket = requestor.rsocket()

        BDDMockito
                .given(roomService.createRoom(anyObject()))
                .willReturn(Mono.just(room.key))

        BDDMockito
                .given(roomService.joinRoom(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userService.getUsersById(anyObject()))
                .willReturn(Flux.just(ChatUser(
                        ChatUserKey(randomUserId, randomUserHandle),
                        "NAME", Instant.now()
                )))
    }

    @AfterEach
    fun tearDown() {

    }

    @Test
    fun `should create a room receive response`() {
        StepVerifier.create(
                requestor.route("room-create")
                        .data(TestRoomCreateRequest(randomRoomName))
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
                .given(roomService.getRooms(anyBoolean()))
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
                            .hasFieldOrPropertyWithValue("name", randomRoomName)
                            .hasFieldOrPropertyWithValue("roomId", randomRoomId)
                }
                .verifyComplete()


    }

    @Test
    fun `should not join a non existent Room`() {
        BDDMockito
                .given(roomService.joinRoom(anyObject(), anyObject()))
                .willReturn(Mono.error(RoomNotFoundException))

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

    @Test
    fun `joins a room and appears in member list`() {
        BDDMockito.given(roomService.joinRoom(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomService.getRoomById(anyObject()))
                .willReturn(Mono.just(roomWithMembers))

        BDDMockito.given(roomService.roomMembers(anyObject()))
                .willReturn(Mono.just(roomWithMembers.members!!))

        StepVerifier
                .create(
                        requestor
                                .route("room-members")
                                .data(RoomRequest(randomRoomId))
                                .retrieveMono(RoomMemberships::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.members)
                            .isNotNull
                            .isNotEmpty

                    Assertions
                            .assertThat(it.members.first())
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("uid", randomUserId)
                            .hasFieldOrPropertyWithValue("handle", randomUserHandle)
                }
                .verifyComplete()

    }
}