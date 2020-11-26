package com.demo.chat.test.rsocket.controller.edge


import com.demo.chat.*
import com.demo.chat.controller.edge.TopicController
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.test.TestChatMessageTopic
import com.demo.chat.test.TestChatRoomKey
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.function.Function

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MockCoreServicesConfiguration::class, EdgeTopicControllerTests.EdgeTopicControllerConfiguration::class)
open class EdgeTopicControllerTests : RSocketControllerTestBase() {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    @Autowired
    lateinit var topicPersistence: TopicPersistence<UUID>

    @Autowired
    lateinit var userPersistence: UserPersistence<UUID>

    @Autowired
    lateinit var topicService: PubSubTopicExchangeService<UUID, String>

    @Autowired
    lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @Autowired
    lateinit var membershipPersistence: MembershipPersistence<UUID>

    val randomUserHandle = randomAlphaNumeric(4) + "User"
    val randomUserId: UUID = UUID.fromString("4455814b-9886-499a-8547-55968e3183c6")

    val randomRoomName = randomAlphaNumeric(6) + "Room"
    val randomTopicId: UUID = UUID.randomUUID()

    val room = TestChatMessageTopic(
            TestChatRoomKey(randomTopicId, randomRoomName),
            true)

    val roomWithMembers = TestChatMessageTopic(
            TestChatRoomKey(randomTopicId, randomRoomName),
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
                requester.route("room-add")
                        .data(ByNameRequest(randomRoomName))
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
                        requester
                                .route("room-list")
                                .data(ByIdRequest(UUID.randomUUID()))
                                .retrieveFlux(TestChatMessageTopic::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("key")
                            .extracting("key")
                            .hasFieldOrPropertyWithValue("name", randomRoomName)
                            .hasFieldOrPropertyWithValue("id", randomTopicId)
                }
                .verifyComplete()
    }

    @Test
    fun `should not join a non existent Room`() {
        StepVerifier
                .create(
                        requester
                                .route("room-join")
                                .data(MembershipRequest(randomUserId, randomTopicId))
                                .retrieveMono(Void::class.java)
                )
                .expectSubscription()
                .expectError(ApplicationErrorException::class.java)
                .verify()
    }

    @Test // TODO TopicMembership<T> does not decode/encode - I switched to a String return
    fun `should fetch topic member list`() {
        val membershipId = UUID.randomUUID()

        BDDMockito
                .given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(User.create(
                        Key.funKey(randomUserId), "NAME", randomUserHandle, "http://imageURI"
                )))

        BDDMockito
                .given(membershipPersistence.byIds(anyObject()))
                .willReturn(Flux.just(TopicMembership.create(
                        membershipId,
                        randomTopicId,
                        randomUserId)))

        BDDMockito
                .given(membershipIndex.findBy(anyObject()))
                .willReturn(Flux.just(Key.funKey(membershipId)))

        StepVerifier
                .create(
                        requester
                                .route("room-members")
                                .data(ByIdRequest(randomTopicId))
                                .retrieveMono(TopicMemberships::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.members)
                            .isNotEmpty

                    val member = it.members.first()

                    Assertions
                            .assertThat(member)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("uid", randomUserId.toString())
                            .hasFieldOrPropertyWithValue("handle", randomUserHandle)
                            .extracting("uid")
                            .isInstanceOf(String::class.java)

                }
                .verifyComplete()
    }

    @TestConfiguration
    class EdgeTopicControllerConfiguration {
        @Controller
        class TestTopicController(
                topicP: TopicPersistence<UUID>,
                topicInd: TopicIndexService<UUID, Map<String, String>>,
                topicSvc: PubSubTopicExchangeService<UUID, String>,
                userP: UserPersistence<UUID>,
                membershipP: MembershipPersistence<UUID>,
                membershipInd: MembershipIndexService<UUID, Map<String, String>>,
        ) :
                TopicController<UUID, String, Map<String, String>>(
                        topicP,
                        topicInd,
                        topicSvc,
                        userP,
                        membershipP,
                        membershipInd,
                        { "" },
                        Function { i -> mapOf(Pair(TopicIndexService.NAME, i.name)) },
                        Function { i -> mapOf(Pair(MembershipIndexService.MEMBEROF, i.id.toString())) }
                )
    }
}