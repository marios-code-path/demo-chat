package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.command.ImportedMessageId
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.util.MimeTypeUtils
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The RSocket import route on a `UUID` deployment. The route derives each message id from a room-scoped source id. See `CHAT-bwzjstoh`. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-message-import-routes",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=uuid", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.command.bus=memory", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageImportRoutesTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<UUID, String>
    @Autowired lateinit var authentication: AuthenticationService<UUID>
    @Autowired lateinit var runtime: MemoryCommandRuntime<UUID, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    @Test
    fun `Admin imports turns with their sender and time and a repeat writes nothing`() {
        val room = composite.topicService().addRoom(ByStringRequest("importroom")).block(timeout)!!
        val firstSender = standardUser("importfirst", "importfirstsecret")
        val secondSender = standardUser("importsecond", "importsecondsecret")
        val admin = connect("Admin", "changeme")
        val early = Instant.parse("2026-10-08T10:00:00.001234Z")
        val middle = Instant.parse("2026-10-08T10:00:01.001234Z")
        val late = Instant.parse("2026-10-08T10:00:02.001234Z")
        fun sourceId(turn: String) = "${room.id}:$turn"

        admin.route("message.message-import")
            .data(MessageImportRequest("late", secondSender.id, room.id, late, sourceId(LATE)))
            .retrieveMono(Map::class.java).block(timeout)
        admin.route("message.message-import")
            .data(MessageImportRequest("early", firstSender.id, room.id, early, sourceId(EARLY)))
            .retrieveMono(Map::class.java).block(timeout)
        admin.route("message.message-import")
            .data(MessageImportRequest("middle", firstSender.id, room.id, middle, sourceId(MIDDLE)))
            .retrieveMono(Map::class.java).block(timeout)
        val mappingsBeforeRepeat = runtime.bus.committedMappings()
        // The source id names the message, so a rerun with changed text reads the stored row. CHAT-bwzjstoh.
        admin.route("message.message-import")
            .data(MessageImportRequest("late, edited", secondSender.id, room.id, late, sourceId(LATE)))
            .retrieveMono(Map::class.java).block(timeout)
        assertThat(runtime.bus.committedMappings()).isEqualTo(mappingsBeforeRepeat)

        val messages = admin.route("message.message-list-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java).collectList().block(timeout)!!
        assertThat(messages).hasSize(3)
        val first = messages[0]["message"] as Map<*, *>
        val second = messages[1]["message"] as Map<*, *>
        val third = messages[2]["message"] as Map<*, *>
        val firstKey = ((first["key"] as Map<*, *>) ["key"] as Map<*, *>)
        val secondKey = ((second["key"] as Map<*, *>) ["key"] as Map<*, *>)
        val thirdKey = ((third["key"] as Map<*, *>) ["key"] as Map<*, *>)
        assertThat(first["data"]).isEqualTo("early")
        assertThat(second["data"]).isEqualTo("middle")
        assertThat(third["data"]).isEqualTo("late")
        assertThat(firstKey["from"].toString()).isEqualTo(firstSender.id.toString())
        assertThat(secondKey["from"].toString()).isEqualTo(firstSender.id.toString())
        assertThat(thirdKey["from"].toString()).isEqualTo(secondSender.id.toString())
        assertThat(firstKey["timestamp"].toString()).isEqualTo("2026-10-08T10:00:00.001Z")
        assertThat(thirdKey["id"].toString()).isEqualTo(ImportedMessageId.derive(sourceId(LATE), room.id).toString())
    }

    private fun standardUser(handle: String, password: String): Key<UUID> {
        val key = composite.userService()
            .addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!
        authentication.setAuthentication(key, "{noop}$password").block(timeout)
        return key
    }


    /** A fresh builder per connection, because the injected builder is one mutable object. */
    private fun connect(username: String, password: String): RSocketRequester =
        RSocketRequester.builder()
            .rsocketStrategies(
                strategies.mutate()
                    .decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                    .encoders { it.add(0, JacksonJsonEncoder(mapper)) }
                    .encoder(SimpleAuthenticationEncoder())
                    .build()
            )
            .setupMetadata(
                UsernamePasswordMetadata(username, password),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string),
            )
            .connectTcp("localhost", port)
            .block(timeout)!!

    private companion object {
        const val EARLY = "1d0f0a52-6a3e-4b8e-9a51-0e2f3c4b5a61"
        const val MIDDLE = "2e1a1b63-7b4f-4c9f-8b62-1f3a4d5c6b72"
        const val LATE = "3f2b2c74-8c5a-4dae-9c73-2a4b5e6d7c83"
    }
}
