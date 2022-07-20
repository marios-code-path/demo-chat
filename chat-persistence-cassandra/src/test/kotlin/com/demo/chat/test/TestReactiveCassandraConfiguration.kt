package com.demo.chat.test

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction


class TestReactiveCassandraConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

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