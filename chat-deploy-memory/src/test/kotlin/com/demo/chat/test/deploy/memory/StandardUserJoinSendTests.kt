package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.UserCreateRequest
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
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * A standard user joins a room, sends to it, and leaves it, over RSocket.
 *
 * **The caller is a plain user with a real credential.** It holds no owner
 * row on the room and no administrator reach. So the only row that can allow
 * its send is the member `SEND` row that a join writes. See `CHAT-mfveaecc`
 * and `CHAT-ruxnqcbj`.
 *
 * Every call crosses the RSocket seam and the composite access checks of a
 * running composition root. Nothing is mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@SpringJUnitConfig(initializers = [RSocketPortInfoApplicationContextInitializer::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-standard-user-join-send",
        "app.server.proto=rsocket", "server.port=0", "spring.rsocket.server.port=0",
        "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory", "app.service.core.pubsub=memory",
        "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory",
        "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true",
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StandardUserJoinSendTests {

    @Autowired lateinit var strategies: RSocketStrategies
    @Autowired lateinit var stores: PersistenceServiceBeans<Long, String>
    @Autowired lateinit var composite: CompositeServiceBeans<Long, String>
    @Autowired lateinit var authentication: AuthenticationService<Long>

    @Value("\${local.rsocket.server.port}")
    var port: Int = 0

    private val timeout = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    /**
     * **Join opens the send, and leave closes it again.** Each refusal sits
     * beside an allow for the same caller and the same room, so the change
     * between them is the membership and nothing else.
     *
     * The room is created with no credential, so it holds no owner row. No
     * caller in this test can send through ownership.
     */
    @Test
    fun `a standard user sends only while it is a member of the room`() {
        val room = composite.topicService().addRoom(ByStringRequest("standardjoinroom")).block(timeout)!!
        val member = standardUser("standardmember", "standardmembersecret")
        val requester = connect("standardmember", "standardmembersecret")

        assertThat(send(requester, member, room, "before the join"))
            .describedAs("a send before the join")
            .isEqualTo(Outcome.Refused)

        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)

        val sent = send(requester, member, room, "while a member")
        assertThat(sent).describedAs("a send after the join").isInstanceOf(Outcome.Sent::class.java)
        val stored = stores.messagePersistence().get((sent as Outcome.Sent).key).block(timeout)
        assertThat(stored?.data).describedAs("the stored message").isEqualTo("while a member")

        requester.route("topic.topic-leave").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)

        assertThat(send(requester, member, room, "after the leave"))
            .describedAs("a send after the leave")
            .isEqualTo(Outcome.Refused)
    }

    /** **The grant belongs to the member that joined.** Another standard user stays refused. */
    @Test
    fun `a standard user that did not join is refused while another user is a member`() {
        val room = composite.topicService().addRoom(ByStringRequest("standardotherroom")).block(timeout)!!
        val member = standardUser("standardjoiner", "standardjoinersecret")
        val outsider = standardUser("standardoutsider", "standardoutsidersecret")
        connect("standardjoiner", "standardjoinersecret").route("topic.topic-join")
            .data(MembershipRequest(member.id, room.id)).retrieveMono(Void::class.java).block(timeout)

        assertThat(send(connect("standardoutsider", "standardoutsidersecret"), outsider, room, "not a member"))
            .describedAs("a send by a user that never joined")
            .isEqualTo(Outcome.Refused)
    }

    /**
     * **Join opens the listen, and leave closes it again.** The listen route
     * checks `SUBSCRIBE` on the room. A join grants it beside `SEND`, and a
     * leave expires both. See `CHAT-lfaajjcj`.
     *
     * The joined listen proves delivery, and not only the check. It receives
     * the message that the member sends after it subscribes.
     */
    @Test
    fun `a standard user listens only while it is a member of the room`() {
        val room = composite.topicService().addRoom(ByStringRequest("standardlistenroom")).block(timeout)!!
        val member = standardUser("standardlistener", "standardlistenersecret")
        val requester = connect("standardlistener", "standardlistenersecret")

        assertThat(listenRefused(requester, room)).describedAs("a listen before the join").isTrue()

        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)

        val heard = requester.route("message.message-listen-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java)
            .filter { it.toString().contains("heard while a member") }
            .next()
            .toFuture()
        assertThat(send(requester, member, room, "heard while a member"))
            .describedAs("a send after the join")
            .isInstanceOf(Outcome.Sent::class.java)
        assertThat(heard.get(timeout.seconds, java.util.concurrent.TimeUnit.SECONDS))
            .describedAs("the message the listen received")
            .isNotNull

        requester.route("topic.topic-leave").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)

        assertThat(listenRefused(requester, room)).describedAs("a listen after the leave").isTrue()
    }

    /** A refused listen ends with an error that reads `Access Denied`. Any other error fails the test. */
    private fun listenRefused(requester: RSocketRequester, room: Key<Long>): Boolean =
        requester.route("message.message-listen-topic").data(ByIdRequest(room.id))
            .retrieveFlux(Map::class.java)
            .take(Duration.ofSeconds(2))
            .then(Mono.just(false))
            .onErrorResume { error ->
                assertThat(error.message).describedAs("the refusal message").contains("Access Denied")
                Mono.just(true)
            }
            .block(timeout)!!

    private sealed interface Outcome {
        data class Sent(val key: Key<Long>) : Outcome
        data object Refused : Outcome
    }

    /** A refusal must read `Access Denied`. Any other error fails the test. */
    @Suppress("UNCHECKED_CAST")
    private fun send(requester: RSocketRequester, from: Key<Long>, room: Key<Long>, text: String): Outcome =
        requester.route("message.message-send")
            .data(MessageSendRequest(text, from.id, room.id))
            .retrieveMono(Key::class.java)
            .map<Outcome> { Outcome.Sent(it as Key<Long>) }
            .onErrorResume { error ->
                assertThat(error.message).describedAs("the refusal message").contains("Access Denied")
                Mono.just(Outcome.Refused)
            }
            .block(timeout)!!

    /** The service stores the value as given, so the password carries the encoder id. */
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
