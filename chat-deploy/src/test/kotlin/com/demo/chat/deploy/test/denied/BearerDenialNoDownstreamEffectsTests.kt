package com.demo.chat.deploy.test.denied

import com.demo.chat.deploy.test.security.DeployTestSigningKey
import com.demo.chat.service.composite.ChatMessageService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/** A denied caller must not reach the service behind the route. */
@SpringBootTest(
    classes = [DeniedCallerApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class BearerDenialNoDownstreamEffectsTests {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var messaging: ChatMessageService<Long, String>

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @BeforeEach
    fun clearPriorRequests() {
        clearInvocations(messaging)
    }

    @Test
    fun `a send with no token answers 401 and never reaches the service`() {
        client.post().uri("/denied/send")
            .bodyValue("hello")
            .exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(messaging)
    }

    @Test
    fun `a send without the configured scope answers 403 and never reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.mint("client-under-test", "profile")) }
            .bodyValue("hello")
            .exchange()
            .expectStatus().isForbidden

        verifyNoInteractions(messaging)
    }

    @Test
    fun `a send from another client answers 401 and never reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.mint("another-client", "chat.mcp")) }
            .bodyValue("hello")
            .exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(messaging)
    }

    @Test
    fun `an expired token answers 401 and never reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.mintExpired("client-under-test", "chat.mcp")) }
            .bodyValue("hello")
            .exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(messaging)
    }

    @Test
    fun `a send from the agent reaches the service`() {
        client.post().uri("/denied/send")
            .headers { it.setBearerAuth(DeployTestSigningKey.agentToken()) }
            .bodyValue("hello")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java).isEqualTo("sent")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DeployTestSigningKey.register(registry)
    }
}
