package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.secure.service.controller.SecretStoreController
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.TestConfigurationRSocket
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestConfigurationRSocket::class, SecretsControllerTests.TestSecretsControllerConfiguration::class)
class SecretsControllerTests : RSocketTestBase() {

    @MockBean
    private lateinit var secretStore: SecretsStore<Long>

    @Test
    fun `test should add secret`() {
        BDDMockito
            .given(secretStore.addCredential(anyObject()))
            .willReturn(Mono.empty())

        StepVerifier
            .create(
                requester
                    .route("add")
                    .data(Mono.just(KeyCredential(Key.funKey(1L), "PASSWORDISTEST")), KeyCredential::class.java)
                    .retrieveMono(Void::class.java)
            )
            .verifyComplete()
    }

    @Test
    fun `test should get secret`() {
        BDDMockito
            .given(secretStore.getStoredCredentials(anyObject()))
            .willReturn(Mono.just("PASSWORDISTEST"))

        StepVerifier
            .create(
                requester
                    .route("get")
                    .data(Mono.just(Key.funKey(1L)), Key::class.java)
                    .retrieveMono(String::class.java)
            )
            .assertNext { credential ->
                Assertions
                    .assertThat(credential)
                    .isNotNull
                    .isEqualTo("PASSWORDISTEST")
            }
            .verifyComplete()
    }


    @TestConfiguration
    class TestSecretsControllerConfiguration {
        @Controller
        class TestSecretsController<T>(secretsStore: SecretsStore<T>) : SecretStoreController<T>(secretsStore)
    }
}