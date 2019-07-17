package com.demo.chat.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@ConfigurationProperties("cassandra-persistence")
data class CassandraProperties(val contactPoint: String = "127.0.0.1",
                               val port: Int = 9042,
                               val keyspace: String = "chat")

@Profile("cassandra-persistence")
@Configuration
@EnableConfigurationProperties
@EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
class CassandraRepositoryConfiguration

@Configuration
@Profile("cassandra-persistence")
class CassandraConfiguration(val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {
    override fun getKeyspaceName(): String {
        return props.keyspace
    }

    override fun getContactPoints(): String {
        return props.contactPoint
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.NONE
    }

    override
    fun cluster(): CassandraClusterFactoryBean {
        val cluster = super.cluster()
        cluster.setJmxReportingEnabled(false)
        return cluster
    }
}