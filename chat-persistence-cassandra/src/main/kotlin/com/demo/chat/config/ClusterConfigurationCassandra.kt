package com.demo.chat.config

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SchemaAction

interface ConfigurationPropertiesCassandra {
    val basePackages: String
}

@Deprecated("Use standard configuration properties going forward")
class ClusterConfigurationCassandra(val props: CassandraProperties,
                                    val pack: ConfigurationPropertiesCassandra) : AbstractReactiveCassandraConfiguration() {

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
        return arrayOf(pack.basePackages)
    }
}