package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.config.CompositeServiceBeans
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
import org.mockito.Mockito.times
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
 * The REST boundary probe, allowed answer, for `CHAT-znprrzhn`.
 *
 * **This is the control.** The broker answers true, so a refusal here means
 * the proxy never saw the annotation.
 *
 * `GET /topic/list` reaches `listRooms` directly, and it crosses the proxy.
 * `GET /topic/id/{id}` reaches `restGetRoom`, a default method on
 * `ChatTopicServiceRestMapping` that calls `getRoom` on its own object. That
 * call is not a route, so it crosses the proxy only if the annotation reaches
 * the controller through the `TopicServiceAccess` supertype.
 *
 * **A status code alone proves nothing.** The delegate answers one room and
 * each route asserts one call, so an empty 200 and a reached service cannot
 * be the same answer.
 *
 * **This class passes, and it is the control of the routing repair.**
 * `@EnableReactiveMethodSecurity` is in production. `MethodSecurityConfiguration`
 * declares it, and `app.service.composite.auth` gates it. The proxy it builds is
 * a JDK dynamic proxy, because the controller implements interfaces. So the
 * routing annotations must sit on `ChatTopicServiceRestMapping`, and the JDK
 * proxy carries them from there.
 *
 * Measured on 2026-10-01. Before the repair both routes answered 404, and the
 * controller bean was not assignable to `ChatTopicServiceController<Long>`.
 * Before that, without `@EnableReactiveMethodSecurity`, both routes answered 200
 * and each asserted its own call.
 *
 * **`LongTopicRestTests` passes on the same controller**, because no method
 * security is active in its slice. The old 404 was not a mapping defect.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        RestBoundaryProbeAllowedConfiguration::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestBoundaryProbeAllowedTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>
    @Autowired lateinit var registry: TestGeneratorKeyService<Long>

    /** The path id resolves in its domain, as a minted id does. */
    @BeforeEach
    fun `register the path id`() {
        registry.register(12345L, ChatDomain.MESSAGE_TOPIC)
    }

    @Test
    fun `an allowed caller reaches the mapped route`() {
        client.get().uri("/topic/list").exchange()
            .expectStatus().isOk

        verify(beans.topicService(), times(1)).listRooms()
    }

    /** The facade route. It is refused only if a denial can reach its object. */
    @Test
    fun `an allowed caller reaches the facade route`() {
        client.get().uri("/topic/id/12345").exchange()
            .expectStatus().isOk

        verify(beans.topicService(), times(1)).getRoom(anyObject())
    }
}

@TestConfiguration
@EnableReactiveMethodSecurity
class RestBoundaryProbeAllowedConfiguration {

    @Bean
    @Primary
    @Suppress("UNCHECKED_CAST")
    fun probeBroker(): AccessBroker<Long> {
        val broker = mock(AccessBroker::class.java) as AccessBroker<Long>
        given(broker.hasAccessByPrincipal(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))
        given(broker.hasAccessByKeyId(anyObject(), anyObject(), anyObject()))
            .willReturn(Mono.just(true))
        return broker
    }

    /**
     * **The name matters.** Every expression resolves the bean `chatAccess`.
     *
     * The slice supplies the roots and the verifier, so this bean takes them.
     * `FakeKeyServices` declares `long` and `uuidRoots`, and it declares no
     * `longVerifier`. `WebFluxTestConfiguration` declares `testKeyVerifier`
     * and `testRootKeys`, and it is in this context.
     */
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
     * The facade route calls `getRoom` on its own object. A delegate that
     * answered null would report a server error, and a refusal and a broken
     * stub must not be the same answer.
     */
    @Bean
    fun probeRooms(beans: CompositeServiceBeans<Long, String>): Boolean {
        val room: MessageTopic<Long> = MessageTopic.create(TestKeys.key(1L), "room-one")
        given(beans.topicService().listRooms()).willReturn(Flux.just(room))
        given(beans.topicService().getRoom(anyObject())).willReturn(Mono.just(room))
        return true
    }
}
