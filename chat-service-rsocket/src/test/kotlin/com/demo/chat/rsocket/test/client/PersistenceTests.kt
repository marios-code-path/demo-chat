package com.demo.chat.rsocket.test.client

import com.demo.chat.TestChatUser
import com.demo.chat.TestChatUserKey
import com.demo.chat.client.rsocket.PersistenceClient
import com.demo.chat.client.rsocket.UserClient
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.UserPersistence
import com.demo.chat.test.service.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveMono
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class,
        UserPersistenceRSocketTests.UserPersistenceTestConfiguration::class)
class PersistenceTests : ServiceTestBase() {
    @MockBean
    private lateinit var userPersistence: UserPersistence<UUID>


    private val defaultImgUri = "http://cdn.test.com/image.jpg"
    private val randomHandle = randomAlphaNumeric(4)
    private val randomName = randomAlphaNumeric(6)
    private val randomUserId = UUID.randomUUID()!!
    private val userKey = TestChatUserKey(randomUserId, randomHandle)
    private val randomUser = TestChatUser(userKey, randomName, defaultImgUri, Instant.now())

    @Test
    fun `should add one`() {
        BDDMockito
                .given(userPersistence.add(anyObject()))
                .willReturn(Mono.empty())

        val client: PersistenceClient<UUID, User<UUID>> = UserClient(requestor)

        StepVerifier
                .create(client.add(randomUser))
                .verifyComplete()
    }

    @Test
    fun `should find all users`() {
        BDDMockito.given(userPersistence.all())
                .willReturn(Flux.just(
                        randomUser, randomUser
                ))

        val client: PersistenceClient<UUID, User<UUID>> = UserClient(requestor)

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

        val client: PersistenceClient<UUID, User<UUID>> = UserClient(requestor)

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