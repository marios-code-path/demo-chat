package com.demo.chat.test

import com.playtika.test.common.utils.ContainerUtils.containerLogsConsumer
import com.playtika.test.common.utils.ContainerUtils.startAndLogTime
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import java.time.Duration

@AutoConfigureOrder
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Configuration
@ComponentScan("com.demo.chat.test")
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@EnableConfigurationProperties(CassandraProperties::class)
class CassandraTestConfiguration(val props: CassandraProperties) {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    // start container, and re-wire cassandraProperties to contain
    // values (IP, Port, DC...) that contianer provides.
    @Bean(name = ["embeddedCassandra"], destroyMethod = "stop")
    fun cassandraContainer(environment: ConfigurableEnvironment): CassandraContainer<*> {
        val container = CassandraContainer<Nothing>("cassandra:3.11.8").apply {
            log.debug("Testcontainer-Cassandra is on port:  ${props.port}")
            //withConfigurationOverride("cassandra.yaml")
            withExposedPorts(props.port)
            withReuse(true)
            withLogConsumer(containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withConfigurationOverride("cassandra")
            withStartupTimeout(Duration.ofSeconds(60))
            withInitScript("keyspace.cql")
            startAndLogTime(this)

            log.debug("Test Container STARTED")
        }

        val host = container.containerIpAddress
        val mappedPort = container.getMappedPort(props.port)
        log.debug("Container is reachable on port: $mappedPort")

        props.contactPoints.removeAt(0)
        props.contactPoints.add(host)
        props.port = mappedPort

        return container
    }
}

@Configuration
@DependsOn("embeddedCassandra")
class TestClusterConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    override fun getLocalDataCenter(): String {
        return props.localDatacenter
    }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }

    override fun getContactPoints(): String {
        return props.contactPoints[0]
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.CREATE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf("com.demo.chat.repository.cassandra")
    }
}