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
import java.nio.file.Files

@EnableConfigurationProperties(CassandraProperties::class)
open class CassandraTestContainerConfiguration(val props: CassandraProperties) {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @Value("\${app.key.type:uuid}")
    private lateinit var keyType: String

    /**
     * The shared container of this JVM. Each context reads it, and no context
     * stops it. The empty destroy method is required: Spring infers `close`
     * on an `AutoCloseable` bean, and that would stop the container for every
     * other context. `CHAT-znodyvcc`.
     */
    @Bean(name = ["embeddedCassandra"], destroyMethod = "")
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

        val container = SharedCassandraContainer.container

        val host = container.host
        val mappedPort = container.getMappedPort(SharedCassandraContainer.PORT)
        log.debug("Container is reachable on port: $mappedPort")

        // Spring Boot 4 types the contact points as nullable. An absent list
        // reads as an empty one, which keeps the optional behaviour of the
        // property. The container host replaces the first entry either way.
        val contactPoints = props.contactPoints.orEmpty().toMutableList()
        if (contactPoints.isNotEmpty()) {
            contactPoints.removeAt(0)
        }
        contactPoints.add(host)
        props.contactPoints = contactPoints
        props.port = mappedPort

        return container
    }

    @Configuration
    @DependsOn("embeddedCassandra")
    class ReactiveCassandraConfiguration(aprops: CassandraProperties) : TestReactiveCassandraConfiguration(aprops)
}
