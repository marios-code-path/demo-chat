package com.demo.chat.test.controller.app

import com.demo.chat.ByIdRequest
import com.demo.chat.TestChatUser
import com.demo.chat.TestChatUserKey
import com.demo.chat.UserCreateRequest
import com.demo.chat.controller.app.UserController
import com.demo.chat.domain.Key
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.assertj.core.api.Assertions
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class, RSocketUserTests.TestConfiguration::class)
class RSocketUserTests : ControllerTestBase() {
    @Autowired
    private lateinit var userIndex: UserIndexService<UUID>

    @Autowired
    private lateinit var userPersistence: UserPersistence<UUID>

    private val defaultImgUri = "http://"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val randomUser = TestChatUser(TestChatUserKey(randomUserId, randomHandle), randomName, defaultImgUri, Instant.now())

    @Test
    fun `should call user create`() {
        BDDMockito.given(userPersistence.key())
                .willReturn(Mono.just(Key.anyKey(UUID.randomUUID())))

        BDDMockito.given(userPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(randomUser))

        BDDMockito.given(userIndex.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("user-add")
                                .data(UserCreateRequest(randomName, randomHandle, defaultImgUri))
                                .retrieveMono<Void>()
                )
                .verifyComplete()

        StepVerifier
                .create(
                        requestor
                                .route("user-by-id")
                                .data(ByIdRequest(randomUserId))
                                .retrieveMono<TestChatUser>()
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
                .data(ByIdRequest(randomUserId))
                .retrieveFlux<TestChatUser>()
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
        class TestUserController(persistence: UserPersistence<UUID>,
                                 index: UserIndexService<UUID>) : UserController<UUID>(persistence, index)
    }
}