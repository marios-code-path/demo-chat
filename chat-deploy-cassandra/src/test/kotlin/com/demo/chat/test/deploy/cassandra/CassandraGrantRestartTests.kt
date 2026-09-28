package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import java.time.Duration

/**
 * A grant written at run time on a domain root still applies after the
 * context restarts against the same Cassandra keyspace. See `CHAT-bafkgkko`.
 *
 * Node id 23 belongs to this class. See docs/NODEID-CLAIM.md. A clean close
 * releases the claim, so the second context takes the same id.
 */
@Tag("integration")
class CassandraGrantRestartTests : CassandraContainerBase() {

    private val timeout = Duration.ofSeconds(10)

    /**
     * The launch surface, written as command line arguments. See
     * CassandraClaimBootTests for why a command line argument is necessary.
     * The initial users are on. A grant read puts the `Anon` identity in its
     * actor set, and that identity loads with the initial users. Without it,
     * every read fails, and `hasAccessByKeyId` answers false.
     */
    private fun launchArguments(): Array<String> = arrayOf(
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.key.type=long",
        "app.nodeid=23",
        "app.users.create=true",
        "app.service.core.key=cassandra",
        "app.service.core.persistence=cassandra",
        "app.service.core.index=cassandra",
        "app.service.core.pubsub=memory",
        "app.service.core.secrets=cassandra",
        "app.service.composite",
        "app.service.composite.auth",
        "app.controller.secrets",
        "app.controller.key",
        "app.controller.persistence",
        "app.controller.index",
        "app.controller.user",
        "app.controller.message",
        "app.controller.topic",
        "app.controller.pubsub",
        "app.service.security.userdetails",
        "spring.profiles.active=cassandra-contact-point",
        "spring.cassandra.contact-points=${cassandraContainer.host}",
        "spring.cassandra.port=${cassandraContainer.getMappedPort(9042)}",
        "spring.cassandra.username=${cassandraContainer.username}",
        "spring.cassandra.password=${cassandraContainer.password}",
    ).map { "--$it" }.toTypedArray()

    private fun start(): ConfigurableApplicationContext =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .run(*launchArguments())

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.broker() = getBean(AccessBroker::class.java) as AccessBroker<Long>

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.roots() = getBean(RootKeys::class.java) as RootKeys<Long>

    /** The broker resolves both ids through the key registry of this process, then checks the grant. */
    private fun ConfigurableApplicationContext.allows(user: Key<Long>, target: Key<Long>, perm: String): Boolean =
        broker().hasAccessByKeyId(user.id, target.id, perm).block(timeout)!!

    /**
     * The target is the `KEY_VALUE_PAIR` root, because no shipped grant names
     * it. The Cassandra auth index keeps one row per target, so a shipped row
     * and this grant on one root would replace each other. `CHAT-rmxxtwtu`
     * holds that defect.
     */
    @Test
    fun `a runtime grant on a domain root still applies after a restart`() {
        val user: Key<Long>
        val target: Key<Long>

        start().use { first ->
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

        start().use { second ->
            // The checks read the root that this process holds, not the value
            // kept from the first context. A process that minted fresh roots
            // would fail both the equality and the grant check.
            val current = second.roots().of(ChatDomain.KEY_VALUE_PAIR)
            Assertions.assertEquals(target, current, "the restart must read the stored root")
            Assertions.assertTrue(second.allows(user, current, "NEW"), "the grant must apply after the restart")
            Assertions.assertFalse(second.allows(user, current, "DEL"), "a permission with no grant must still deny")
        }
    }
}
