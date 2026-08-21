package com.demo.chat.test.deploy.cassandra

import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.time.Duration

@Testcontainers
open class CassandraContainerBase {

    @Value("\${app.key.type:uuid}")
    private lateinit var keyType: String

    companion object {
        private const val CASSANDRA_IMAGE = "cassandra:4.1.3"

        @Container
        val cassandraContainer = CassandraContainer(CASSANDRA_IMAGE).apply {
            withExposedPorts(9042)
            withReuse(true)
            withNetwork(Network.SHARED)
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
