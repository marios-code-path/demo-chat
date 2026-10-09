package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
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

/** The RSocket submit and status routes, over a running server. The setup of `StandardUserJoinSendTests`. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-message-command-routes",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
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
class MessageCommandRoutesTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>
    @Autowired lateinit var runtime: MemoryCommandRuntime<Long, String>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    @Test
    fun `a member submits and reads the status of its own command`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesroom")).block(timeout)!!
        val member = standardUser("commandmember", "commandmembersecret")
        val requester = connect("commandmember", "commandmembersecret")
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id)).retrieveMono(Void::class.java).block(timeout)

        val result = requester.route("message.message-submit")
            .data(MessageSubmitRequest("over rsocket", room.id, "rs-1"))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat(result["outcome"]).isEqualTo("COMPLETED")
        val commandId = (result["receipt"] as Map<*, *>)["commandId"] as String

        val status = requester.route("message.message-command-status").data(CommandStatusRequest(commandId))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat(status["commandId"]).isEqualTo(commandId)

        val again = requester.route("message.message-submit")
            .data(MessageSubmitRequest("over rsocket", room.id, "rs-1"))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat((again["receipt"] as Map<*, *>)["commandId"]).isEqualTo(commandId)
    }

    @Test
    fun `case 11 and review focus 5 - another user reads no status and a non-member submits nothing`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesdenied")).block(timeout)!!
        val owner = standardUser("commandowner", "commandownersecret")
        val ownerRequester = connect("commandowner", "commandownersecret")
        ownerRequester.route("topic.topic-join").data(MembershipRequest(owner.id, room.id)).retrieveMono(Void::class.java).block(timeout)
        val result = ownerRequester.route("message.message-submit")
            .data(MessageSubmitRequest("owned", room.id, "rs-owned"))
            .retrieveMono(Map::class.java).block(timeout)!!
        val commandId = (result["receipt"] as Map<*, *>)["commandId"] as String

        standardUser("commandstranger", "commandstrangersecret")
        val stranger = connect("commandstranger", "commandstrangersecret")
        val statusError = catchThrowable {
            stranger.route("message.message-command-status").data(CommandStatusRequest(commandId))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(statusError).hasMessageContaining("Object not Found")

        val before = runtime.bus.committedMappings()
        val submitError = catchThrowable {
            stranger.route("message.message-submit").data(MessageSubmitRequest("not a member", room.id, "rs-denied"))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(submitError).hasMessageContaining("Access Denied")
        assertThat(runtime.bus.committedMappings()).isEqualTo(before)
    }

    @Test
    fun `case 28 - the legacy RSocket send rejects another sender and stores nothing`() {
        val room = composite.topicService().addRoom(ByStringRequest("commandroutesforged")).block(timeout)!!
        val member = standardUser("commandforger", "commandforgersecret")
        val victim = standardUser("commandvictim", "commandvictimsecret")
        val requester = connect("commandforger", "commandforgersecret")
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id)).retrieveMono(Void::class.java).block(timeout)

        val before = runtime.bus.committedMappings()
        val error = catchThrowable {
            requester.route("message.message-send").data(MessageSendRequest("forged", victim.id, room.id))
                .retrieveMono(Map::class.java).block(timeout)
        }
        assertThat(error).hasMessageContaining("is not the authenticated user")
        assertThat(runtime.bus.committedMappings()).isEqualTo(before)
    }

    private fun standardUser(handle: String, password: String): Key<Long> {
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
}
