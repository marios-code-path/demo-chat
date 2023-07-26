package com.demo.chat.test.rsocket.client.composite

import com.demo.chat.client.rsocket.clients.composite.UserClient
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestChatUser
import com.demo.chat.test.rsocket.controller.composite.UserControllerTests
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import org.springframework.messaging.rsocket.retrieveFlux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

class UserClientTests : UserControllerTests() {
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


    fun `client should list users`() {
        BDDMockito.given(userPersistence.get(TestBase.anyObject()))
            .willReturn(Mono.just(randomUser))

        StepVerifier.create(
            requester
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