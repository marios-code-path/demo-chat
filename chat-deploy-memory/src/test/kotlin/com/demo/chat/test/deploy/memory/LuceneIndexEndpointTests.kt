package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.nio.file.Files
import java.nio.file.Path

/** The launch surface of MemoryVectorIndexActuatorTests, without the vector selectors. */
private val BASE = arrayOf(
    "spring.application.name=test-deployment-lucene-actuator", "app.server.proto=rsocket",
    "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
    "app.service.core.key=memory", "app.service.core.pubsub=memory", "app.service.core.index=lucene",
    "app.service.core.persistence=memory", "app.service.core.secrets=memory",
    "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
    "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
    "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
    "app.service.security.userdetails",
)

private const val WITH_DEFAULTS =
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml"
private const val WITHOUT_DEFAULTS =
    "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/userinit.yml"

abstract class LuceneEndpointClient {
    @Value("\${local.server.port}")
    private var port: Int = 0

    protected val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    protected fun WebTestClient.RequestHeadersSpec<*>.actuator() =
        headers { it.setBasicAuth("actuator", "actuator") }
}

/** Test 1. The shipped defaults keep the endpoint unexposed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(properties = [WITH_DEFAULTS])
class LuceneIndexEndpointDefaultsTests : LuceneEndpointClient() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) = BASE.forEach { p ->
            val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
            registry.add(k) { v }
        }
    }

    @Test
    fun `the shipped defaults answer 404`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange().expectStatus().isNotFound
    }
}

/**
 * Test 2. This test isolates the annotation. It sets no global access default
 * and no endpoint access, and it exposes the id. Boot 4.0.8 resolves both
 * annotation values to NONE when enabled-by-default=false is set, so the
 * shipped defaults cannot isolate the annotation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(properties = [WITHOUT_DEFAULTS, "management.endpoints.web.exposure.include=luceneindex"])
class LuceneIndexEndpointAnnotationTests : LuceneEndpointClient() {
    companion object {
        private val root: Path = Files.createTempDirectory("lucene-endpoint-annotation")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            BASE.forEach { p ->
                val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
                registry.add(k) { v }
            }
            registry.add("app.index.lucene.root") { root.toString() }
        }
    }

    @Test
    fun `every operation answers 404, and no request file appears`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange().expectStatus().isNotFound
        client.post().uri("/actuator/luceneindex/user").actuator().exchange().expectStatus().isNotFound
        client.delete().uri("/actuator/luceneindex/user").actuator().exchange().expectStatus().isNotFound
        assertThat(Files.exists(root.resolve("long/1/user/chat-rebuild.request"))).isFalse()
        assertThat(Files.exists(root.resolve("long/1/user/chat-drop.request"))).isFalse()
    }
}

/** Tests 3, 4 and 5. Access and exposure are both set, and the indexes are in files. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        WITH_DEFAULTS,
        "management.endpoint.luceneindex.access=unrestricted",
        "management.endpoints.web.exposure.include=luceneindex",
    ]
)
class LuceneIndexEndpointAccessTests : LuceneEndpointClient() {
    companion object {
        private val root: Path = Files.createTempDirectory("lucene-endpoint-access")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            BASE.forEach { p ->
                val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
                registry.add(k) { v }
            }
            registry.add("app.index.lucene.root") { root.toString() }
        }
    }

    private fun request(index: String, file: String) = root.resolve("long/1/$index/$file")

    @Test
    fun `no credentials answer 401, and no request file appears`() {
        client.get().uri("/actuator/luceneindex").exchange().expectStatus().isUnauthorized
        client.post().uri("/actuator/luceneindex/topic").exchange().expectStatus().isUnauthorized
        client.delete().uri("/actuator/luceneindex/topic").exchange().expectStatus().isUnauthorized
        assertThat(Files.exists(request("topic", "chat-rebuild.request"))).isFalse()
        assertThat(Files.exists(request("topic", "chat-drop.request"))).isFalse()
    }

    @Test
    fun `the read answers six reports`() {
        client.get().uri("/actuator/luceneindex").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(6)
            .jsonPath("$[0].mode").isEqualTo("files")
    }

    @Test
    fun `post writes a rebuild request`() {
        client.post().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(true)
        assertThat(Files.exists(request("user", "chat-rebuild.request"))).isTrue()
    }

    @Test
    fun `delete writes a drop request`() {
        client.delete().uri("/actuator/luceneindex/message").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(true)
        assertThat(Files.exists(request("message", "chat-drop.request"))).isTrue()
    }

    @Test
    fun `an unknown name lists the indexes`() {
        client.post().uri("/actuator/luceneindex/nope").actuator().exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.accepted").isEqualTo(false)
            .jsonPath("$.reason").value<String> { assertThat(it).contains("user, message, topic, membership, auth, keyvalue") }
    }
}

/** Test 6. Memory mode refuses both commands. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        WITH_DEFAULTS,
        "management.endpoint.luceneindex.access=unrestricted",
        "management.endpoints.web.exposure.include=luceneindex",
    ]
)
class LuceneIndexEndpointMemoryTests : LuceneEndpointClient() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) = BASE.forEach { p ->
            val (k, v) = p.split("=", limit = 2).let { it[0] to (it.getOrNull(1) ?: "") }
            registry.add(k) { v }
        }
    }

    @Test
    fun `both commands are refused in memory mode`() {
        client.post().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.accepted").isEqualTo(false)
            .jsonPath("$.reason").value<String> { assertThat(it).contains("in memory") }
        client.delete().uri("/actuator/luceneindex/user").actuator().exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.accepted").isEqualTo(false)
    }
}
