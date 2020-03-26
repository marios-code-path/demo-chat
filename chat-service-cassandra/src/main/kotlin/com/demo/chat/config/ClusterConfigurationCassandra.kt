package com.demo.chat.config

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

interface ConfigurationPropertiesCassandra {
    val basePackages: String
}

class ClusterConfigurationCassandra(val props: CassandraProperties,
                                    val pack: ConfigurationPropertiesCassandra) : AbstractReactiveCassandraConfiguration() {

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }

    override fun getContactPoints(): String {
        return props.contactPoints.get(0)
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.CREATE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf(pack.basePackages)
    }

    override fun cluster(): CassandraClusterFactoryBean {
        val cluster = super.cluster()
        cluster.setJmxReportingEnabled(props.isJmxEnabled)
        return cluster
    }
}