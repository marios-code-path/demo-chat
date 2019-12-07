package com.demo.chat.test.controller.app

import com.demo.chat.TestChatUser
import com.demo.chat.TestChatUserKey
import com.demo.chat.UserCreateRequest
import com.demo.chat.UserRequestId
import com.demo.chat.controller.app.UserController
import com.demo.chat.domain.UserKey
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.assertj.core.api.Assertions
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
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
@Import(ConfigurationRSocket::class, RSocketUserTests.TestConfiguration::class)
class RSocketUserTests : ControllerTestBase() {
    @Autowired
    private lateinit var userIndex: UserIndexService

    @Autowired
    private lateinit var userPersistence: UserPersistence

    private val defaultImgUri = "http://"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val randomUser = TestChatUser(TestChatUserKey(randomUserId, randomHandle), randomName, defaultImgUri, Instant.now())

    @Test
    fun `should call user create`() {
        BDDMockito.given(userPersistence.key())
                .willReturn(Mono.just(UserKey.create(UUID.randomUUID())))

        BDDMockito.given(userPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(randomUser))

        BDDMockito.given(userIndex.add(anyObject(), anyObject()))
                .willReturn(Mono.empty())

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

    @Ignore
    fun `should list users`() {
        BDDMockito.given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(randomUser))

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

    @Configuration
    class TestConfiguration {
        @Controller
        class TestUserController(persistence: UserPersistence,
                               index: UserIndexService) : UserController(persistence, index)
    }
}