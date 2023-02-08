package com.demo.chat.test.deploy.cassandra

import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration


@Testcontainers
open class CassandraContainerBase {

    companion object {
        val imageName = "cassandra:3.11.8"

        @Container
        val cassandraContainer = CassandraContainer(imageName).apply {
            withExposedPorts(9042)
            withReuse(true)
            //withLogConsumer(ContainerUtils.containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withConfigurationOverride("cassandra")
            withStartupTimeout(Duration.ofSeconds(60))
            withInitScript("keyspace-uuid.cql")

            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun cassandraProperties(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.cassandra.contact-points") { cassandraContainer.host }
            registry.add("spring.cassandra.port") { cassandraContainer.getMappedPort(9042) }
            registry.add("spring.cassandra.username") { cassandraContainer.username }
            registry.add("spring.cassandra.password") { cassandraContainer.password }
        }

    }
}