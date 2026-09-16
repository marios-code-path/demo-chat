package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The actuator route over HTTP.
 *
 * The unit tests call the operation method. Only a real request proves that
 * WebFlux unwraps the Mono the operation returns. ReactiveWebOperationAdapter
 * does that unwrapping, and no test of the method can show it.
 *
 * The deployment sets `management.endpoints.enabled-by-default` to false, so an
 * operator must enable this endpoint and expose it. Both values here are test
 * values. The plan adds neither to any deployment.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-vector-actuator", "app.server.proto=rsocket",
        "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.service.core.vector=simple", "app.service.core.embedding=mock",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.controller.recall",
        "app.service.security.userdetails",
        // management-defaults.yml sets enabled-by-default to false, so an
        // operator must enable this endpoint and expose it. Both values are
        // test values. The plan adds neither to any deployment.
        "management.endpoint.vectorindex.enabled=true",
        "management.endpoints.web.exposure.include=vectorindex",
    ]
)
class MemoryVectorIndexActuatorTests {

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `the read route answers with the status and the jobs`() {
        client
            .get()
            .uri("/actuator/vectorindex")
            .headers { headers -> headers.setBasicAuth("actuator", "actuator") }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").exists()
            .jsonPath("$.jobs").isArray
    }
}
