package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.domain.NodeIdClaimStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Boot level tests for the cassandra claim seam.
 *
 * Node ids 21 and 22 belong to this class. See the allocation table in the
 * plan. CassandraDeployTest holds node id 1 in the same container, and
 * truncate-long.cql does not clear node_claim.
 */
@Tag("integration")
class CassandraClaimBootTests : CassandraContainerBase() {

    private fun cqlsh(statement: String): String {
        val result = cassandraContainer.execInContainer(
            "cqlsh",
            "-u", cassandraContainer.username,
            "-p", cassandraContainer.password,
            "-e", statement
        )
        Assertions.assertEquals(
            0, result.exitCode,
            "cqlsh failed. stdout: ${result.stdout} stderr: ${result.stderr}"
        )
        return result.stdout
    }

    /**
     * Writes the claim with cqlsh, outside every application context.
     *
     * A booted context would take the id itself and release it on close, and
     * the duplicate would not be there for the second boot.
     */
    private fun seedForeignClaim(nodeId: Int, owner: String) {
        cqlsh("DELETE FROM chat_long.node_claim WHERE node_id = $nodeId;")
        cqlsh(
            "INSERT INTO chat_long.node_claim (node_id, owner_id) " +
                "VALUES ($nodeId, '$owner') IF NOT EXISTS USING TTL 300;"
        )
        val readBack = cqlsh("SELECT owner_id FROM chat_long.node_claim WHERE node_id = $nodeId;")
        Assertions.assertTrue(
            readBack.contains(owner),
            "the foreign claim seed must be applied. cqlsh said: $readBack"
        )
    }

    /**
     * The launch surface, written as command line arguments.
     *
     * SpringApplicationBuilder.properties() writes to defaultProperties,
     * which is the lowest precedence source. application.yml would then
     * override the container address with its own localhost value. A command
     * line argument outranks a config file, so these arguments hold.
     */
    private fun launchArguments(): Array<String> = launchProperties().map { "--$it" }.toTypedArray()

    private fun launchProperties(): Array<String> = arrayOf(
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=classpath:/config/logging.yml," +
            "classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "server.port=0",
        "spring.rsocket.server.port=0",
        "app.key.type=long",
        "app.service.core.pubsub=memory",
        "app.service.core.index=cassandra",
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
        // SpringApplicationBuilder does not read @DynamicPropertySource.
        "spring.cassandra.contact-points=${cassandraContainer.host}",
        "spring.cassandra.port=${cassandraContainer.getMappedPort(9042)}",
        "spring.cassandra.username=${cassandraContainer.username}",
        "spring.cassandra.password=${cassandraContainer.password}"
    )

    private fun boot(vararg extra: String, ready: AtomicBoolean) =
        SpringApplicationBuilder(ChatApp::class.java)
            .web(WebApplicationType.NONE)
            .listeners(ApplicationListener<ApplicationReadyEvent> { ready.set(true) })
            .let { builder ->
                BootRun(builder, launchArguments() + extra.map { "--$it" })
            }

    /** Carries the builder and its arguments, so `run` takes no argument list. */
    private class BootRun(
        private val builder: SpringApplicationBuilder,
        private val arguments: Array<String>
    ) {
        fun run(): org.springframework.context.ConfigurableApplicationContext =
            builder.run(*arguments)
    }

    private fun allMessages(thrown: Throwable): String =
        generateSequence(thrown) { it.cause }.mapNotNull { it.message }.joinToString(" | ")

    @Test
    fun `a live claim fails startup with the actionable message`() {
        seedForeignClaim(21, "foreign-owner@host-z:1#deadbeef")
        val ready = AtomicBoolean(false)

        val thrown = Assertions.assertThrows(Exception::class.java) {
            boot(
                "app.nodeid=21",
                "app.service.core.key=cassandra",
                "app.service.core.persistence=cassandra",
                ready = ready
            ).run().close()
        }

        val text = allMessages(thrown)
        Assertions.assertTrue(text.contains("app.nodeid=21 is already claimed"), text)
        Assertions.assertTrue(text.contains("cassandra keyspace chat_long"), text)
        Assertions.assertTrue(text.contains("foreign-owner@host-z:1#deadbeef"), text)
        Assertions.assertFalse(ready.get(), "ApplicationReadyEvent must not be published")
    }

    @Test
    fun `a memory key selector with cassandra persistence still claims`() {
        val ready = AtomicBoolean(false)

        boot(
            "app.nodeid=22",
            "app.service.core.key=memory",
            "app.service.core.persistence=cassandra",
            ready = ready
        ).run().use { context ->
            val stores = context.getBeansOfType(NodeIdClaimStore::class.java)
            Assertions.assertEquals(1, stores.size)
            Assertions.assertEquals("cassandra", stores.values.first().backendName)
        }

        Assertions.assertTrue(ready.get())
    }
}
