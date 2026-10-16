package com.demo.chat.test.deploy.memory

import com.demo.chat.config.ChatJackson3Modules
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.security.AuthenticationService
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/**
 * The callers of a running memory deployment, over RSocket. The pointer
 * tests and the message read tests share them. See `CHAT-erkoxybm`.
 */
class DeploymentCallers(
    private val strategies: RSocketStrategies,
    private val port: Int,
    private val composite: CompositeServiceBeans<Long, String>,
    private val authentication: AuthenticationService<Long>,
) {
    val timeout: Duration = Duration.ofSeconds(10)

    private val mapper = JsonMapper.builder()
        .addModule(ChatJackson3Modules().chatJackson3Module())
        .build()

    /** A room with no owner row. It is created with no credential. */
    fun addRoom(name: String): Key<Long> =
        composite.topicService().addRoom(ByStringRequest(name)).block(timeout)!!

    /** The service stores the value as given, so the password carries the encoder id. */
    fun standardUser(handle: String, password: String): Key<Long> {
        val key = composite.userService()
            .addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!
        authentication.setAuthentication(key, "{noop}$password").block(timeout)
        return key
    }

    /** A fresh builder per connection, because the injected builder is one mutable object. A null user connects anonymously. */
    fun connect(username: String?, password: String?): RSocketRequester {
        val builder = RSocketRequester.builder()
            .rsocketStrategies(
                strategies.mutate()
                    .decoders { it.add(0, JacksonJsonDecoder(mapper)) }
                    .encoders { it.add(0, JacksonJsonEncoder(mapper)) }
                    .encoder(SimpleAuthenticationEncoder())
                    .build()
            )
        if (username != null && password != null) {
            builder.setupMetadata(
                UsernamePasswordMetadata(username, password),
                MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string),
            )
        }
        return builder.connectTcp("localhost", port).block(timeout)!!
    }

    fun join(requester: RSocketRequester, member: Key<Long>, room: Key<Long>) {
        requester.route("topic.topic-join").data(MembershipRequest(member.id, room.id))
            .retrieveMono(Void::class.java).block(timeout)
    }

    @Suppress("UNCHECKED_CAST")
    fun send(requester: RSocketRequester, member: Key<Long>, room: Key<Long>, text: String): Key<Long> =
        requester.route("message.message-send").data(MessageSendRequest(text, member.id, room.id))
            .retrieveMono(Key::class.java).block(timeout)!! as Key<Long>
}
