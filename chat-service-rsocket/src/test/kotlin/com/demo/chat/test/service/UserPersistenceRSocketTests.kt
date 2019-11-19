package com.demo.chat.test.service

import com.demo.chat.TestChatUser
import com.demo.chat.TestChatUserKey
import com.demo.chat.TestEventKey
import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import com.demo.chat.service.ChatPersistence
import com.demo.chat.service.ChatPersistenceRSocket
import com.demo.chat.service.ChatUserPersistence
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
@Import(RSocketTestConfiguration::class, UserPersistenceRSocketTests.UserPersistenceTestConfiguration::class)
class UserPersistenceRSocketTests : ServiceTestBase() {

    @Autowired
    lateinit var userPersistence: ChatUserPersistence

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

    @Configuration
    @Import(RSocketTestConfiguration::class)
    class UserPersistenceTestConfiguration {

        @Bean
        fun userModule() = com.demo.chat.module("USERS", User::class.java, TestChatUser::class.java)

        @Bean
        fun eventKeyModule() = com.demo.chat.module("EVENTKEY", EventKey::class.java, TestEventKey::class.java)

        @MockBean
        lateinit var userPersistence: ChatUserPersistence

        @Controller
        class RSocketUserPersistence(t: ChatPersistence<User>) : ChatPersistenceRSocket<User>(t)
    }
}