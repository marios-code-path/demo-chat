package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * One process serves REST over the memory composition, with two agents. See
 * `CHAT-frcrctdp`.
 *
 * Each request carries a real token over HTTP. The REST principal writes the
 * owner row, so the owner row measures the REST selection.
 *
 * Run it with `-Pexpose-webflux`. Without that profile, `chat-webflux` is
 * absent and this class is disabled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [ChatApp::class])
@EnabledIf("com.demo.chat.test.deploy.memory.RestAgentSelectionTests#webfluxPresent")
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-rest-agent-selection",
        "app.primary=REST", "app.server.proto=rest",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite=true", "app.command.bus=memory",
        "app.service.composite.auth=true",
        "app.service.security.userdetails=true", "app.users.create=true",
        "app.controller.topic=true", "app.controller.user=true", "app.controller.message=true",
        "app.init.initial-users[Claude].handle=Claude",
        "app.init.initial-users[Claude].name=Claude",
        "app.init.initial-users[Claude].image-uri=chatimg://agent.png",
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-agent",
        "app.security.agents[0].username=Agent",
        "app.security.agents[1].client-id=client-claude",
        "app.security.agents[1].username=Claude",
    ]
)
class RestAgentSelectionTests {

    @LocalServerPort
    var port: Int = 0

    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `each agent token writes an owner row that names its own agent over HTTP`() {
        val agentRoom = addRoom("client-agent", "restagentroom")
        val claudeRoom = addRoom("client-claude", "restclauderoom")

        assertThat(ownerOf(agentRoom)).isEqualTo(keyOf("Agent"))
        assertThat(ownerOf(claudeRoom)).isEqualTo(keyOf("Claude"))
        assertThat(keyOf("Agent")).isNotEqualTo(keyOf("Claude"))
    }

    @Test
    fun `a token from an unlisted client answers 401`() {
        val response = send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/topic/list"))
                .header("Authorization", "Bearer ${AgentTestTokens.mint(signingKeyPath, "client-unlisted")}")
                .GET().build()
        )

        assertThat(response.statusCode()).isEqualTo(401)
    }

    private fun addRoom(clientId: String, name: String): Long {
        val response = send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/topic/new"))
                .header("Authorization", "Bearer ${AgentTestTokens.mint(signingKeyPath, clientId)}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"ByNameRequest\",\"name\":\"$name\"}"))
                .build()
        )
        assertThat(response.statusCode()).describedAs("the room add of $clientId").isEqualTo(201)
        return Regex("\\\"id\\\"\\s*:\\s*(\\d+)").find(response.body())!!.groupValues[1].toLong()
    }

    private fun ownerOf(roomId: Long): Key<Long> {
        val owners = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target.id == roomId && it.permission == "*" && !it.mute }
        assertThat(owners).describedAs("the owner rows of room $roomId").hasSize(1)
        return owners.single().principal
    }

    private fun keyOf(handle: String): Key<Long> =
        composite.userService().findByUsername(ByStringRequest(handle))
            .filter { it.handle == handle }.single().block(timeout)!!.key

    private fun send(request: HttpRequest): HttpResponse<String> =
        client.send(request, HttpResponse.BodyHandlers.ofString())

    companion object {
        private val signingKeyPath = AgentTestTokens.createKey()

        @JvmStatic
        fun webfluxPresent(): Boolean =
            runCatching { Class.forName("com.demo.chat.config.WebFluxSecurity") }.isSuccess

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}
