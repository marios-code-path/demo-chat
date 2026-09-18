package com.demo.chat.test

import org.assertj.core.api.Assertions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import java.nio.file.Files
import java.time.Duration

@EnableConfigurationProperties(CassandraProperties::class)
open class CassandraTestContainerConfiguration(val props: CassandraProperties) {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @Value("\${app.key.type:uuid}")
    private lateinit var keyType: String

    @Bean(name = ["embeddedCassandra"], destroyMethod = "stop")
    open fun cassandraContainer(context: ConfigurableApplicationContext): CassandraContainer<*> {
        val ddlResource = "keyspace-${keyType}.cql"
        val resource = context.getResource(ddlResource)

        Assertions
            .assertThat(resource)
            .isNotNull
            .hasFieldOrPropertyWithValue("readable", true)
        Files.readString(resource.file.toPath()).let {
            log.info("DDL: {}", it)
        }

        val container = CassandraContainer<Nothing>("cassandra:4.1.3").apply {
            withExposedPorts(props.port)
            withReuse(true)
            withNetwork(Network.SHARED)
            withStartupTimeout(Duration.ofSeconds(60))
            withInitScript(ddlResource)
            this.start()

            log.debug("Test Container STARTED")
        }

        val host = container.host
        val mappedPort = container.getMappedPort(props.port)
        log.debug("Container is reachable on port: $mappedPort")

        // Spring Boot 4 types the contact points as nullable. The test
        // resources of this module set them, so an absent list is a broken
        // fixture rather than a state this code handles.
        val contactPoints = checkNotNull(props.contactPoints) {
            "the test resources do not set spring.cassandra.contact-points"
        }
        contactPoints.removeAt(0)
        contactPoints.add(host)
        props.port = mappedPort

        return container
    }

    @Configuration
    @DependsOn("embeddedCassandra")
    class ReactiveCassandraConfiguration(aprops: CassandraProperties) : TestReactiveCassandraConfiguration(aprops)
}
