package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.AnonymousJoinException
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AuthorizationService
import org.assertj.core.api.Assertions.assertThat
import reactor.core.publisher.Mono
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * The member `SEND` grant as a deployed composition wires it.
 *
 * **This test measures the wiring, and not the writer.**
 * `MembershipSendGrantTests` proves the writer, and
 * `TopicServiceMemberGrantTests` proves the composite with the port injected.
 * This class proves that an auth-enabled composition discovers
 * `RoomMemberGrantConfiguration` and passes the port to `TopicServiceImpl`. It
 * also proves the change of one row through the Lucene auth index, which
 * removes and then writes with the same key. See `CHAT-mfveaecc`.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-room-member-grant", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class RoomMemberGrantWiringTests {

    @Autowired
    lateinit var composite: CompositeServiceBeans<Long, String>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var grants: AuthorizationService<Long, @JvmSuppressWildcards AuthMetadata<Long>>

    @Autowired
    lateinit var rootKeys: RootKeys<Long>

    private val timeout = Duration.ofSeconds(10)

    @Test
    fun `a join grants SEND, a leave expires it, and a second join grants it again`() {
        val member = newUser("wiringmember")
        val room = composite.topicService().addRoom(ByStringRequest("wiringmemberroom")).block(timeout)!!
        val request = MembershipRequest(member.key.id, room.id)
        assertThat(canSend(member.key, room)).describedAs("before the join").isFalse()

        composite.topicService().joinRoom(request).block(timeout)

        val joined = sendRows(member.key, room)
        assertThat(joined).describedAs("the rows after the join").hasSize(1)
        assertThat(joined.single().expires).describedAs("the expiry after the join").isEqualTo(0L)
        assertThat(canSend(member.key, room)).describedAs("after the join").isTrue()

        composite.topicService().leaveRoom(request).block(timeout)

        val left = sendRows(member.key, room)
        assertThat(left.map { it.key }).describedAs("the grant key").isEqualTo(joined.map { it.key })
        assertThat(left.single().expires).describedAs("the expiry after the leave").isGreaterThan(0L)
        assertThat(canSend(member.key, room)).describedAs("after the leave").isFalse()

        composite.topicService().joinRoom(request).block(timeout)

        assertThat(sendRows(member.key, room).map { it.expires }).describedAs("after the second join").containsExactly(0L)
        assertThat(canSend(member.key, room)).describedAs("after the second join").isTrue()
    }

    /**
     * **The `Anon` key cannot join a room.** The deployed `Anon` key is in the
     * registry, so this reaches the composite refusal and not a key error.
     */
    @Test
    fun `an anonymous join is refused and writes no row`() {
        val room = composite.topicService().addRoom(ByStringRequest("wiringanonjoin")).block(timeout)!!
        val anon = rootKeys.anon()

        // block() wraps a checked exception, so the test reads the error signal itself.
        val error = composite.topicService().joinRoom(MembershipRequest(anon.id, room.id))
            .then(Mono.empty<Throwable>())
            .onErrorResume { Mono.just(it) }
            .block(timeout)

        assertThat(error).describedAs("the refusal").isEqualTo(AnonymousJoinException)

        assertThat(stores.authMetaPersistence().all().collectList().block(timeout)!!.filter { it.target == room })
            .describedAs("the rows that name the room")
            .isEmpty()
    }

    private fun sendRows(member: Key<Long>, room: Key<Long>): List<AuthMetadata<Long>> =
        stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.principal == member && it.target == room && it.permission == "SEND" }

    /**
     * The summarized read that the access broker uses. It answers one row per
     * permission, so the shipped `GET` and `NEW` rows on the `MessageTopic`
     * root are in it too. Only a `SEND` row answers the question.
     */
    private fun canSend(member: Key<Long>, room: Key<Long>): Boolean =
        grants.getAuthorizationsAgainst(member, room, "SEND")
            .any { it.permission == "SEND" }
            .block(timeout)!!

    /** The handle index tokenizes on a hyphen, so each handle is one token. */
    private fun newUser(handle: String): User<Long> {
        val key = composite.userService()
            .addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!
        return stores.userPersistence().get(key).block(timeout)!!
    }
}
