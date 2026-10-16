package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.ByIdRequest

import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
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
import org.mockito.BDDMockito.willAnswer
import org.mockito.invocation.InvocationOnMock
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * `GET /message/id/{id}` reads the message, and then checks `SUBSCRIBE` on
 * its room. See `CHAT-erkoxybm`. The broker allows `SUBSCRIBE` on
 * [OPEN_ROOM] alone.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        MessageByIdRestRuleConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class MessageByIdRestRuleTests {
    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    @BeforeEach
    fun `register the ids and stub the reads`() {
        registry.register(OPEN_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(CLOSED_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(OPEN_MESSAGE, ChatDomain.MESSAGE)
        registry.register(CLOSED_MESSAGE, ChatDomain.MESSAGE)
        given(beans.messageService().messageById(ByIdRequest(OPEN_MESSAGE))).willReturn(Mono.just(messageIn(OPEN_MESSAGE, OPEN_ROOM)))
        given(beans.messageService().messageById(ByIdRequest(CLOSED_MESSAGE))).willReturn(Mono.just(messageIn(CLOSED_MESSAGE, CLOSED_ROOM)))
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `a caller with SUBSCRIBE on the room reads the message`() {
        client.get().uri("/message/id/$OPEN_MESSAGE").exchange().expectStatus().isOk
    }

    @Test
    @WithLongCustomChatUser(userId = 1L, roles = ["TEST"])
    fun `a caller without SUBSCRIBE on the room is refused`() {
        client.get().uri("/message/id/$CLOSED_MESSAGE").exchange().expectStatus().isForbidden
    }

    private fun messageIn(id: Long, room: Long): Message<Long, String> =
        Message.create(SimpleMessageKey(id, 1L, 1L, room), "text", true)

    companion object {
        const val OPEN_ROOM = 45601L
        const val CLOSED_ROOM = 45602L
        const val OPEN_MESSAGE = 45611L
        const val CLOSED_MESSAGE = 45612L
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class MessageByIdRestRuleConfiguration {

    /** The broker allows SUBSCRIBE on the open room and denies every other check. */
    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun roomBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        willAnswer { call: InvocationOnMock ->
            val target = call.getArgument<VerifiedKey<Long>>(1)
            val permission = call.getArgument<String>(2)
            Mono.just(target.key.id == MessageByIdRestRuleTests.OPEN_ROOM && permission == "SUBSCRIBE")
        }.given(broker).hasAccessByPrincipal(anyObject(), anyObject(), anyObject())
        return broker
    }

    /** **The name matters.** Every expression resolves the bean `chatAccess`. */
    @Bean
    fun chatAccess(broker: AccessBroker<Long>, rootKeys: RootKeys<Long>, verifier: KeyVerifier<Long>): SpringSecurityAccessBrokerService<Long> =
        SpringSecurityAccessBrokerService(broker, rootKeys, verifier)
}
