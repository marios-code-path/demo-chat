package com.demo.chat.test

import com.datastax.oss.driver.api.core.config.DefaultDriverOption
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.DriverConfigLoaderBuilderConfigurer
import org.springframework.data.cassandra.config.SchemaAction


class TestReactiveCassandraConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    // The base class types this as nullable, so the override does too. Spring
    // Boot 4 types the property the same way, and the base class supplies
    // datacenter1 when nothing sets it. That optional behaviour stays.
    override fun getLocalDataCenter(): String? =
        props.localDatacenter ?: super.getLocalDataCenter()

    override fun getKeyspaceName(): String {
        // getKeyspaceName is abstract in the base class, so no default
        // exists. A cassandra test without a keyspace is a broken fixture.
        return checkNotNull(props.keyspaceName) {
            "the test resources do not set spring.cassandra.keyspace-name"
        }
    }

    override fun getContactPoints(): String {
        // The base class supplies localhost when nothing sets this.
        return props.contactPoints?.firstOrNull() ?: super.getContactPoints()
    }

    override fun getPort(): Int {
        return props.port
    }

    override fun getDriverConfigLoaderBuilderConfigurer(): DriverConfigLoaderBuilderConfigurer? {
        // The base class applies no driver configuration, so the session ran
        // at the driver default of 2 seconds. That default timed out the
        // schema setup in a slow CI run. `CHAT-sgyaaivp`.
        val timeout = props.request.timeout

        return if (timeout != null) {
            DriverConfigLoaderBuilderConfigurer { builder ->
                builder.withDuration(DefaultDriverOption.REQUEST_TIMEOUT, timeout)
            }
        } else {
            super.getDriverConfigLoaderBuilderConfigurer()
        }
    }

    override fun getSchemaAction(): SchemaAction {
        return SchemaAction.CREATE
    }

    override fun getEntityBasePackages(): Array<String> {
        return arrayOf("com.demo.chat.persistence.cassandra.repository")
    }
}
