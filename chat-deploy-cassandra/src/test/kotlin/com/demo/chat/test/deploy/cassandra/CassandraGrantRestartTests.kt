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
 * The Cassandra grant index keeps every grant of one target.
 *
 * `userinit.yml` names the `MESSAGE_TOPIC` root as the target of four rows, so
 * a runtime grant on that root shares its partition with shipped rows. The
 * grant id is a clustering column, and a removal reads the by-id row.
 * `CHAT-rmxxtwtu` holds both defects.
 *
 * The tests run through the production `AccessBroker`, so they prove the
 * deployment path and not a mock.
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

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurableApplicationContext.grants() =
        getBean(AuthorizationService::class.java) as AuthorizationService<Long, AuthMetadata<Long>>

    /** The broker resolves both ids through the key registry of this process, then checks the grant. */
    private fun ConfigurableApplicationContext.allows(user: Key<Long>, target: Key<Long>, perm: String): Boolean =
        broker().hasAccessByKeyId(user.id, target.id, perm).block(timeout)!!

    /**
     * A runtime grant beside the four shipped grants of the `MESSAGE_TOPIC`
     * root. The control is `GET_ALL`, which a shipped row carries. A backend that
     * kept one row per target would answer one of the two and not both.
     *
     * The removal runs in the second context, after the restart, because the
     * by-id row must reach the process that did not write the grant.
     *
     * **A denied permission here does not prove that the index row is gone.**
     * A grant read joins the index to the domain store, so the removal of the
     * domain row alone denies the permission. Measured on 2026-09-27: a
     * mutation that deleted the by-id row alone left this test green.
     * `AuthMetadataIndexRepositoryTests` reads the index tables directly, and
     * it fails on that mutation.
     */
    @Test
    fun `a runtime grant beside the shipped grants survives a restart, and one removal leaves the other`() {
        val user: Key<Long>
        val target: Key<Long>

        start().use { first ->
            @Suppress("UNCHECKED_CAST")
            val composite = first.getBean(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

            user = composite.userService().addUser(UserCreateRequest("restart", "restartuser", "http://u")).block(timeout)!!
            target = first.roots().of(ChatDomain.MESSAGE_TOPIC)

            // A shipped row names this target. REM is the control, and no
            // shipped row names it. **NEW served as the control until
            // 2026-10-01**, when the owner made every user able to add a room.
            // That row now allows before any runtime grant is written.
            Assertions.assertTrue(first.allows(user, target, "GET_ALL"), "the shipped grant must allow GET_ALL")
            Assertions.assertFalse(first.allows(user, target, "REM"), "REM must deny before the grant")

            val placeholder = Key.empty(0L, first.roots().of(ChatDomain.AUTH_METADATA).id)
            first.grants()
                .authorize(AuthMetadata.create(placeholder, user, target, "REM", false, Long.MAX_VALUE), true)
                .block(timeout)

            Assertions.assertTrue(first.allows(user, target, "REM"), "REM must allow after the grant")
            Assertions.assertTrue(
                first.allows(user, target, "GET_ALL"),
                "the shipped grant must survive beside the runtime grant on one target"
            )
        }

        start().use { second ->
            // The checks read the root that this process holds, not the value
            // kept from the first context. A process that minted fresh roots
            // would fail both the equality and the grant checks.
            val current = second.roots().of(ChatDomain.MESSAGE_TOPIC)
            Assertions.assertEquals(target, current, "the restart must read the stored root")
            Assertions.assertTrue(second.allows(user, current, "REM"), "the runtime grant must apply after the restart")
            Assertions.assertTrue(second.allows(user, current, "GET_ALL"), "the shipped grant must apply after the restart")

            val runtime = second.grants()
                .getAuthorizationsForPrincipal(user)
                .filter { it.permission == "REM" }
                .blockFirst(timeout)
            Assertions.assertNotNull(runtime, "the runtime grant must be readable after the restart")

            second.grants().authorize(runtime!!, false).block(timeout)

            Assertions.assertFalse(second.allows(user, current, "REM"), "the removed grant must deny")
            Assertions.assertTrue(
                second.allows(user, current, "GET_ALL"),
                "the shipped grant must survive the removal of the runtime grant"
            )
        }
    }
}
