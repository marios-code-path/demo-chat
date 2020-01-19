package com.demo.chat.test.controller.app


import com.demo.chat.*
import com.demo.chat.codec.Codec
import com.demo.chat.controller.app.RoomController
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.TopicMemberships
import com.demo.chat.domain.TopicNotFoundException
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
@Import(TestConfigurationRSocket::class, RSocketMessageTopicTests.TestConfiguration::class)
class RSocketMessageTopicTests : ControllerTestBase() {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var topicIndex: TopicIndexService<UUID>

    @Autowired
    lateinit var topicPersistence: TopicPersistence<UUID>

    @Autowired
    lateinit var userPersistence: UserPersistence<UUID>

    @Autowired
    lateinit var topicService: ChatTopicMessagingService<UUID, out Any>

    @Autowired
    lateinit var membershipIndex: MembershipIndexService<UUID>

    @Autowired
    lateinit var membershipPersistence: MembershipPersistence<UUID>

    private val randomUserHandle = randomAlphaNumeric(4) + "User"
    private val randomUserId: UUID = UUID.randomUUID()

    private val randomRoomName = randomAlphaNumeric(6) + "Room"
    private val randomRoomId: UUID = UUID.randomUUID()

    private val room = TestChatMessageTopic(
            TestChatRoomKey(randomRoomId, randomRoomName),
            true)

    private val roomWithMembers = TestChatMessageTopic(
            TestChatRoomKey(randomRoomId, randomRoomName),
            true)

    @Test
    fun `should create a room receive Void response`() {
        BDDMockito
                .given(topicPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(topicService.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito
                .given(topicPersistence.key())
                .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))

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
                .given(topicPersistence.all())
                .willReturn(Flux.just(room))

        StepVerifier
                .create(
                        requestor
                                .route("room-list")
                                .data(RoomRequestId(UUID.randomUUID()))
                                .retrieveFlux(TestChatMessageTopic::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .hasFieldOrPropertyWithValue("name", randomRoomName)

                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasFieldOrPropertyWithValue("id", randomRoomId)
                }
                .verifyComplete()
    }

    @Test
    fun `should not join a non existent Room`() {
        BDDMockito
                .given(topicIndex.add(anyObject()))
                .willReturn(Mono.error(TopicNotFoundException))

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

        BDDMockito.given(topicIndex.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(topicPersistence.get(anyObject()))
                .willReturn(Mono.just(roomWithMembers))

        BDDMockito.given(topicIndex.findBy(anyObject()))
                .willReturn(Flux.just(roomWithMembers.key))

        BDDMockito.given(topicPersistence.add(anyObject()))
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
                .willReturn(Flux.just(TopicMembership.create(
                        Key.funKey(membershipId),
                        Key.funKey(randomRoomId),
                        Key.funKey(randomUserId))))

        BDDMockito
                .given(membershipIndex.findBy(anyObject()))
                .willReturn(Flux.just(Key.funKey(membershipId)))

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

    class EmptyDecoder: Codec<String, Any> {
        override fun decode(record: String): Any {
            return record as Any
        }

    }
    @Configuration
    class TestConfiguration {
        @Controller
        class TestRoomController(topicP: TopicPersistence<UUID>,
                                 topicInd: TopicIndexService<UUID>,
                                 topicSvc: ChatTopicMessagingService<UUID, Any>,
                                 userP: UserPersistence<UUID>,
                                 membershipP: MembershipPersistence<UUID>,
                                 membershipInd: MembershipIndexService<UUID>) :
                RoomController<UUID, Any>(topicP, topicInd, topicSvc, userP, membershipP, membershipInd, EmptyDecoder())
    }
}