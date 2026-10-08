package com.demo.chat.test.deploy.redis

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

/**
 * A grant written at run time on a domain root still applies after the
 * context restarts against the same Redis store. See `CHAT-bafkgkko`.
 *
 * A Redis deployment reads grants through the Lucene auth index, which lives
 * in process memory. So this test also proves that the start sequence
 * reloads that index from Redis before readiness.
 *
 * Node ids 13 to 17, 30 and 31 belong to this class. See docs/NODEID-CLAIM.md. The
 * second context takes 14, because a Redis close does not release the claim
 * today. `LettuceConnectionFactory` stops before the claim guard releases.
 * `CHAT-ocpojbyy` holds that defect.
 */
@Tag("integration")
class RedisGrantRestartTests {

    private val container = RedisDeployBootTests.redis

    private val timeout = Duration.ofSeconds(10)

    /**
     * The launch surface of RedisClaimBootTests, with the initial users on. A
     * grant read puts the `Anon` identity in its actor set, and that identity
     * loads with the initial users.
     */
    private fun launchProperties(): Array<String> = arrayOf(
        "spring.application.name=redis-grant-restart-test",
        "spring.config.additional-location=classpath:/config/userinit.yml",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.server.proto=rsocket",
        "app.key.type=long",
        "app.users.create=true",
        "app.service.core.key=redis",
        "app.service.core.persistence=redis",
        "app.service.core.pubsub=redis-pubsub",
        "app.service.core.index=lucene",
        "app.service.core.secrets=memory",
        "app.service.composite",
        "app.service.composite.auth=true",
        "app.controller.persistence",
        "app.controller.index",
        "app.controller.key",
        "app.controller.pubsub",
        "app.controller.secrets",
        "app.controller.user",
        "app.controller.topic",
        "app.controller.message",
        "app.service.security.userdetails",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        // SpringApplicationBuilder does not read @DynamicPropertySource.
        "redis-topics.host=${container.containerIpAddress}",
        "redis-topics.port=${container.getMappedPort(6379)}",
    )

    private fun start(nodeId: Int): ConfigurableApplicationContext =
        SpringApplicationBuilder(RedisDeployBootTests.BootApp::class.java)
            .web(WebApplicationType.NONE)
            .properties(*launchProperties(), "app.nodeid=$nodeId")
            .run()

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.broker() = getBean(AccessBroker::class.java) as AccessBroker<Long>

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.roots() = getBean(RootKeys::class.java) as RootKeys<Long>

    /** The broker resolves both ids through the key registry of this process, then checks the grant. */
    private fun ConfigurableApplicationContext.allows(user: Key<Long>, target: Key<Long>, perm: String): Boolean =
        broker().hasAccessByKeyId(user.id, target.id, perm).block(timeout)!!

    /**
     * The target is the `KEY_VALUE_PAIR` root, the same root as the Cassandra
     * test, so both backends prove one contract.
     */
    @Test
    fun `a runtime grant on a domain root still applies after a restart`() {
        val user: Key<Long>
        val target: Key<Long>

        start(13).use { first ->
            @Suppress("UNCHECKED_CAST")
            val composite = first.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
            @Suppress("UNCHECKED_CAST")
            val grants = first.getBean(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>

            user = composite.userService().addUser(UserCreateRequest("restart", "restartuser", "http://u")).block(timeout)!!
            target = first.roots().of(ChatDomain.KEY_VALUE_PAIR)

            // Before the grant, both permissions deny. DEL is the control.
            Assertions.assertFalse(first.allows(user, target, "NEW"), "NEW must deny before the grant")
            Assertions.assertFalse(first.allows(user, target, "DEL"), "DEL must deny before the grant")

            val placeholder = Key.empty(0L, first.roots().of(ChatDomain.AUTH_METADATA).id)
            grants.authorize(AuthMetadata.create(placeholder, user, target, "NEW", false, Long.MAX_VALUE), true).block(timeout)

            Assertions.assertTrue(first.allows(user, target, "NEW"), "NEW must allow after the grant")
        }

        start(14).use { second ->
            // The checks read the root that this process holds, not the value
            // kept from the first context. A process that minted fresh roots
            // would fail both the equality and the grant check.
            val current = second.roots().of(ChatDomain.KEY_VALUE_PAIR)
            Assertions.assertEquals(target, current, "the restart must read the stored root")
            Assertions.assertTrue(second.allows(user, current, "NEW"), "the grant must apply after the restart")
            Assertions.assertFalse(second.allows(user, current, "DEL"), "a permission with no grant must still deny")

            // Each start writes the Admin wildcard on every domain root. A
            // repeat by the same owner is a no-op, so two starts against one
            // store hold one row. See CHAT-esengqpv.
            @Suppress("UNCHECKED_CAST")
            val grants = second.getBean(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>
            val adminRows = grants.getStoredGrants(second.roots().admin(), current)
                .filter { it.permission == "*" }
                .collectList().block(timeout)!!
            Assertions.assertEquals(1, adminRows.size, "the Admin wildcard rows on one root after two starts: $adminRows")
        }
    }

    /**
     * **Every Lucene index loads from the store at start.** Each index lives in
     * process memory, so each was empty after a restart. See `CHAT-uxgdzpag`.
     *
     * **The identity users keep their keys.** `InitialUsersService` finds an
     * identity by its handle through the user index. Before this load, the
     * second start found no `Admin`, created a new one with a new key, and
     * wrote a new set of `Admin` wildcard rows. Measured on 2026-10-04: the
     * two starts of the test above wrote their rows under two `Admin` keys.
     *
     * Each read below goes through one reloaded index: the user index, the
     * topic name index, the message index by room, and the membership index.
     */
    @Test
    fun `the stored identities, rooms, messages and memberships are found after a restart`() {
        val admin: Key<Long>
        val member: Key<Long>
        val room: Key<Long>

        start(16).use { first ->
            val composite = first.composite()
            admin = first.roots().admin()
            member = composite.userService().addUser(UserCreateRequest("reload", "reloaduser", "http://u")).block(timeout)!!
            room = composite.topicService().addRoom(ByStringRequest("reloadroom")).block(timeout)!!
            composite.topicService().joinRoom(MembershipRequest(member.id, room.id)).block(timeout)
            composite.messageService().send(MessageSendRequest("reloadline", member.id, room.id)).block(timeout)
        }

        start(17).use { second ->
            val composite = second.composite()

            Assertions.assertEquals(admin, second.roots().admin(), "the Admin identity keeps its key across a restart")
            Assertions.assertEquals(
                1,
                composite.userService().findByUsername(ByStringRequest("Admin")).collectList().block(timeout)!!.size,
                "one Admin user after two starts",
            )
            Assertions.assertEquals(
                room,
                composite.topicService().getRoomByName(ByStringRequest("reloadroom")).block(timeout)!!.key,
                "the room by its name",
            )
            Assertions.assertEquals(
                listOf("reloadline"),
                composite.messageService().listMessages(ByIdRequest(room.id)).map { it.data }.collectList().block(timeout),
                "the stored message of the room",
            )
            Assertions.assertTrue(
                composite.topicService().roomMembers(ByIdRequest(room.id)).block(timeout)!!
                    .members.any { it.uid == member.id.toString() },
                "the stored member of the room",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.composite() =
        getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.grants() =
        getBean(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>

    private fun ConfigurableApplicationContext.stored(principal: Key<Long>, target: Key<Long>, perm: String) =
        grants().getStoredGrants(principal, target).filter { it.permission == perm }.collectList().block(timeout)!!

    /** The row that decides one access question for [caller]. */
    private fun ConfigurableApplicationContext.deciding(caller: Key<Long>, target: Key<Long>, perm: String) =
        grants().getAuthorizationsAgainst(caller, target, perm).filter { it.permission == perm }
            .collectList().block(timeout)!!.single()

    /**
     * **The initial grants are a seed, not a reconcile.** See `CHAT-ghwtzgjp`.
     *
     * Before this rule, each start wrote one more copy of each named initial
     * grant. Measured on 2026-10-07: two starts on one store added 9 rows.
     *
     * - The second start keeps one row for each initial grant.
     * - It keeps the row that decided access before the start, and the same
     *   row decides access after it.
     * - It does not undo a revoke.
     *
     * The test restores the revoked grant, because the other Redis boot tests
     * share this container.
     */
    @Test
    fun `a restart keeps one row per initial grant and keeps a revoke`() {
        val member: Key<Long>
        val selected: AuthMetadata<Long>
        val revoked: AuthMetadata<Long>

        start(30).use { first ->
            val roots = first.roots()
            val userRoot = roots.of(ChatDomain.USER)
            val topicRoot = roots.of(ChatDomain.MESSAGE_TOPIC)
            val messageRoot = roots.of(ChatDomain.MESSAGE)
            val placeholder = Key.empty(0L, roots.of(ChatDomain.AUTH_METADATA).id)
            member = first.composite().userService()
                .addUser(UserCreateRequest("seed", "seeduser", "http://u")).block(timeout)!!

            // Two copies of a shipped grant, as two starts before this rule wrote them.
            repeat(2) {
                first.grants().authorize(AuthMetadata.create(placeholder, userRoot, topicRoot, "JOIN", false, 0L), true)
                    .block(timeout)
            }
            val copies = first.stored(userRoot, topicRoot, "JOIN")
            Assertions.assertTrue(copies.size >= 3, "the seeded copies: $copies")
            selected = first.deciding(member, topicRoot, "JOIN")
            Assertions.assertEquals(copies.maxBy { it.key.id }.key, selected.key, "the summarizer selects the highest key")

            // An operator revokes a shipped grant by expiring the row that decides it.
            val send = first.deciding(member, messageRoot, "SEND")
            revoked = AuthMetadata.create(send.key, send.principal, send.target, send.permission, send.mute, 1L)
            first.grants().authorize(revoked, true).block(timeout)
        }

        start(31).use { second ->
            val roots = second.roots()
            val userRoot = roots.of(ChatDomain.USER)
            val topicRoot = roots.of(ChatDomain.MESSAGE_TOPIC)
            val messageRoot = roots.of(ChatDomain.MESSAGE)
            try {
                val join = second.stored(userRoot, topicRoot, "JOIN")
                Assertions.assertEquals(listOf(selected.key), join.map { it.key }, "the JOIN rows after the restart")
                Assertions.assertEquals(selected.key, second.deciding(member, topicRoot, "JOIN").key, "the deciding row")

                val send = second.stored(userRoot, messageRoot, "SEND").distinctBy { it.key }
                Assertions.assertEquals(listOf(revoked.key), send.map { it.key }, "the SEND rows after the restart")
                Assertions.assertEquals(1L, send.single().expires, "the revoke holds")

                // Every named initial grant holds one row after two starts.
                val properties = second.getBean(UserInitializationProperties::class.java)
                properties.initialRoles.roles.forEach { role ->
                    val principal = roots.byName(role.user)!!
                    val target = roots.byName(role.target)!!
                    val rows = second.stored(principal, target, role.role).distinctBy { it.key }
                    Assertions.assertEquals(1, rows.size, "rows for ${role.user} -> ${role.target} : ${role.role}: $rows")
                }
            } finally {
                second.grants().authorize(
                    AuthMetadata.create(revoked.key, revoked.principal, revoked.target, revoked.permission, false, 0L),
                    true,
                ).block(timeout)
            }
        }
    }

    /**
     * A stored grant that cannot be read fails the start. The auth index load
     * reads every stored row, so an unreadable row is a partial index, and the
     * process must not start with it. See `CHAT-bafkgkko`, task 2 step 6.
     *
     * The row is removed afterwards, because the other Redis boot tests share
     * this container and would fail on it.
     */
    @Test
    fun `an unreadable stored grant fails the start`() {
        val template = template()
        template.opsForValue().set("chat:auth:$BAD_ID", "{not json").block()
        template.opsForSet().add("chat:idx:auth", BAD_ID).block()
        try {
            val thrown = Assertions.assertThrows(Exception::class.java) { start(15).close() }
            val text = generateSequence<Throwable>(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
            Assertions.assertTrue(text.contains("does not start with a partial index"), text)
        } finally {
            template.delete("chat:auth:$BAD_ID").block()
            template.opsForSet().remove("chat:idx:auth", BAD_ID).block()
        }
    }

    private fun template(): ReactiveStringRedisTemplate {
        val factory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379))
        ).apply { afterPropertiesSet() }
        return ReactiveStringRedisTemplate(factory)
    }

    private companion object {
        const val BAD_ID = "999999001"
    }
}
