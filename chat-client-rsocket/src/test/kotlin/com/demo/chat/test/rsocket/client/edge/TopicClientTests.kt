package com.demo.chat.test.rsocket.client.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.ByNameRequest
import com.demo.chat.MembershipRequest
import com.demo.chat.test.anyObject
import com.demo.chat.client.rsocket.composite.TopicClient
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.test.rsocket.TestConfigurationRSocket
import com.demo.chat.test.rsocket.controller.edge.EdgeTopicControllerTests
import com.demo.chat.test.rsocket.controller.edge.MockCoreServicesConfiguration
import io.rsocket.exceptions.ApplicationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestConfigurationRSocket::class,
    MockCoreServicesConfiguration::class,
    EdgeTopicControllerTests.EdgeTopicControllerConfiguration::class
)
class TopicClientTests : EdgeTopicControllerTests() {
    lateinit var client: TopicClient<UUID, String>
    private val svcPrefix = ""

    @BeforeEach
    fun setUp() {
        client = TopicClient<UUID, String>(svcPrefix, requester)
    }

    @Test
    fun `client should create a room receive Void response`() {
        BDDMockito
            .given(topicPersistence.add(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicServiceTopic.open(anyObject()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(topicPersistence.key())
            .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))

        StepVerifier.create(client.addRoom(ByNameRequest(randomRoomName)))
            .expectSubscription()
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
    fun `client should not join a non existent Room`() {
        BDDMockito
            .given(topicPersistence.get(anyObject()))
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
                        Key.funKey(randomUserId), "NAME", randomUserHandle, "http://imageURI"
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
            .willReturn(Flux.just(Key.funKey(membershipId)))

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