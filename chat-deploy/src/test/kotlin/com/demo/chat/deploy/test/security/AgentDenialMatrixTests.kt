package com.demo.chat.deploy.test.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/** Runs the REST denial matrix through the production chain and a real socket. */
@SpringBootTest(
    classes = [BothChainsApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.endpoints.web.exposure.include=health,info",
        "app.actuator.username=actuator",
        "app.actuator.password=actuator",
    ],
)
class AgentDenialMatrixTests {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `no authorization header answers 401 with a challenge`() {
        client.get().uri("/test/open")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("WWW-Authenticate")
    }

    @Test
    fun `a malformed token answers 401`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth("not-a-jwt") }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `a bad signature answers 401`() {
        val foreign = TestTokenMinter.mint(SigningKeys.otherKeyFile(), "client-under-test", "chat.mcp")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(foreign) }
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `a wrong client id answers 401, and the answer names no client`() {
        val token = DeployTestSigningKey.mint("another-client", "chat.mcp")
        val answer = client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isUnauthorized
            .returnResult(String::class.java)
            .responseBody

        assertThat(answer.collectList().block().orEmpty().joinToString(""))
            .doesNotContain("another-client")
    }

    @Test
    fun `a valid token without the configured scope answers 403`() {
        val token = DeployTestSigningKey.mint("client-under-test", "profile")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `a valid agent token reaches the route`() {
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("open")
    }

    @Test
    fun `a foreign audience with the right client and scope is accepted today`() {
        val token = DeployTestSigningKey.mintForAudience(
            "client-under-test",
            "chat.mcp",
            "https://another-resource",
        )
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `an expired agent token answers 401`() {
        val token = DeployTestSigningKey.mintExpired("client-under-test", "chat.mcp")
        client.get().uri("/test/open")
            .headers { it.setBearerAuth(token) }
            .exchange()
            .expectStatus().isUnauthorized
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
    }
}
