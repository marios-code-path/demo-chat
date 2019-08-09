package com.demo.chatgateway

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.test.StepVerifier
import java.net.URI

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WSMappingTests {

    @LocalServerPort
    var port: Int? = null

    @Autowired
    private lateinit var context: AnnotationConfigReactiveWebServerApplicationContext

    @BeforeAll
    fun setUp() {
        context = AnnotationConfigReactiveWebServerApplicationContext(TestWSConfiguration::class.java)

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
        val wsFlux = client
                .execute(URI("ws://localhost:${port}/app/userdist")) {
                    it
                            .receive()
                            .doOnNext(System.out::println)
                            .then()
                }

        StepVerifier
                .create(wsFlux)
                .expectSubscription()
                .verifyComplete()
    }
}