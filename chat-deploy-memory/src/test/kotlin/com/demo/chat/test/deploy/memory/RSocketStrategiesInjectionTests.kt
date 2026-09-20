package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.test.context.TestPropertySource
import java.nio.charset.StandardCharsets

/**
 * A deployment supplies the chat domain Jackson 3 module to the RSocket
 * server strategies.
 *
 * **This is the injection, not the customizer.**
 * `RSocketServerStrategiesTests` in chat-service-controller drives
 * `rSocketStrategiesCustomizer` directly and hands it a module list, so it
 * proves the customizer and says nothing about where the list comes from.
 * The chat-client-rsocket tests use their own test server. Neither reaches
 * the question this test answers.
 *
 * The claim that stood in for evidence: `ChatApp` scans
 * `com.demo.chat.config`, `ChatJackson3Modules` sits in that package, so
 * `List<JacksonModule>` resolves. That was read from source and never run.
 *
 * The risk it left: an empty list builds a mapper with no domain
 * deserializers, and the server answers an RSocket application error 0x201
 * with a type definition error for a `Key`. That failure appears only in a
 * deployment.
 *
 * This test boots a real rsocket composition root and decodes through the
 * `RSocketStrategies` bean that Boot builds after every customizer has run.
 * See CHAT-rmfuqcwi.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ChatApp::class]
)
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.secrets", "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"

    ]
)
class RSocketStrategiesInjectionTests {

    @Autowired
    private lateinit var strategies: RSocketStrategies

    private val buffers = DefaultDataBufferFactory()

    private fun decode(json: String): Any? {
        val target = ResolvableType.forClass(Key::class.java)
        val decoder = strategies.decoders().first { it.canDecode(target, MediaType.APPLICATION_JSON) }

        return decoder.decode(
            buffers.wrap(json.toByteArray(StandardCharsets.UTF_8)),
            target,
            MediaType.APPLICATION_JSON,
            null,
        )
    }

    @Test
    fun `the deployment strategies decode a Key`() {
        val decoded = decode("""{"key":{"empty":false,"id":1001}}""")

        assertThat(decoded)
            .describedAs("the domain module reached the server strategies of a real deployment")
            .isInstanceOf(Key::class.java)

        val key = decoded as Key<*>
        assertThat(key.id).isEqualTo(1001L)
        assertThat(key)
            .describedAs("a key with no from and no dest stays a Key")
            .isNotInstanceOf(MessageKey::class.java)
    }

    @Test
    fun `the deployment strategies decode a MessageKey`() {
        val decoded = decode("""{"key":{"empty":false,"id":7,"from":10,"dest":20}}""")

        assertThat(decoded).isInstanceOf(MessageKey::class.java)

        val key = decoded as MessageKey<*>
        assertThat(key.id).isEqualTo(7L)
        assertThat(key.from).isEqualTo(10L)
        assertThat(key.dest).isEqualTo(20L)
    }
}
