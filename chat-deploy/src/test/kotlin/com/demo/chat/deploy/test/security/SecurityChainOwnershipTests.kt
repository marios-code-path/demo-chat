package com.demo.chat.deploy.test.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The two chains own disjoint routes.
 *
 * This module owns the actuator chain. chat-webflux owns the application
 * chain, and it reaches this module at test scope alone. No other module
 * carries both on one classpath, so no other module can prove this.
 *
 * Before this test existed the actuator chain answered anyExchange, so it
 * owned every route of every deployment that carried both modules. Neither
 * chain declared an order, so the winner rested on bean ordering.
 */
@SpringBootTest(
    classes = [BothChainsApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
    ],
)
class SecurityChainOwnershipTests {

    /**
     * The port of the running server.
     *
     * **Boot 4 removed the context customizer that supplied a bound
     * WebTestClient.** Under Boot 3 a RANDOM_PORT test injected a client
     * already bound to the server. Boot 4 offers only
     * `@AutoConfigureWebTestClient`, and that auto-configuration builds a
     * client bound to the application context instead.
     *
     * A context bound client would still run the filter chain, so these
     * tests would pass. They would no longer cross a real socket, and the
     * claim would quietly get weaker. This test binds to the server, which
     * is what it measured before. See CHAT-njtoyatt.
     */
    @Value("\${local.server.port}")
    private var port: Int = 0

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `an actuator route refuses a request with no credentials`() {
        client.get().uri("/actuator/info")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `an actuator route answers the actuator user`() {
        client.get().uri("/actuator/info")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `the health route stays open`() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `an application route answers a request with no credentials`() {
        // This is the regression. The actuator chain answered anyExchange, so
        // it demanded the ACTUATOR role on every application route.
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("open")
    }

    @Test
    fun `an application route refuses no one`() {
        // The application chain permits every route it owns. This project adds
        // no application authentication here. CHAT-jdsamcia says that a
        // separate issue owns that decision.
        client.get().uri("/test/open")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
    }
}
