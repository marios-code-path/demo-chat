package com.demo.chat.test


import com.demo.chat.*
import com.demo.chat.controllers.RoomController
import com.demo.chat.domain.RoomKey
import com.demo.chat.domain.RoomMemberships
import com.demo.chat.domain.RoomNotFoundException
import com.demo.chat.service.*
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(RSocketTestConfig::class, RoomController::class)
class RSocketRoomTests : RSocketTestBase() {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var roomIndex: ChatRoomIndexService

    @Autowired
    lateinit var userIndex: ChatUserIndexService

    @Autowired
    lateinit var messageIndex: ChatMessageIndexService

    @Autowired
    lateinit var roomPersistence: ChatRoomPersistence

    @Autowired
    lateinit var userPersistence: ChatUserPersistence

    @Autowired
    lateinit var topicService: ChatTopicService

    private val randomUserHandle = randomAlphaNumeric(4) + "User"
    private val randomUserId: UUID = UUID.randomUUID()

    private val randomRoomName = randomAlphaNumeric(6) + "Room"
    private val randomRoomId: UUID = UUID.randomUUID()

    private val room = TestChatRoom(
            TestChatRoomKey(randomRoomId, randomRoomName),
            emptySet(), true, Instant.now())

    private val roomWithMembers = TestChatRoom(
            TestChatRoomKey(randomRoomId, randomRoomName),
            setOf(randomUserId), true, Instant.now())

    @Test
    fun `should create a room receive Void response`() {
        BDDMockito
                .given(roomPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(topicService.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(roomPersistence.key())
                .willReturn(Mono.just(RoomKey.create(UUID.randomUUID(), "randomRoomName")))

        StepVerifier.create(
                requestor.route("room-add")
                        .data(RoomCreateRequest(randomRoomName))
                        .retrieveMono(Void::class.java)
        )
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should receive list of rooms`() {
        BDDMockito
                .given(roomPersistence.all())
                .willReturn(Flux.just(room))

        StepVerifier
                .create(
                        requestor
                                .route("room-list")
                                .data(RoomRequestId(UUID.randomUUID()))
                                .retrieveFlux(TestChatRoom::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("members")

                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("name", randomRoomName)
                            .hasFieldOrPropertyWithValue("id", randomRoomId)
                }
                .verifyComplete()
    }

    @Test
    fun `should not join a non existent Room`() {
        BDDMockito
                .given(roomIndex.add(anyObject(), anyObject()))
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
        BDDMockito.given(roomIndex.add(anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(roomPersistence.get(anyObject()))
                .willReturn(Mono.just(roomWithMembers))

        BDDMockito.given(roomIndex.findBy(anyObject()))
                .willReturn(Flux.just(roomWithMembers.key))

        BDDMockito.given(roomPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(userPersistence.byIds(anyObject()))
                .willReturn(Flux.just(TestChatUser(
                        TestChatUserKey(randomUserId, randomUserHandle),
                        "NAME", "http://imageURI", Instant.now()
                )))

        BDDMockito
                .given(topicService.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("room-members")
                                .data(RoomRequestId(randomRoomId))
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