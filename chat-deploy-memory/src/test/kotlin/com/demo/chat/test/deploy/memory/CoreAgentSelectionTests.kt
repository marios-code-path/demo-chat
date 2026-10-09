package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder
import org.springframework.security.rsocket.metadata.BearerTokenMetadata
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import reactor.test.StepVerifier
import java.time.Duration

/**
 * The core selects the agent of each bearer token. See `CHAT-frcrctdp`.
 *
 * Two agents each add a room over RSocket. The owner row of each room names
 * the key of the agent whose token added it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-core-agent-selection",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite=true", "app.command.bus=memory",
        "app.service.composite.auth=true",
        "app.controller.user=true", "app.controller.topic=true", "app.controller.message=true",
        "app.service.security.userdetails=true",
        "app.users.create=true",
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreAgentSelectionTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `each agent token writes an owner row that names its own agent`() {
        addRoom("client-agent", "agentroom")
        addRoom("client-claude", "clauderoom")

        assertThat(ownerOf("agentroom")).isEqualTo(keyOf("Agent"))
        assertThat(ownerOf("clauderoom")).isEqualTo(keyOf("Claude"))
        assertThat(keyOf("Agent")).isNotEqualTo(keyOf("Claude"))
    }

    @Test
    fun `an unlisted client is refused as authentication`() {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(BearerTokenMetadata(AgentTestTokens.mint(signingKeyPath, "client-unlisted")), BEARER)
                    .data(ByStringRequest("unlistedroom"))
                    .retrieveMono(Map::class.java)
            ).expectErrorSatisfies { error ->
                assertThat((error as CustomRSocketException).errorCode())
                    .isEqualTo(RSocketSecurityErrorCodes.AUTHENTICATION)
            }.verify(timeout)
        } finally {
            requester.dispose()
        }
    }

    private fun addRoom(clientId: String, name: String) {
        val requester = bearerRequester()
        try {
            StepVerifier.create(
                requester.route("topic.topic-add")
                    .metadata(BearerTokenMetadata(AgentTestTokens.mint(signingKeyPath, clientId)), BEARER)
                    .data(ByStringRequest(name))
                    .retrieveMono(Map::class.java)
            ).expectNextCount(1).verifyComplete()
        } finally {
            requester.dispose()
        }
    }

    private fun ownerOf(room: String): Key<Long> {
        val roomKey = composite.topicService().getRoomByName(ByStringRequest(room)).block(timeout)!!.key
        val owners = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target.id == roomKey.id && it.permission == "*" && !it.mute }
        assertThat(owners).describedAs("the owner rows of $room").hasSize(1)
        return owners.single().principal
    }

    private fun keyOf(handle: String): Key<Long> =
        composite.userService().findByUsername(ByStringRequest(handle))
            .filter { it.handle == handle }.single().block(timeout)!!.key

    private fun bearerRequester(): RSocketRequester = RSocketRequester.builder()
        .rsocketStrategies(strategies.mutate().encoders { it.add(0, BearerTokenAuthenticationEncoder()) }.build())
        .connectTcp("localhost", port)
        .block(timeout)!!

    companion object {
        private val BEARER = MimeTypeUtils.parseMimeType("message/x.rsocket.authentication.v0")
        private val signingKeyPath = AgentTestTokens.createKey()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.security.jwt.jwk-path") { signingKeyPath }
        }
    }
}
