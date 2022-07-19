package com.demo.chat.test


import com.playtika.test.common.utils.ContainerUtils
import org.assertj.core.api.Assertions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import java.time.Duration

open class CassandraTestContainerConfiguration(val props: CassandraProperties) {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    @Value("\${keyType:uuid}")
    private lateinit var keyType: String

    // TODO convert to embedded.cassandra properties
    // start container, and re-wire cassandraProperties to contain
    // values (IP, Port, DC...) that container provides.
    @Bean(name = ["embeddedCassandra"], destroyMethod = "stop")
    open fun cassandraContainer(context: ConfigurableApplicationContext): CassandraContainer<*> {
        val ddlResource = "qspace-${keyType}.cql"
        val resource = context.getResource(ddlResource)

        Assertions
            .assertThat(resource)
            .isNotNull
            .hasFieldOrPropertyWithValue("readable", true)

        val container = CassandraContainer<Nothing>("cassandra:3.11.8").apply {
            log.debug("Testcontainer-Cassandra is on port:  ${props.port}")
            //withConfigurationOverride("cassandra.yaml")
            withExposedPorts(props.port)
            withReuse(true)
            withLogConsumer(ContainerUtils.containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withConfigurationOverride("cassandra")
            withStartupTimeout(Duration.ofSeconds(60))
            withInitScript(ddlResource)
            ContainerUtils.startAndLogTime(this)

            log.debug("Test Container STARTED")
        }

        val host = container.host
        val mappedPort = container.getMappedPort(props.port)
        log.debug("Container is reachable on port: $mappedPort")

        props.contactPoints.removeAt(0)
        props.contactPoints.add(host)
        props.port = mappedPort

        return container
    }
}