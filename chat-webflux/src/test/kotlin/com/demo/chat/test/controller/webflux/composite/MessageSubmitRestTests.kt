package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.InvalidRequestIdException
import com.demo.chat.domain.command.MessageSendResult
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.command.RequestConflictException
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
import java.util.concurrent.atomic.AtomicReference

/**
 * The REST submit and status routes. Method security is active in this slice,
 * and the broker allows `SEND` on one room alone. `KeyRefusalAdvice` is the
 * production advice, so each status comes from production code.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatMessageServiceController::class,
        MessageSubmitRestConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.message"])
class MessageSubmitRestTests {
    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    private fun result(outcome: CallerOutcome) =
        MessageSendResult(Receipt("c-rest", TestKeys.message(1L, 7L, ALLOWED_ROOM)), outcome, emptyMap())

    @BeforeEach
    fun `register the rooms`() {
        registry.register(ALLOWED_ROOM, ChatDomain.MESSAGE_TOPIC)
        registry.register(DENIED_ROOM, ChatDomain.MESSAGE_TOPIC)
        clearInvocations(beans.messageService())
    }

    private fun submit(room: Long, key: String?) = client.post().uri("/message/submit/$room")
        .contentType(MediaType.TEXT_PLAIN)
        .apply { if (key != null) header("Idempotency-Key", key) }
        .bodyValue("hello")
        .exchange()

    @Test
    fun `Completed answers 201 and the header becomes the request ID`() {
        // ArgumentCaptor.capture() answers null, and Kotlin refuses null for this parameter. The answer records the request.
        val seen = AtomicReference<MessageSubmitRequest<Long, String>>()
        given(beans.messageService().submit(anyObject())).willAnswer {
            seen.set(it.getArgument(0))
            Mono.just(result(CallerOutcome.COMPLETED))
        }
        submit(ALLOWED_ROOM, "rest-1").expectStatus().isCreated
            .expectBody(String::class.java).value { assertBody(it!!, "\"outcome\":\"COMPLETED\"", "c-rest") }
        assertThat(seen.get().requestId).isEqualTo("rest-1")
    }

    @Test
    fun `Pending and Accepted answer 202`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.PENDING)))
        submit(ALLOWED_ROOM, "rest-2").expectStatus().isAccepted
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.ACCEPTED)))
        submit(ALLOWED_ROOM, "rest-3").expectStatus().isAccepted
    }

    @Test
    fun `Incomplete answers 424`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result(CallerOutcome.INCOMPLETE)))
        submit(ALLOWED_ROOM, "rest-4").expectStatus().isEqualTo(424)
    }

    @Test
    fun `a conflict answers 409`() {
        given(beans.messageService().submit(anyObject())).willReturn(Mono.error(RequestConflictException("rest-5")))
        submit(ALLOWED_ROOM, "rest-5").expectStatus().isEqualTo(409)
    }

    @Test
    fun `review focus 1 - a missing or invalid header answers 400`() {
        submit(ALLOWED_ROOM, null).expectStatus().isBadRequest
        verify(beans.messageService(), never()).submit(anyObject())
        given(beans.messageService().submit(anyObject())).willReturn(Mono.error(InvalidRequestIdException("It holds a character outside visible ASCII.")))
        submit(ALLOWED_ROOM, "has space").expectStatus().isBadRequest
    }

    @Test
    fun `case 11 - a caller that may not send is refused with 403 and the service never runs`() {
        submit(DENIED_ROOM, "rest-6").expectStatus().isForbidden
        verify(beans.messageService(), never()).submit(anyObject())
    }

    @Test
    fun `review focus 5 - a status of another owner answers 404`() {
        given(beans.messageService().commandStatus(anyObject())).willReturn(Mono.error(NotFoundException))
        client.get().uri("/message/command/c-other").exchange().expectStatus().isNotFound
    }

    private fun assertBody(body: String, vararg parts: String) =
        assertThat(body).contains(*parts)

    companion object {
        const val ALLOWED_ROOM = 12345L
        const val DENIED_ROOM = 23456L
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class MessageSubmitRestConfiguration {

    /** The broker allows `SEND` on the allowed room, and nothing else. */
    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun submitBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        willAnswer { call ->
            val target = call.getArgument<VerifiedKey<Long>>(1)
            val action = call.getArgument<String>(2)
            Mono.just(target.key.id == MessageSubmitRestTests.ALLOWED_ROOM && action == "SEND")
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
