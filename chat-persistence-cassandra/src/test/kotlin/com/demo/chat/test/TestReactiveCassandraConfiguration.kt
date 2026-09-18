package com.demo.chat.test

import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction


class TestReactiveCassandraConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    override fun getLocalDataCenter(): String {
        return checkNotNull(props.localDatacenter) { MISSING + "local-datacenter" }
    }

    override fun getKeyspaceName(): String {
        return checkNotNull(props.keyspaceName) { MISSING + "keyspace-name" }
    }

    override fun getContactPoints(): String {
        return checkNotNull(props.contactPoints) { MISSING + "contact-points" }[0]
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

    companion object {
        // Spring Boot 4 types these properties as nullable. The test resources
        // of this module set each one, so an absent value is a broken fixture.
        private const val MISSING = "the test resources do not set spring.cassandra."
    }
}
