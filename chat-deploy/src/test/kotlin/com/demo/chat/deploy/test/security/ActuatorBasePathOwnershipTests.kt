package com.demo.chat.deploy.test.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The actuator chain follows the management configuration.
 *
 * An operator can move every actuator route to another prefix. A matcher on a
 * literal actuator path would then miss them, and the permit all application
 * chain would answer them. That is why the chain uses EndpointRequest.
 *
 * This class sets a prefix that is not the default. It is a separate class
 * because the property changes the context, and Spring caches one context for
 * each property set.
 */
@SpringBootTest(
    classes = [BothChainsApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.base-path=/manage",
        "management.endpoints.web.exposure.include=health,info",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
    ],
)
class ActuatorBasePathOwnershipTests {

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
    fun `an actuator route on the new prefix refuses a request with no credentials`() {
        client.get().uri("/manage/info")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `an actuator route on the new prefix answers the actuator user`() {
        client.get().uri("/manage/info")
            .headers { it.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `the health route on the new prefix stays open`() {
        client.get().uri("/manage/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `the default prefix carries no actuator route`() {
        // The routes moved. So this path is an application route now, and the
        // application chain owns it. The answer is 404 and not 401.
        client.get().uri("/actuator/info")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `an application route still answers a request with no credentials`() {
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isOk
    }
}
