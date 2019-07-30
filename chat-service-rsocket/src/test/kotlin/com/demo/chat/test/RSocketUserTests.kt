package com.demo.chat.test

import com.demo.chat.*
import com.demo.chat.controllers.UserController
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserPersistence
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
@Import(RSocketTestConfig::class, UserController::class)
class RSocketUserTests : RSocketTestBase() {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var userPersistence: ChatUserPersistence<out User, UserKey>

    private val defaultImgUri = "http://"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val randomUser = TestChatUser(TestChatUserKey(randomUserId, randomHandle), randomName, defaultImgUri, Instant.now())

    @Test
    fun `should call user create`() {
        BDDMockito.given(userPersistence.key(anyObject()))
                .willReturn(Mono.just(UserKey.create(UUID.randomUUID(), randomHandle)))

        BDDMockito.given(userPersistence.add(anyObject(), anyObject(), anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(userPersistence.getById(anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requestor
                                .route("user-add")
                                .data(UserCreateRequest(randomName, randomHandle, defaultImgUri))
                                .retrieveMono(Void::class.java)
                )
                .verifyComplete()

        StepVerifier
                .create(
                        requestor
                                .route("user-by-id")
                                .data(UserRequestId(randomUserId))
                                .retrieveMono(TestChatUser::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                }
                .verifyComplete()
    }


    @Test
    fun `should get a user by handle`() {
        BDDMockito.given(userPersistence.getByHandle(anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requestor
                                .route("user-by-handle")
                                .data(UserRequest(randomHandle))
                                .retrieveMono(TestChatUser::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("id", randomUserId)
                }
                .verifyComplete()
    }

    @Test
    fun `should list users`() {
        BDDMockito.given(userPersistence.findByIds(anyObject()))
                .willReturn(Flux.just(randomUser))

        StepVerifier.create(requestor
                .route("user-by-ids")
                .data(Flux.just(UserRequestId(randomUserId)), UserRequestId::class.java)
                .retrieveFlux(TestChatUser::class.java)
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("id", randomUserId)
                }
                .verifyComplete()
    }
}