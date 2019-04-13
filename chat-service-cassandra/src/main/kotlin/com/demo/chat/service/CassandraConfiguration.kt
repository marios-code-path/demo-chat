package com.demo.chat.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.CassandraClusterFactoryBean
import org.springframework.data.cassandra.config.SchemaAction
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories

@Configuration
@EnableReactiveCassandraRepositories
class CassandraConfiguration : AbstractReactiveCassandraConfiguration() {
    @Value("\${cassandra.contactpoints}")
    private lateinit var contactPoints: String
    @Value("\${cassandra.port}")
    private lateinit var port: Integer
    @Value("\${cassandra.keyspace}")
    private lateinit var keyspace: String
    @Value("\${cassandra.basepackages}")
    private lateinit var basePackages: String

    override fun getKeyspaceName(): String {
        return keyspace
    }

    override fun getContactPoints(): String {
        return contactPoints
    }

    override fun getPort(): Int {
        return port.toInt()
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.NONE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf(basePackages)
    }

    override
    fun cluster(): CassandraClusterFactoryBean {
        val cluster = super.cluster()
        cluster.setJmxReportingEnabled(false)
        return cluster
    }
}