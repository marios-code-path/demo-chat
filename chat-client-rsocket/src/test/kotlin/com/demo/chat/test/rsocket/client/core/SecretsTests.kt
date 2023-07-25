package com.demo.chat.test.rsocket.client.core

import com.demo.chat.client.rsocket.clients.core.SecretStoreClient
import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.controller.core.SecretsControllerTests
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        SecretsControllerTests.TestSecretsControllerConfiguration::class
    ]
)
class SecretsTests : RSocketTestBase() {
    @MockBean
    private lateinit var secretStore: SecretsStore<Long>
    private val svcPrefix = ""

    @Test
    fun `test should add credential`() {
        BDDMockito
            .given(secretStore.addCredential(anyObject()))
            .willReturn(Mono.empty())

        val client = SecretStoreClient<Long>(svcPrefix, requester)

        StepVerifier
            .create(client.addCredential(KeyCredential(Key.funKey(1L), "ABCDEFG")))
            .verifyComplete()
    }

    @Test
    fun `test should get credential`() {
        BDDMockito
            .given(secretStore.getStoredCredentials(anyObject()))
            .willReturn(Mono.just("ABCDEFG"))

        val client = SecretStoreClient<Long>(svcPrefix, requester)

        StepVerifier
            .create(client.getStoredCredentials(Key.funKey(1L)))
            .assertNext { cred ->
                Assertions
                    .assertThat(cred)
                    .isNotNull
                    .isEqualTo("ABCDEFG")
            }
            .verifyComplete()
    }

    @Test
    fun `should compare credentials`() {
        BDDMockito
            .given(secretStore.compareSecret(anyObject()))
            .willReturn(Mono.just(true))

        val client = SecretStoreClient<Long>(svcPrefix, requester)

        StepVerifier
            .create(client.compareSecret(KeyCredential(Key.funKey(1L), "ABCDEFG")))
            .assertNext { cred ->
                Assertions
                    .assertThat(cred)
                    .isNotNull
                    .hasToString(true.toString())
            }
            .verifyComplete()
    }

}