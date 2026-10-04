package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.LongTypeUtilConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.controller.webflux.config.WithLongCustomChatUser
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * The REST message routes under method security. See `CHAT-evxtlmfs`.
 *
 * **Before the repair, every `/message` route answered 404 here.** The JDK
 * proxy hid the class level `@RequestMapping`. Measured on 2026-10-03.
 *
 * **A 403 proves two facts.** The route is mapped, and its own check ran. A
 * facade call crosses no proxy, so the check of the member alone never runs.
 * The call count is the control: the service never runs.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        MessageRestAccessConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class MessageRestAccessTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    /** Each path id resolves in its domain, as a minted id does. */
    @BeforeEach
    fun `register the path ids`() {
        registry.register(ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(MESSAGE, ChatDomain.MESSAGE)
        clearInvocations(beans.messageService())
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `a denied caller is refused at every message route and the service never runs`() {
        client.get().uri("/message/topic/$ROOM").exchange().expectStatus().isForbidden
        client.get().uri("/message/id/$MESSAGE").exchange().expectStatus().isForbidden
        client.post().uri("/message/send/$ROOM").contentType(MediaType.TEXT_PLAIN)
            .bodyValue("hello").exchange().expectStatus().isForbidden

        verify(beans.messageService(), never()).listenTopic(anyObject())
        verify(beans.messageService(), never()).messageById(anyObject())
        verify(beans.messageService(), never()).send(anyObject())
    }

    companion object {
        const val ROOM = 23456L
        const val MESSAGE = 34567L
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class MessageRestAccessConfiguration {

    /** The broker denies every check. */
    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun denyingBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))
        return broker
    }

    /** **The name matters.** Every expression resolves the bean `chatAccess`. */
    @Bean
    fun chatAccess(
        broker: AccessBroker<Long>,
        rootKeys: RootKeys<Long>,
        verifier: KeyVerifier<Long>,
    ): SpringSecurityAccessBrokerService<Long> =
        SpringSecurityAccessBrokerService(broker, rootKeys, verifier)
}
