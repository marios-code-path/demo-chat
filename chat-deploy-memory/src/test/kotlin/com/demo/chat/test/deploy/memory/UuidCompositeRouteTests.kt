package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.util.concurrent.TimeUnit

/**
 * The thirteen composite routes that take a raw key id, on a `UUID`
 * deployment. Each id decoded as a `String` before `CHAT-oeyxthsy`, and each
 * route failed at the registry lookup. The Admin cases call one route each.
 * The member cases join, send, read, submit, point and leave as a standard
 * user. The `Long` coverage of the same routes is listed on the issue. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-uuid-composite-routes",
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
class UuidCompositeRouteTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var composite: CompositeServiceBeans<UUID, String>
    @Autowired lateinit var authentication: AuthenticationService<UUID>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    private val admin by lazy { connect("Admin", "changeme") }

    private fun addRoom(name: String): Key<UUID> = composite.topicService().addRoom(ByStringRequest(name)).block(timeout)!!

    private fun addUser(handle: String): Key<UUID> =
        composite.userService().addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!

    // A request id holds visible ASCII only, so the source id replaces each space.
    private fun import(room: Key<UUID>, sender: Key<UUID>, text: String, publish: Boolean = false): Map<*, *> =
        admin.route("message.message-import")
            .data(MessageImportRequest(text, sender.id, room.id, Instant.parse("2026-10-08T10:00:00Z"), "${room.id}:${text.replace(' ', '-')}", publish))
            .retrieveMono(Map::class.java).block(timeout)!!

    private fun message(read: Map<*, *>): Map<*, *> = read["message"] as Map<*, *>

    private fun messageId(read: Map<*, *>): UUID =
        UUID.fromString(((message(read)["key"] as Map<*, *>)["key"] as Map<*, *>)["id"].toString())

    /** The server decodes requests with the domain module. See `RSocketServerConfiguration`. */
    @Test
    fun `the server decoder carries the domain Jackson 3 module`() {
        val decoder = strategies.decoders().first { it is JacksonJsonDecoder } as JacksonJsonDecoder

        assertThat(decoder.mapper.registeredModules().map { it.moduleName }).contains("ChatDomainJackson3")
    }

    @Test
    fun `Admin imports one message`() {
        val room = addRoom("uuidimportroom")
        val sender = addUser("uuidimport")

        val stored = message(import(room, sender, "imported"))

        assertThat(stored["data"]).isEqualTo("imported")
        assertThat(((stored["key"] as Map<*, *>)["key"] as Map<*, *>)["from"].toString()).isEqualTo(sender.id.toString())
    }

    @Test
    fun `Admin lists the messages of a room`() {
        val room = addRoom("uuidlistroom")
        import(room, addUser("uuidlist"), "listed")

        val messages = admin.route("message.message-list-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java).collectList().block(timeout)!!

        assertThat(messages.map { message(it)["data"] }).containsExactly("listed")
    }

    @Test
    fun `Admin listens to a room and hears a published import`() {
        val room = addRoom("uuidlistenroom")
        val sender = addUser("uuidlisten")
        val heard = admin.route("message.message-listen-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java)
            .filter { it.toString().contains("heard by the listener") }
            .next()
            .toFuture()

        import(room, sender, "heard by the listener", publish = true)

        assertThat(heard.get(timeout.seconds, TimeUnit.SECONDS)).isNotNull
    }

    @Test
    fun `Admin reads a message by id`() {
        val room = addRoom("uuidbyidroom")
        val id = messageId(import(room, addUser("uuidbyid"), "read by id"))

        val read = admin.route("message.message-by-id").data(ByIdRequest(id))
            .retrieveMono(Map::class.java).block(timeout)!!

        assertThat(message(read)["data"]).isEqualTo("read by id")
    }

    @Test
    fun `Admin reads a room by id`() {
        val room = addRoom("uuidtopicbyid")

        val read = admin.route("topic.topic-by-id").data(ByIdRequest(room.id))
            .retrieveMono(Map::class.java).block(timeout)!!

        assertThat(read.toString()).contains("uuidtopicbyid", room.id.toString())
    }

    @Test
    fun `Admin reads the members of a room`() {
        val room = addRoom("uuidmembersroom")

        val members = admin.route("topic.topic-members").data(ByIdRequest(room.id))
            .retrieveMono(Map::class.java).block(timeout)

        assertThat(members).isNotNull
    }

    @Test
    fun `Admin removes a room`() {
        val room = addRoom("uuidremroom")

        admin.route("topic.topic-rem").data(ByIdRequest(room.id)).retrieveMono(Void::class.java).block(timeout)

        assertThatThrownBy {
            admin.route("topic.topic-by-name").data(ByStringRequest("uuidremroom"))
                .retrieveMono(Map::class.java).block(timeout)
        }.describedAs("the room after its removal").isNotNull
    }

    @Test
    fun `Admin reads a user by id`() {
        val user = addUser("uuidbyiduser")

        val read = admin.route("user.user-by-id").data(ByIdRequest(user.id))
            .retrieveMono(Map::class.java).block(timeout)!!

        assertThat(read.toString()).contains("uuidbyiduser", user.id.toString())
    }

    /** The service stores the value as given, so the password carries the encoder id. */
    private fun standardUser(handle: String): Key<UUID> =
        addUser(handle).also { authentication.setAuthentication(it, "{noop}${handle}secret").block(timeout) }

    private fun join(requester: RSocketRequester, member: Key<UUID>, room: Key<UUID>) {
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)
    }

    /** The id of the sent message. The route answers the message key in its wrapper. */
    private fun send(requester: RSocketRequester, member: Key<UUID>, room: Key<UUID>, text: String): UUID =
        requester.route("message.message-send").data(MessageSendRequest(text, member.id, room.id))
            .retrieveMono(Map::class.java).block(timeout)!!
            .let { UUID.fromString((it["key"] as Map<*, *>)["id"].toString()) }

    @Test
    fun `a user joins a room, sends a message, and reads it`() {
        val room = addRoom("uuidchatroom")
        val member = standardUser("uuidchatter")
        val requester = connect("uuidchatter", "uuidchattersecret")

        join(requester, member, room)
        val id = send(requester, member, room, "hello over uuid")

        val listed = requester.route("message.message-list-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java).collectList().block(timeout)!!
        assertThat(listed.map { message(it)["data"] }).containsExactly("hello over uuid")
        val read = requester.route("message.message-by-id").data(ByIdRequest(id))
            .retrieveMono(Map::class.java).block(timeout)!!
        assertThat(message(read)["data"]).isEqualTo("hello over uuid")
    }

    @Test
    fun `a member submits a message`() {
        val room = addRoom("uuidsubmitroom")
        val member = standardUser("uuidsubmitter")
        val requester = connect("uuidsubmitter", "uuidsubmittersecret")
        join(requester, member, room)

        val result = requester.route("message.message-submit")
            .data(MessageSubmitRequest("submitted over uuid", room.id, "uuid-submit-1"))
            .retrieveMono(Map::class.java).block(timeout)!!

        assertThat(result["outcome"]).isEqualTo("COMPLETED")
    }

    @Test
    fun `a member creates a pointer to a message`() {
        val room = addRoom("uuidpointerroom")
        val member = standardUser("uuidpointer")
        val requester = connect("uuidpointer", "uuidpointersecret")
        join(requester, member, room)
        val target = send(requester, member, room, "pointer target")

        val result = requester.route("message.message-pointer")
            .data(MessagePointerRequest(room.id, target, "Message", "uuid-pointer-1"))
            .retrieveMono(Map::class.java).block(timeout)!!

        assertThat(result["outcome"]).isEqualTo("COMPLETED")
    }

    @Test
    fun `a member leaves a room and its next send is refused`() {
        val room = addRoom("uuidleaveroom")
        val member = standardUser("uuidleaver")
        val requester = connect("uuidleaver", "uuidleaversecret")
        join(requester, member, room)
        // A send before the leave succeeds, so the refusal below comes from the leave alone.
        send(requester, member, room, "before the leave")

        requester.route("topic.topic-leave").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)

        assertThatThrownBy { send(requester, member, room, "after the leave") }
            .describedAs("a send after the leave").hasMessageContaining("Access Denied")
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
