package com.demo.chat.test.rsocket.client.composite

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.test.rsocket.RSocketTestRegistry

import com.demo.chat.test.key.TestKeys

import com.demo.chat.client.rsocket.clients.composite.TopicClient
import com.demo.chat.domain.*
import com.demo.chat.service.core.*
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestChatMessageTopic
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.controller.composite.MockCoreServicesConfiguration
import com.demo.chat.test.rsocket.controller.composite.TopicControllerTests
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        MockCoreServicesConfiguration::class,
        TopicControllerTests.TestTopicControllerConfiguration::class
    ]
)
class TopicClientTests : RSocketTestBase() {
    lateinit var client: TopicClient<UUID, String>
    private val svcPrefix = ""

    @MockitoBean
    lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    @MockitoBean
    lateinit var topicPersistence: TopicPersistence<UUID>

    @MockitoBean
    lateinit var userPersistence: UserPersistence<UUID>

    @MockitoBean
    lateinit var pubsub: TopicPubSubService<UUID, String>

    @MockitoBean
    lateinit var membershipIndex: MembershipIndexService<UUID, Map<String, String>>

    @MockitoBean
    lateinit var membershipPersistence: MembershipPersistence<UUID>

    val randomUserHandle = TestBase.randomAlphaNumeric(4) + "User"
    val randomUserId: UUID = RSocketTestRegistry.register(UUID.fromString("4455814b-9886-499a-8547-55968e3183c6"), ChatDomain.USER).id

    val randomRoomName = TestBase.randomAlphaNumeric(6) + "Room"
    val randomTopicId: UUID = RSocketTestRegistry.registered(ChatDomain.MESSAGE_TOPIC).id

    val room = TestChatMessageTopic(
        TestKeys.key(randomTopicId), randomRoomName,
        true
    )

    val roomWithMembers = TestChatMessageTopic(
        TestKeys.key(randomTopicId), randomRoomName,
        true
    )

    @BeforeEach
    fun setUp() {
        client = TopicClient<UUID, String>(svcPrefix, requester)
    }

    @Test
    fun `client should create a room`() {
        Hooks.onOperatorDebug()
        val keyId = UUID.randomUUID()

        BDDMockito
            .given(topicPersistence.add(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(pubsub.open(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicPersistence.key())
            .willReturn(Mono.just(TestKeys.key(keyId)))

        // addRoom rejects a duplicate name, so it queries the topic index
        // first. An empty result means the name is free.
        BDDMockito
            .given(topicIndex.findBy(anyObject()))
            .willReturn(Flux.empty())

        BDDMockito
            .given(topicIndex.add(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier.create(client.addRoom(ByStringRequest(randomRoomName)))
            .expectSubscription()
            .assertNext { key ->

                Assertions
                    .assertThat(key)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("id", keyId)
            }
            .verifyComplete()
    }

    @Test
    fun `client should receive list of rooms`() {
        BDDMockito
            .given(topicPersistence.all())
            .willReturn(Flux.just(room))

        StepVerifier
            .create(client.listRooms())
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrProperty("key")
                    .hasFieldOrPropertyWithValue("data", randomRoomName)
                    .extracting("key")
                    .hasFieldOrPropertyWithValue("id", randomTopicId)
            }
            .verifyComplete()
    }

    @Test
    fun `should create and join a room`() {
        val theRoomId = UUID.randomUUID()
        Hooks.onOperatorDebug()

        BDDMockito
            .given(topicPersistence.add(TestBase.anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(pubsub.open(TestBase.anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicPersistence.key())
            .willReturn(Mono.just(TestKeys.key(theRoomId)))

        // addRoom rejects a duplicate name, so it queries the topic index
        // first. An empty result means the name is free.
        BDDMockito
            .given(topicIndex.findBy(anyObject()))
            .willReturn(Flux.empty())

        BDDMockito
            .given(topicIndex.add(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(membershipIndex.add(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(membershipPersistence.add(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier.create(client.addRoom(ByStringRequest(randomRoomName)))
            .expectSubscription()
            .assertNext { key ->

                Assertions
                    .assertThat(key)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("id", theRoomId)
            }
            .verifyComplete()

        val topicRoom = MessageTopic.create(TestKeys.key(theRoomId), randomRoomName)

        BDDMockito
            .given(topicIndex.add(anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(topicPersistence.get(TestBase.anyObject()))
            .willReturn(Mono.just(topicRoom))
        BDDMockito
            .given(membershipPersistence.key())
            .willReturn(Mono.just(TestKeys.key(UUID.randomUUID())))
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
            .create(client.joinRoom(MembershipRequest(randomUserId, randomTopicId)))
            .expectSubscription()
            .verifyComplete()
    }


    @Test
    fun `client should not join a non existent Room`() {
        BDDMockito
            .given(topicPersistence.get(TestBase.anyObject()))
            .willReturn(Mono.empty())
        BDDMockito
            .given(membershipPersistence.key())
            .willReturn(Mono.just(TestKeys.key(UUID.randomUUID())))
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
            .create(client.joinRoom(MembershipRequest(randomUserId, randomTopicId)))
            .expectError(ApplicationErrorException::class.java)
            .verify()
    }

    @Test // TODO TopicMembership<T> does not decode/encode - I switched to a String return
    fun `client should fetch topic member list`() {
        val membershipId = UUID.randomUUID()

        BDDMockito
            .given(userPersistence.get(anyObject()))
            .willReturn(
                Mono.just(
                    User.create(
                        TestKeys.key(randomUserId), "NAME", randomUserHandle, "http://imageURI"
                    )
                )
            )

        BDDMockito
            .given(membershipPersistence.byIds(anyObject()))
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
            .given(membershipIndex.findBy(anyObject()))
            .willReturn(Flux.just(TestKeys.key(membershipId)))

        StepVerifier
            .create(client.roomMembers(ByIdRequest(randomTopicId)))
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
}