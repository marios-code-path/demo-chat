package com.demo.chatgateway

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WSMappingTests {

    private val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @LocalServerPort
    var port: Int? = 9090

    @Autowired
    private lateinit var context: AnnotationConfigReactiveWebServerApplicationContext

    @BeforeAll
    fun setUp() {
        context = AnnotationConfigReactiveWebServerApplicationContext(TestWSConfiguration::class.java)
        Hooks.onOperatorDebug()
    }

    @Test
    fun `context loads`() {
        Assertions
                .assertThat(context)
                .isNotNull
    }

    @Test
    fun `get a hello`() {
        WebTestClient
                .bindToApplicationContext(context)
                .build()
                .get()
                .uri("/foo")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .consumeWith {
                    Assertions
                            .assertThat(it.responseBodyContent)
                            .isNotNull()
                            .isNotEmpty()
                }
    }

    @Test
    fun `connect to WS endpoint`() {
        val client = ReactorNettyWebSocketClient()
        val uri = "ws://localhost:${port}/dist"
        val wsFlux = client.execute(URI(uri)) { it ->
            it.receive()
                    .limitRequest(2)
                    .doOnNext {
                log.info("Data: ${it.payloadAsText}")
            }
                    .then()
        }

        StepVerifier
                .create(wsFlux)
                .expectSubscription()
                .verifyComplete()
    }
}