package com.demo.chat.config.deploy.cassandra.dse

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction

//@Configuration
//@DependsOn("KeyGenerator", "TypeUtil")
//@EnableConfigurationProperties(CassandraProperties::class)
class CassandraConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

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
        return arrayOf("com.demo.chat.persistence.cassandra.repository")
    }
}