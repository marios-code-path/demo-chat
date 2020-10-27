package com.demo.chat.test

import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import com.playtika.test.common.utils.ContainerUtils.containerLogsConsumer
import com.playtika.test.common.utils.ContainerUtils.startAndLogTime
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
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.Network
import java.time.Duration

@AutoConfigureOrder
@Configuration
@ComponentScan("com.demo.chat.test")
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
@EnableConfigurationProperties(CassandraProperties::class, CassandraConfigProperties::class)
class CassandraTestConfiguration(val properties: CassandraProperties) {
    val log = LoggerFactory.getLogger(this::class.qualifiedName)

    // start container, and re-wire cassandraProperties to contain
    // values (IP, Port, DC...) that contianer provides.
    @Bean(name = ["embeddedCassandra"], destroyMethod = "stop")
    fun cassandraContainer(environment: ConfigurableEnvironment): CassandraContainer<*> {
        val container = CassandraContainer<Nothing>("cassandra:3.11.8").apply {
            log.debug("Testcontainer Cassandra is on port:  ${properties.port}")
            //withConfigurationOverride("another-cassandra.yaml")
            withExposedPorts(properties.port)
            withReuse(false)
            withLogConsumer(containerLogsConsumer(log))
            withNetwork(Network.SHARED)
            withStartupTimeout(Duration.ofSeconds(60))
            withInitScript("keyspace.cql")
            startAndLogTime(this)
            log.debug("Test Container STARTED")
        }

        val host = container.containerIpAddress
        val mappedPort = container.getMappedPort(properties.port)
        log.debug("Container is reachable on port: $mappedPort")

        properties.contactPoints.removeAt(0)
        properties.contactPoints.add(host)
        properties.port = mappedPort

        return container
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "spring.data.cassandra")
data class CassandraConfigProperties(override val basePackages: String) : ConfigurationPropertiesCassandra

@Configuration
@DependsOn("embeddedCassandra")
class TestClusterConfiguration(props: CassandraProperties, config: CassandraConfigProperties) : ClusterConfigurationCassandra(props, config)