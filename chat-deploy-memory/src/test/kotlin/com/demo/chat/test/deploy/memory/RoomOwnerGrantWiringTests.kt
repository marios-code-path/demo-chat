package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.security.ChatUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.test.context.TestPropertySource
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.time.Duration

/**
 * The room owner grant as a deployed composition wires it.
 *
 * **This test measures the wiring, and not the writer.** `RoomOwnerGrantTests`
 * proves the writer with the port by hand, and
 * `TopicServiceOwnerGrantTests` proves the composite with the port injected.
 * Neither proves that an auth-enabled composition discovers
 * `RoomOwnerGrantConfiguration`, passes the port to `TopicServiceImpl`, and
 * writes the row during `addRoom`. This class is that evidence.
 *
 * `addRoom` runs over the real memory stores, so every key resolves through
 * the registry before the grant is written.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-room-owner-grant", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class RoomOwnerGrantWiringTests {

    @Autowired
    lateinit var composite: CompositeServiceBeans<Long, String>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    private val timeout = Duration.ofSeconds(10)

    /**
     * **The composed topic service writes one wildcard row for the caller.**
     *
     * The principal is a `ChatUserDetails`, which is the shape a deployed
     * login produces. `ContextIdentity` rule 5 answers the key of its user.
     */
    @Test
    fun `an auth enabled composition writes one owner row for the caller`() {
        val user = newUser("wiringowner")
        val room = addRoomAs(user, "wiringroom")

        val rows = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target == room }

        assertThat(rows).describedAs("the rows that name the new room").hasSize(1)
        val row = rows.single()
        assertThat(row.principal).describedAs("the owner").isEqualTo(user.key)
        assertThat(row.permission).describedAs("the permission").isEqualTo("*")
        assertThat(row.expires).describedAs("the expiry").isEqualTo(0L)
    }

    /**
     * **The wildcard is singular, so a room that a shell created holds none.**
     *
     * A caller with no credential reaches the `Anon` root key, and the writer
     * drops it. So this room carries no owner row at all. If the writer wrote
     * one, every caller would own the room, because the actor set of every
     * query holds the `Anon` key.
     */
    @Test
    fun `a room created with no credential carries no owner row`() {
        val room = composite.topicService().addRoom(ByStringRequest("wiringanonroom")).block(timeout)!!

        val rows = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target == room }

        assertThat(rows).describedAs("the rows that name the new room").isEmpty()
        assertThat(stores.topicPersistence().get(room).block(timeout)).describedAs("the room").isNotNull
    }

    /** The handle index tokenizes on a hyphen, so each handle is one token. */
    private fun newUser(handle: String): User<Long> {
        val key = composite.userService()
            .addUser(UserCreateRequest("name-$handle", handle, "http://u")).block(timeout)!!
        return stores.userPersistence().get(key).block(timeout)!!
    }

    private fun addRoomAs(user: User<Long>, name: String): Key<Long> =
        composite.topicService()
            .addRoom(ByStringRequest(name))
            .contextWrite(context(ChatUserDetails(user, listOf("ROLE_USER"))))
            .block(timeout)!!

    private fun context(details: ChatUserDetails<Long>): Context =
        ReactiveSecurityContextHolder.withSecurityContext(
            Mono.just(SecurityContextImpl(UsernamePasswordAuthenticationToken(details, "secret", details.authorities)))
        )
}
