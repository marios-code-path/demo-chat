package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.config.SecretsStoreBeans
import com.demo.chat.controller.webflux.SecretsRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongSecretsStoreBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

/**
 * A credential belongs to a user, so each credential route resolves its id in
 * USER. A credential has no domain, so no route mints a credential key. See
 * `CHAT-avduuqwp`, C72 to C74 and E19.
 */
@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.secrets"])
@ContextConfiguration(
    classes = [
        TestLongSecretsStoreBeans::class,
        TestLongKeyServiceBeans::class,
        LongTypeUtilConfiguration::class,
        WebFluxTestConfiguration::class,
        SecretsRestController::class,
        // Production reaches the advice through the component scan. A slice names it.
        KeyRefusalAdvice::class,
    ]
)
class SecretsKeyVerificationTests {

    @Autowired
    private lateinit var beans: SecretsStoreBeans<Long>

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var client: WebTestClient

    // The store is a mock and a context bean, so each test starts from a clean mock.
    @BeforeEach
    fun `reset the store`() {
        Mockito.reset(beans.secretsStore())
    }

    @Test
    fun `a credential read resolves its owner in USER`() {
        val user = registry.register(2001L, ChatDomain.USER)
        val asked = AtomicReference<Key<Long>>()

        BDDMockito.given(beans.secretsStore().getStoredCredentials(anyObject()))
            .willAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                asked.set(invocation.arguments[0] as Key<Long>)
                Mono.just("hash")
            }

        client.get().uri("/secrets/{id}", user.id).exchange().expectStatus().isOk

        assertThat(asked.get()).isEqualTo(user)
    }

    @Test
    fun `a credential read with a message id is refused, and the store is not called`() {
        val message = registry.register(2002L, ChatDomain.MESSAGE)

        client.get().uri("/secrets/{id}", message.id).exchange().expectStatus().isNotFound

        Mockito.verifyNoInteractions(beans.secretsStore())
    }

    @Test
    fun `a credential compare with a message id is refused, and the store is not called`() {
        val message = registry.register(2003L, ChatDomain.MESSAGE)

        client.post().uri("/secrets/compare/{id}", message.id).bodyValue("secret")
            .exchange().expectStatus().isNotFound

        Mockito.verifyNoInteractions(beans.secretsStore())
    }

    @Test
    fun `a credential mint is refused with 501`() {
        client.put().uri("/secrets/add").bodyValue("secret").exchange()
            .expectStatus().isEqualTo(501)
            .expectBody(String::class.java).value { body -> assertThat(body).contains("KeyCredential") }

        Mockito.verifyNoInteractions(beans.secretsStore())
    }
}
