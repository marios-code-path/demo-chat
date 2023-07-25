package com.demo.chat.test.rsocket.controller.composite


import com.demo.chat.controller.composite.mapping.TopicServiceControllerMapping
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.core.*
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestChatMessageTopic
import com.demo.chat.test.TestChatRoomKey
import com.demo.chat.test.rsocket.RSocketTestBase
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*
import java.util.function.Function

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MockCoreServicesConfiguration::class, TopicControllerTests.TestTopicControllerConfiguration::class)
open class TopicControllerTests : RSocketTestBase() {
    private val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    @Autowired
    lateinit var topicPersistence: TopicPersistence<UUID>

    @Autowired
    lateinit var userPersistence: UserPersistence<UUID>

    @Autowired
    lateinit var pubsub: TopicPubSubService<UUID, String>

    @Autowired
    lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @Autowired
    lateinit var membershipPersistence: MembershipPersistence<UUID>

    val randomUserHandle = TestBase.randomAlphaNumeric(4) + "User"
    val randomUserId: UUID = UUID.fromString("4455814b-9886-499a-8547-55968e3183c6")

    val randomRoomName = TestBase.randomAlphaNumeric(6) + "Room"
    val randomTopicId: UUID = UUID.randomUUID()

    val room = TestChatMessageTopic(
        TestChatRoomKey(randomTopicId, randomRoomName),
        true
    )

    val roomWithMembers = TestChatMessageTopic(
        TestChatRoomKey(randomTopicId, randomRoomName),
        true
    )

    @Test
    fun `should receive list of rooms`() {
        BDDMockito
            .given(topicPersistence.all())
            .willReturn(Flux.just(room))

        StepVerifier
            .create(
                requester
                    .route("topic-list")
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
    fun `should create and join a room`() {
        val theRoomId = UUID.randomUUID()

        BDDMockito
            .given(topicPersistence.add(TestBase.anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(pubsub.open(TestBase.anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicIndex.add(TestBase.anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicPersistence.key())
            .willReturn(Mono.just(Key.funKey(theRoomId)))

        StepVerifier.create(
            requester.route("topic-add")
                .data(ByStringRequest(randomRoomName))
                .retrieveMono(Void::class.java)
        )
            .expectSubscription()
            .verifyComplete()

        val topicRoom = MessageTopic.create(Key.funKey(theRoomId), randomRoomName)

        BDDMockito
            .given(topicPersistence.get(TestBase.anyObject()))
            .willReturn(Mono.just(topicRoom))
        BDDMockito
            .given(membershipPersistence.key())
            .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))
        BDDMockito
            .given(membershipPersistence.add(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(membershipIndex.add(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(pubsub.sendMessage(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(pubsub.subscribe(TestBase.anyObject(), TestBase.anyObject()))
            .willReturn(Mono.empty())


        StepVerifier
            .create(
                requester
                    .route("topic-join")
                    .data(MembershipRequest(randomUserId, randomTopicId))
                    .retrieveMono(Void::class.java)
            )
            .expectSubscription()
            .verifyComplete()
    }

    @Test
    fun `should not join a non existent Room`() {
        BDDMockito
            .given(topicPersistence.get(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(membershipPersistence.key())
            .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))
        BDDMockito
            .given(membershipPersistence.add(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(pubsub.sendMessage(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(pubsub.subscribe(TestBase.anyObject(), TestBase.anyObject()))
            .willReturn(Mono.empty())

        StepVerifier
            .create(
                requester
                    .route("topic-join")
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
            .given(userPersistence.get(TestBase.anyObject()))
            .willReturn(
                Mono.just(
                    User.create(
                        Key.funKey(randomUserId), "NAME", randomUserHandle, "http://imageURI"
                    )
                )
            )

        BDDMockito
            .given(membershipPersistence.byIds(TestBase.anyObject()))
            .willReturn(
                Flux.just(
                    TopicMembership.create(
                        membershipId,
                        randomTopicId,
                        randomUserId
                    )
                )
            )

        BDDMockito
            .given(membershipIndex.findBy(TestBase.anyObject()))
            .willReturn(Flux.just(Key.funKey(membershipId)))

        StepVerifier
            .create(
                requester
                    .route("topic-members")
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
    class TestTopicControllerConfiguration {
        @Bean
        fun testTopicImpl(
            topicP: TopicPersistence<UUID>,
            topicInd: TopicIndexService<UUID, Map<String, String>>,
            pubsub: TopicPubSubService<UUID, String>,
            userP: UserPersistence<UUID>,
            membershipP: MembershipPersistence<UUID>,
            membershipInd: MembershipIndexService<UUID, Map<String, String>>,
        ) =
            TopicServiceImpl<UUID, String, Map<String, String>>(
                topicP,
                topicInd,
                pubsub,
                userP,
                membershipP,
                membershipInd,
                { "" },
                Function { i -> mapOf(Pair(TopicIndexService.NAME, i.name)) },
                Function { i -> mapOf(Pair(MembershipIndexService.MEMBEROF, UUIDUtil().toString(i.id))) },
                Function { i ->
                    mapOf(
                        Pair(
                            MembershipIndexService.MEMBER, "" +
                                    "${UUIDUtil().toString(i.uid)} AND ${MembershipIndexService.MEMBEROF}:${
                                        UUIDUtil().toString(
                                            i.roomId
                                        )
                                    }"
                        )
                    )
                }
            )

        @Controller
        class MessagingServiceServiceController(b: TopicServiceImpl<UUID, String, Map<String, String>>) :
            TopicServiceControllerMapping<UUID, String>, ChatTopicService<UUID, String> by b

    }
}