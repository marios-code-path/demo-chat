package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.Message
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
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The REST route for `listMessages`, the stored messages of one room. See
 * `CHAT-evxtlmfs`.
 *
 * **Method security is active in this slice.** The broker allows `SUBSCRIBE`
 * on one room alone. So the status of each case comes from the production
 * check, and the call count is the control.
 *
 * **`KeyRefusalAdvice` is the production advice.** It renders the refusal as
 * 403 and the unknown id as 404.
 *
 * `MessageRestAccessTests` holds the refusal of the other message routes.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        ListMessagesRestConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class ListMessagesRestTests {
    @Autowired lateinit var wireMapper: tools.jackson.databind.json.JsonMapper

    @Test
    fun `Long and UUID histories match the shared MCP fixtures`() {
        MessagingRestFixtures.both(wireMapper).forEach { it.checkHistory() }
    }


    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    /** Both rooms resolve in their domain. The unknown room is not registered. */
    @BeforeEach
    fun `register the rooms`() {
        registry.register(ALLOWED_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(DENIED_ROOM, ChatDomain.MESSAGE_TOPIC)
        clearInvocations(beans.messageService())
        given(beans.messageService().listMessages(anyObject())).willReturn(
            Flux.just(
                Message.create(TestKeys.message(1L, 7L, ALLOWED_ROOM), "first", true),
                Message.create(TestKeys.message(2L, 7L, ALLOWED_ROOM), "second", true),
            )
        )
    }

    @Test
    fun `a caller that may subscribe reads the stored messages and the response completes`() {
        val body = client.get().uri("/message/list/$ALLOWED_ROOM").exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .expectBody(String::class.java)
            .returnResult().responseBody!!

        assertThat(body.lines().filter { it.isNotBlank() })
            .describedAs("one NDJSON line per stored message")
            .hasSize(2)
        assertThat(body).contains("\"first\"", "\"second\"")
        verify(beans.messageService(), times(1)).listMessages(anyObject())
    }

    @Test
    fun `a caller that may not subscribe is refused with 403 and the service never runs`() {
        client.get().uri("/message/list/$DENIED_ROOM").exchange()
            .expectStatus().isForbidden

        verify(beans.messageService(), never()).listMessages(anyObject())
    }

    @Test
    fun `an unknown room answers 404 and the service never runs`() {
        client.get().uri("/message/list/$UNKNOWN_ROOM").exchange()
            .expectStatus().isNotFound

        verify(beans.messageService(), never()).listMessages(anyObject())
    }

    companion object {
        const val ALLOWED_ROOM = 12345L
        const val DENIED_ROOM = 23456L
        const val UNKNOWN_ROOM = 99999L
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class ListMessagesRestConfiguration {

    /** The broker allows `SUBSCRIBE` on the allowed room, and nothing else. */
    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun listBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        willAnswer { call ->
            val target = call.getArgument<VerifiedKey<Long>>(1)
            val action = call.getArgument<String>(2)
            Mono.just(target.key.id == ListMessagesRestTests.ALLOWED_ROOM && action == "SUBSCRIBE")
        }.given(broker).hasAccessByPrincipal(anyObject(), anyObject(), anyObject())
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
