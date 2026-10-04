package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.KeyRefusalAdvice
import com.demo.chat.controller.webflux.ChatTopicServiceController
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.security.rsocket.RSocketNotFound
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongCompositeServiceBeans
import com.demo.chat.test.controller.webflux.LongTypeUtilConfiguration
import io.rsocket.exceptions.CustomRSocketException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

/**
 * A core miss reaches a REST caller as 404. See `CHAT-undefoqd`.
 *
 * The REST facade calls the core through the RSocket client. The client
 * decoder makes [CoreNotFound] from code `0x404`. The delegate here answers
 * that error, as the client does for a core miss.
 *
 * **The advice is the production advice.** Without its `CoreNotFound`
 * handler, this route answered 500. Measured on 2026-10-03.
 */
@WebFluxTest
@ContextConfiguration(
    classes = [
        TestLongCompositeServiceBeans::class,
        WebFluxTestConfiguration::class,
        LongTypeUtilConfiguration::class,
        ChatTopicServiceController::class,
        KeyRefusalAdvice::class,
    ]
)
@TestPropertySource(properties = ["app.controller.topic"])
class RestCoreNotFoundMappingTests {

    @Autowired lateinit var client: WebTestClient
    @Autowired lateinit var beans: CompositeServiceBeans<Long, String>

    @Test
    fun `a core miss answers 404 with the core message`() {
        val miss = CoreNotFound(CustomRSocketException(RSocketNotFound.CODE, "No room is named nowhere."))
        given(beans.topicService().getRoomByName(anyObject())).willReturn(Mono.error(miss))

        client.get().uri("/topic/name/nowhere").exchange()
            .expectStatus().isNotFound
            .expectBody(String::class.java).isEqualTo("No room is named nowhere.")
    }
}
