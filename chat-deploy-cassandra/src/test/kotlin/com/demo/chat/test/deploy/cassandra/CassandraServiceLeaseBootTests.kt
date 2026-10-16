package com.demo.chat.test.deploy.cassandra

import com.demo.chat.ChatApp
import com.demo.chat.domain.ServiceLeaseException
import com.demo.chat.domain.ServiceLeaseGuard
import com.demo.chat.domain.ServiceLeaseNames
import com.demo.chat.domain.StoreWriteGatePostProcessor
import com.demo.chat.service.core.ProcessLocalIndex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The store service lease on a Cassandra deployment. See `CHAT-lswjobhz`.
 *
 * Node ids 45 to 47 belong to this class. See docs/NODEID-CLAIM.md. The
 * harness is the one of CassandraClaimBootTests, with Cassandra keys and
 * persistence.
 */
@Tag("integration")
class CassandraServiceLeaseBootTests : CassandraContainerBase() {

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
        "app.service.composite", "app.command.bus=memory",
        "app.service.composite.auth=true",
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

    private fun boot(vararg extra: String, ready: AtomicBoolean) = core(*extra, ready = ready)

    private fun core(vararg extra: String, ready: AtomicBoolean) =
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

    private fun cassandraCore(nodeId: Int) = boot(
        "app.nodeid=$nodeId",
        "app.service.core.key=cassandra",
        "app.service.core.persistence=cassandra",
        ready = AtomicBoolean(false)
    )

    @Test
    fun `a second core on one keyspace fails at start and names the service`() {
        cassandraCore(45).run().use { a ->
            val thrown = Assertions.assertThrows(Exception::class.java) {
                cassandraCore(46).run().close()
            }
            val lease = generateSequence<Throwable>(thrown) { it.cause }.filterIsInstance<ServiceLeaseException>().first()
            Assertions.assertEquals("index:authmeta", lease.name)
            Assertions.assertEquals("cassandra keyspace chat_long", lease.scope)
            Assertions.assertTrue(lease.holder.startsWith("node 45 "), lease.holder)
            Assertions.assertTrue(a.isActive)
        }
    }

    @Test
    fun `a clean close releases every lease, so a restart starts at once`() {
        cassandraCore(47).run().use { context ->
            val guard = context.getBean(ServiceLeaseGuard::class.java)
            Assertions.assertEquals(ServiceLeaseNames.ALL, guard.claimed)

            // Each store service of the holders writes through the gate. The
            // Cassandra holders create their services, so they are not beans.
            val ungated = ServiceLeaseNames.servicesIn(context)
                .filterValues { !StoreWriteGatePostProcessor.isGated(it) && it !is ProcessLocalIndex }.keys
            Assertions.assertEquals(emptySet<String>(), ungated, "store services that write past the gate")
        }
        cassandraCore(47).run().use { }
    }
}
