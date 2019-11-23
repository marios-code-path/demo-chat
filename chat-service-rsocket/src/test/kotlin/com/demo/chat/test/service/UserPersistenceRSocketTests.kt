package com.demo.chat.test.service

import com.demo.chat.TestChatUser
import com.demo.chat.TestChatUserKey
import com.demo.chat.TestEventKey
import com.demo.chat.controller.rsocket.RSocketUserPersistence
import com.demo.chat.controller.service.PersistenceServiceController
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.service.ChatPersistence
import com.demo.chat.service.UserPersistence
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
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
@Import(TestConfigurationRSocket::class, UserPersistenceRSocketTests.UserPersistenceTestConfiguration::class)
class UserPersistenceRSocketTests : ServiceTestBase() {

    @Autowired
    lateinit var userPersistence: UserPersistence

    private val defaultImgUri = "http://cdn.test.com/image.jpg"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val userKey = TestChatUserKey(randomUserId, randomHandle)
    private val randomUser = TestChatUser(userKey, randomName, defaultImgUri, Instant.now())

    @Test
    fun `should save one`() {
        BDDMockito
                .given(userPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requestor
                                .route("add")
                                .data(Mono.just(randomUser), TestChatUser::class.java)
                                .retrieveMono(Void::class.java)
                )
                .verifyComplete()
    }

    @Test
    fun `should find all users`() {
        BDDMockito.given(userPersistence.all())
                .willReturn(Flux.just(
                        randomUser, randomUser
                ))

        StepVerifier
                .create(
                        requestor
                                .route("all")
                                .retrieveFlux(TestChatUser::class.java)
                )
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
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Test
    fun `should get one`() {
        BDDMockito.given(userPersistence.get(anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requestor
                                .route("get")
                                .data(Mono.just(TestEventKey(userKey.id)))
                                .retrieveMono(TestChatUser::class.java)
                )
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

    class UserPersistenceTestConfiguration {
        @MockBean
        lateinit var userPersistence: UserPersistence

        @Bean
        fun persistenceController() = RSocketUserPersistence(userPersistence)
    }
}