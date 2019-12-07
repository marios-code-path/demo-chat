package com.demo.chat.test.controller.app


import com.demo.chat.*
import com.demo.chat.controller.app.RoomController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ConfigurationRSocket::class, RSocketTopicTests.TestConfiguration::class)
class RSocketTopicTests : ControllerTestBase() {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var roomIndex: RoomIndexService

    @Autowired
    lateinit var roomPersistence: RoomPersistence

    @Autowired
    lateinit var userPersistence: UserPersistence

    @Autowired
    lateinit var topicService: ChatTopicService

    @Autowired
    lateinit var membershipIndex: MembershipIndexService

    @Autowired
    lateinit var membershipPersistence: MembershipPersistence

    private val randomUserHandle = randomAlphaNumeric(4) + "User"
    private val randomUserId: UUID = UUID.randomUUID()

    private val randomRoomName = randomAlphaNumeric(6) + "Room"
    private val randomRoomId: UUID = UUID.randomUUID()

    private val room = TestChatTopic(
            TestChatRoomKey(randomRoomId, randomRoomName),
            true)

    private val roomWithMembers = TestChatTopic(
            TestChatRoomKey(randomRoomId, randomRoomName),
            true)

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
                .willReturn(Mono.just(TopicKey.create(UUID.randomUUID())))

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
                                .retrieveFlux(TestChatTopic::class.java)
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
        val membershipId = UUID.randomUUID()

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
                .given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(TestChatUser(
                        TestChatUserKey(randomUserId, randomUserHandle),
                        "NAME", "http://imageURI", Instant.now()
                )))

        BDDMockito
                .given(membershipPersistence.byIds(anyObject()))
                .willReturn(Flux.just(RoomMembership.create(
                        EventKey.create(membershipId),
                        EventKey.create(randomRoomId),
                        EventKey.create(randomUserId))))

        BDDMockito
                .given(membershipIndex.findBy(anyObject()))
                .willReturn(Flux.just(EventKey.create(membershipId)))

        BDDMockito
                .given(topicService.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("room-members")
                                .data(RoomRequestId(randomRoomId))
                                .retrieveMono(TopicMemberships::class.java)
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

    @Configuration
    class TestConfiguration {
        @Controller
        class TestRoomController(roomP: RoomPersistence,
                                 roomInd: RoomIndexService,
                                 topicSvc: ChatTopicService,
                                 userP: UserPersistence,
                                 membershipP: MembershipPersistence,
                                 membershipInd: MembershipIndexService) :
                RoomController(roomP, roomInd, topicSvc, userP, membershipP, membershipInd)
    }
}