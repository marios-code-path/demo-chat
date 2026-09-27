package com.demo.chat.test.rsocket.controller

import com.demo.chat.test.access.ChatAccessTestConfiguration
import org.springframework.context.annotation.Bean

import org.springframework.boot.test.context.TestConfiguration


import com.demo.chat.test.key.TestRoots

import com.demo.chat.domain.knownkey.ChatDomain

import org.springframework.security.access.prepost.PreAuthorize

import com.demo.chat.service.core.VerifiedKey

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.test.key.TestKeys

import com.demo.chat.security.access.core.SecretsStoreAccess
import com.demo.chat.controller.core.mapping.SecretsStoreMapping
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.test.anyObject
import com.demo.chat.test.rsocket.RSocketSecurityTestConfiguration
import com.demo.chat.test.rsocket.RSocketTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.stereotype.Controller
import org.springframework.test.context.ContextConfiguration
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ContextConfiguration(
    classes = [
        TestSecretStoreController::class,
        RSocketSecurityTestConfiguration::class,
        ChatAccessTestConfiguration::class,
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecretsControllerTests : RSocketTestBase("user", "password") {

    @MockitoBean
    private lateinit var secretStore: SecretsStore<Long>

    @MockitoBean
    private lateinit var rootKeys: RootKeys<Long>

    @MockitoBean
    private lateinit var accessBroker: AccessBroker<Long>


    @Test
    fun `no permissions, access denied on key fetch`() {
        BDDMockito
            .given(secretStore.getStoredCredentials(anyObject()))
            .willReturn(Mono.just("1234567890"))

        BDDMockito
            .given(accessBroker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))

        // The key must verify, so only the access check can refuse the call.
        BDDMockito
            .given(rootKeys.of(ChatDomain.USER))
            .willReturn(Key.root(TestRoots.of(0L)))

        StepVerifier.create(
            requester
                .route("get")
                .metadata(UsernamePasswordMetadata("user", "password"), SIMPLE_AUTH)
                .data(Mono.just(TestKeys.key(1L)), Key::class.java)

                .retrieveMono(String::class.java)
        ).expectErrorMatches { it.message!!.contains("Access Denied") }
            .verify()
    }
}

/**
 * The route calls the store on this object, so a check on the store method
 * never runs through the proxy. The check sits on the route. See
 * CHAT-avduuqwp, T4.
 */
@Controller
class TestSecretStoreController<T>(private val that: SecretsStore<T>, private val verifier: KeyVerifier<T>) :
    SecretsStoreMapping<T>, SecretsStoreAccess<T>, SecretsStore<T> by that {
    override fun verifier(): KeyVerifier<T> = verifier

    @PreAuthorize("@chatAccess.hasAccessTo(#key.key, 'READ')")
    override fun getRoute(key: VerifiedKey<T>): Mono<String> = super.getRoute(key)
}
