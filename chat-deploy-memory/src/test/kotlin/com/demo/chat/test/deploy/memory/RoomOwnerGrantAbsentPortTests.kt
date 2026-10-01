package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.security.RoomOwnerGrant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
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
 * The room owner grant when the composition carries no authorization. See
 * `CHAT-zhjltbky`, decision 2.
 *
 * **`app.service.composite.auth` is absent here.** That property is the
 * condition of `AuthorizationService`, of `RoomOwnerGrantConfiguration` and of
 * `UserInitializationListener`. So this context holds no authorization service
 * at all. The port is optional, and a composition without it still creates a
 * room.
 *
 * **`app.users.create` is absent for that reason.** That property is the switch
 * of `UserInitializationListener`, and that bean takes `AuthorizationService`
 * as a required constructor dependency. So a composition with no
 * `app.service.composite.auth` cannot start while the initializer runs.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml",
        "spring.application.name=test-deployment-room-owner-absent", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails"
    ]
)
class RoomOwnerGrantAbsentPortTests {

    @Autowired
    lateinit var composite: CompositeServiceBeans<Long, String>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var roomOwnerGrants: ObjectProvider<RoomOwnerGrant<Long>>

    private val timeout = Duration.ofSeconds(10)

    /** **The port is absent, and that is what the condition says.** */
    @Test
    fun `a composition without auth supplies no grant port`() {
        assertThat(roomOwnerGrants.ifAvailable).describedAs("the grant port").isNull()
    }

    /**
     * **A composition with no port creates the room and writes no owner row.**
     *
     * The caller is authenticated here, so the missing row proves the absent
     * port and not a missing identity.
     *
     * The row count is read for the whole store and then filtered by the new
     * room, because this deployment ships no grant rows but a later one may.
     */
    @Test
    fun `an auth free composition creates the room and writes no owner row`() {
        val room = addRoomAs("absentroom")
        assertThat(stores.topicPersistence().get(room).block(timeout)).describedAs("the room").isNotNull

        val rows = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .filter { it.target == room }

        assertThat(rows).describedAs("the rows that name the new room").isEmpty()
    }

    private fun addRoomAs(name: String): Key<Long> =
        composite.topicService()
            .addRoom(ByStringRequest(name))
            .contextWrite(context(user()))
            .block(timeout)!!

    /** The shape a deployed login produces. `ContextIdentity` rule 5 reads it. */
    private fun user(): ChatUserDetails<Long> =
        ChatUserDetails(User.create(Key.of(1L, 1L), "absentowner", "absentowner", "http://u"), listOf("ROLE_USER"))

    private fun context(details: ChatUserDetails<Long>): Context =
        ReactiveSecurityContextHolder.withSecurityContext(
            Mono.just(SecurityContextImpl(UsernamePasswordAuthenticationToken(details, "secret", details.authorities)))
        )
}
