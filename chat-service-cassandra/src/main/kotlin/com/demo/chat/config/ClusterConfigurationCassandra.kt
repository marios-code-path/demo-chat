package com.demo.chat.config

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

interface ConfigurationPropertiesCassandra {
    val contactPoints: String
    val port: Int
    val keyspace: String
    val basePackages: String
    val jmxReporting: Boolean
}

class ClusterConfigurationCassandra(val props: ConfigurationPropertiesCassandra) : AbstractReactiveCassandraConfiguration() {

    override fun getKeyspaceName(): String {
        return props.keyspace
    }

    override fun getContactPoints(): String {
        return props.contactPoints
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.CREATE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf("com.demo.chat.domain")
    }

    override fun cluster(): CassandraClusterFactoryBean {
        val cluster = super.cluster()
        cluster.setJmxReportingEnabled(props.jmxReporting)
        return cluster
    }

    @Configuration
    @EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
    class RepositoryConfigurationCassandra
}