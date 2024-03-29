package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.clients.core.PersistenceClient
import com.demo.chat.client.rsocket.clients.core.config.UserPersistenceClient
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.TestChatUser
import com.demo.chat.test.TestChatUserKey
import com.demo.chat.test.anyObject
import com.demo.chat.test.randomAlphaNumeric
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.controller.core.UserPersistenceRequesterTests
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        UserPersistenceRequesterTests.UserPersistenceTestConfiguration::class
    ]
)
class PersistenceTests : RSocketTestBase() {
    @MockBean
    private lateinit var userPersistence: UserPersistence<UUID>

    private val defaultImgUri = "http://cdn.test.com/image.jpg"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val userKey = TestChatUserKey(randomUserId, randomHandle)
    private val randomUser = TestChatUser(userKey, randomName, defaultImgUri, Instant.now())

    private val svcPrefix = ""

    @Test
    fun `should add one`() {
        BDDMockito
            .given(userPersistence.add(anyObject()))
            .willReturn(Mono.empty())

        val client: PersistenceClient<UUID, User<UUID>> = UserPersistenceClient(svcPrefix, requester)

        StepVerifier
            .create(client.add(randomUser))
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

        val client: PersistenceClient<UUID, User<UUID>> = UserPersistenceClient(svcPrefix, requester)

        StepVerifier
            .create(client.all())
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()

                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrPropertyWithValue("handle", randomHandle)
                    .hasFieldOrPropertyWithValue("imageUri", defaultImgUri)
            }
            .assertNext {
                Assertions
                    .assertThat(it.key)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
            }
            .verifyComplete()
    }

    @Test
    fun `should get one`() {
        BDDMockito.given(userPersistence.get(anyObject()))
            .willReturn(Mono.just(randomUser))

        val client: PersistenceClient<UUID, User<UUID>> = UserPersistenceClient(svcPrefix, requester)

        StepVerifier
            .create(client.get(Key.funKey(userKey.id)))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()

                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrPropertyWithValue("handle", randomHandle)
                    .hasFieldOrPropertyWithValue("imageUri", defaultImgUri)
            }
            .verifyComplete()
    }
}