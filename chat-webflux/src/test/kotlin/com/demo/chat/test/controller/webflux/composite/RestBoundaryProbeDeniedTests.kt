package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatTopicServiceController
import com.demo.chat.domain.MessageTopic
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
import com.demo.chat.test.key.TestKeys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The REST boundary probe, denied answer, for `CHAT-znprrzhn`.
 *
 * **A refusal must arrive, and the service must never run.** An assertion
 * that only reads a status code cannot tell a refusal from a route that was
 * never mapped. The call count is the control.
 *
 * **The delegate answers a room, and not null.** So the facade route answers
 * 200 with a body when it crosses no proxy. A delegate that answered null
 * would answer 500 instead, and a refusal must not be confused with a broken
 * stub.
 *
 * **The class passes, and both assertions of the gate run.** Each route answers
 * 403 and each call count reads zero.
 *
 * **`KeyRefusalAdvice` is the real production advice, and not a fixture.** It is
 * in this slice on purpose. Method security throws `AuthorizationDeniedException`
 * from the handler, and no other type in this repository renders it, so a refusal
 * answered 500 before it gained that handler. A 500 would run the status
 * assertion and stop before the call count assertion. See `CHAT-znprrzhn`.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        RestBoundaryProbeDeniedConfiguration::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestBoundaryProbeDeniedTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    /** The path id resolves in its domain, as a minted id does. */
    @BeforeEach
    fun `register the path id`() {
        registry.register(12345L, ChatDomain.MESSAGE_TOPIC)
    }

    @Test
    fun `a denied caller is refused at the mapped route and listRooms is never called`() {
        client.get().uri("/topic/list").exchange()
            .expectStatus().isForbidden

        verify(beans.topicService(), never()).listRooms()
    }

    /**
     * **The facade route is the finding of Task 2.** The method calls `getRoom`
     * on its own object, so that call crosses no proxy and the member's check
     * never ran. The facade method carries its own check for that reason. This
     * test is what pins that repair.
     */
    @Test
    fun `a denied caller is refused at the facade route and getRoom is never called`() {
        client.get().uri("/topic/id/12345").exchange()
            .expectStatus().isForbidden

        verify(beans.topicService(), never()).getRoom(anyObject())
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class RestBoundaryProbeDeniedConfiguration {

    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun probeBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(false))
        given(broker.hasAccessByKeyId(anyObject(), anyObject(), anyObject()))
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

    /**
     * The delegate answers one room on each route.
     *
     * **This is the control of the facade finding.** A guarded route never
     * reaches it. An unguarded route answers 200 and the room is this one.
     * A delegate that answered null would answer 500, and that reading is
     * not the reading this task is looking for.
     */
    @Bean
    fun probeRooms(beans: CompositeServiceBeans<Long, String>): Boolean {
        val room: MessageTopic<Long> = MessageTopic.create(TestKeys.key(1L), "room-one")
        given(beans.topicService().listRooms()).willReturn(Flux.just(room))
        given(beans.topicService().getRoom(anyObject())).willReturn(Mono.just(room))
        return true
    }
}
