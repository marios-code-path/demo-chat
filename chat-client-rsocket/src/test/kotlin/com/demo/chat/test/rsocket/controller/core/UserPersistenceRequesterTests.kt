package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.TestChatUser
import com.demo.chat.test.TestChatUserKey
import com.demo.chat.test.anyObject
import com.demo.chat.test.randomAlphaNumeric
import com.demo.chat.test.rsocket.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    UserPersistenceRequesterTests.UserPersistenceTestConfiguration::class
)
class UserPersistenceRequesterTests : RSocketTestBase() {

    @MockBean
    private lateinit var userPersistence: UserPersistence<UUID>

    private val defaultImgUri = "http://cdn.test.com/image.jpg"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val userKey = TestChatUserKey(randomUserId, randomHandle)
    private val randomUser = TestChatUser(userKey, randomName, defaultImgUri, Instant.now())

    @Test
    fun contextLoads() {
    }

    @Test
    fun `should add one`() {
        BDDMockito
            .given(userPersistence.add(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier
            .create(
                requester
                    .route("add")
                    .data(Mono.just(randomUser), TestChatUser::class.java)
                    .retrieveMono(Void::class.java)
            )
            .verifyComplete()
    }

    @Test
    fun `should find all users`() {
        BDDMockito.given(userPersistence.all())
            .willReturn(
                Flux.just(
                    randomUser, randomUser
                )
            )

        StepVerifier
            .create(
                requester
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
                requester
                    .route("get")
                    .data(Mono.just(Key.funKey(userKey.id)))
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

    @TestConfiguration
    class UserPersistenceTestConfiguration {
        @Controller
        class TestPersistenceController<T>(up: UserPersistence<T>) : PersistenceServiceController<T, User<T>>(up)
    }
}