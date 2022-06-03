package com.demo.chat.test.rsocket.client.edge

import com.demo.chat.ByIdRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.client.rsocket.edge.UserClient
import com.demo.chat.domain.Key
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestChatUser
import com.demo.chat.test.rsocket.controller.edge.EdgeUserControllerTests
import com.demo.chat.test.rsocket.controller.edge.MockCoreServicesConfiguration
import org.assertj.core.api.Assertions
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MockCoreServicesConfiguration::class, EdgeUserControllerTests.TestConfiguration::class)
class UserClientTests : EdgeUserControllerTests() {
    private lateinit var client: UserClient<UUID>
    private val svcPrefix = ""

    @BeforeEach
    fun setUp() {
        client = UserClient(svcPrefix, requester)
    }

    @Test
    fun `client should create`() {
        BDDMockito.given(userPersistence.key())
                .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))

        BDDMockito.given(userPersistence.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(userIndex.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(client.addUser(UserCreateRequest(randomName, randomHandle, defaultImgUri)))
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull()
                }
                .verifyComplete()
    }


    @Test
    fun `client should find user`() {
        BDDMockito.given(userPersistence.get(TestBase.anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(client.findByUserId(ByIdRequest(randomUserId)))
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.key)
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Ignore
    fun `client should list users`() {
        BDDMockito.given(userPersistence.get(TestBase.anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier.create(requester
                .route("user-by-ids")
                .data(ByIdRequest(randomUserId))
                .retrieveFlux<TestChatUser>()
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.key)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("id", randomUserId)
                }
                .verifyComplete()
    }

}