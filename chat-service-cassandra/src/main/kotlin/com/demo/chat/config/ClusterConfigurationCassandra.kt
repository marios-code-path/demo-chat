package com.demo.chat.config

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.config.SchemaAction


class ClusterConfigurationCassandra(val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }

    override fun getContactPoints(): String {
        return props.contactPoints.joinToString(",")
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.CREATE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf("com.demo.chat")
    }

    override fun cluster(): CassandraClusterFactoryBean {
        val cluster = super.cluster()
        cluster.setJmxReportingEnabled(props.isJmxEnabled)
        return cluster
    }

    @Deprecated("Use at front of production deployments")
    @Configuration
    class RepositoryConfigurationCassandra
}