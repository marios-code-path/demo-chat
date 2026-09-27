package com.demo.chat.test.rsocket.controller.core

import com.demo.chat.test.rsocket.LongRSocketTestRegistry

import org.springframework.context.annotation.Bean

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.controller.resolve.KeyDomain

import com.demo.chat.test.key.TestKeys

import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.boot.security.autoconfigure.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestSecretStoreController::class,
    RSocketSecurityTestConfiguration::class,
    SecretsVerifierConfiguration::class,
)
class SecretsControllerTests : RSocketTestBase() {

    @MockitoBean
    private lateinit var secretStore: SecretsStore<Long>

    /** A credential owner is a user, so the key is registered in USER. */
    private val owner = LongRSocketTestRegistry.register(1L, ChatDomain.USER)

    @Test
    fun `test should add secret`() {
        BDDMockito.given(secretStore.addCredential(anyObject())).willReturn(Mono.empty())

        StepVerifier.create(
            metadataRequester.route("add")
                .data(Mono.just(KeyCredential(owner, "PASSWORDISTEST")), KeyCredential::class.java)
                .retrieveMono(Void::class.java)
        ).verifyComplete()
    }

    @Test
    fun `test should get secret`() {
        BDDMockito.given(secretStore.getStoredCredentials(anyObject())).willReturn(Mono.just("PASSWORDISTEST"))

        StepVerifier.create(
            metadataRequester.route("get").data(Mono.just(owner), Key::class.java).retrieveMono(String::class.java)
        ).assertNext { credential ->
            Assertions.assertThat(credential).isNotNull.isEqualTo("PASSWORDISTEST")
        }.verifyComplete()
    }
}
@Controller
class TestSecretStoreController<T>(private val that: SecretsStore<T>, private val verifier: KeyVerifier<T>) : SecretsStoreMapping<T>,
    SecretsStore<T> by that {
    override fun verifier(): KeyVerifier<T> = verifier
}

@TestConfiguration
class SecretsVerifierConfiguration {
    @Bean
    fun testKeyVerifier(): KeyVerifier<Long> = LongRSocketTestRegistry.verifier
}
