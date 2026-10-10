package com.demo.chat.test.deploy.cassandra

import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.time.Duration

@Testcontainers
@Tag("integration")
open class CassandraContainerBase {

    @Value("\${app.key.type:uuid}")
    private lateinit var keyType: String

    companion object {
        private const val CASSANDRA_IMAGE = "cassandra:4.1.3"

        // Not annotated with @Container on purpose. @Container on a static
        // field makes JUnit stop the container when the first test class
        // finishes. The init block below starts it and applies the long
        // keyspace exactly once per JVM, so a second test class would then
        // meet a fresh container with no chat_long keyspace.
        // RedisTestContainer starts its container the same way.
        val cassandraContainer = CassandraContainer(CASSANDRA_IMAGE).apply {
            withExposedPorts(9042)
            withNetwork(Network.SHARED)
            // The image sizes the heap from the memory of the Docker VM. One
            // container used up to 2 GiB on a 16 GiB VM. The image requires
            // both values together. `CHAT-znodyvcc`.
            withEnv(mapOf("MAX_HEAP_SIZE" to "1G", "HEAP_NEWSIZE" to "256M"))
            withStartupTimeout(Duration.ofSeconds(120))
            // Load the UUID keyspace as the primary init script.
            withInitScript("keyspace-uuid.cql")
        }

        init {
            cassandraContainer.start()

            // Apply the long keyspace after start — Testcontainers only
            // supports one withInitScript, but tests need both keyspaces
            // available depending on app.key.type. We copy the CQL into
            // the container and run it via cqlsh.
            val cqlMount = MountableFile.forClasspathResource("keyspace-long.cql")
            cassandraContainer.copyFileToContainer(cqlMount, "/tmp/keyspace-long.cql")
            cassandraContainer.execInContainer(
                "cqlsh",
                "-u", cassandraContainer.username,
                "-p", cassandraContainer.password,
                "-f", "/tmp/keyspace-long.cql"
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun cassandraProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.cassandra.port") { cassandraContainer.getMappedPort(9042) }
            registry.add("spring.cassandra.username") { cassandraContainer.username }
            registry.add("spring.cassandra.password") { cassandraContainer.password }
        }
    }
}
