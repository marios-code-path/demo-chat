package com.demo.chat.deploy.test.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
    fun `an application route refuses a request with no credentials`() {
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("WWW-Authenticate")
    }

    @Test
    fun `an application route refuses the actuator user`() {
        client.get().uri("/test/open")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `an application route answers the agent token`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("open")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
    }
}
