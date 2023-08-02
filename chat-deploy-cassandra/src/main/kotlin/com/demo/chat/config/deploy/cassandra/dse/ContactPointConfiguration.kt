package com.demo.chat.config.deploy.cassandra.dse

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.net.InetSocketAddress

@Configuration
@Profile("cassandra-default", "default")
class ContactPointConfiguration(
        val props: CassandraProperties,
) : AbstractCassandraConfiguration() {
    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
            SessionBuilderConfigurer { sessionBuilder ->
                sessionBuilder
                        .withAuthCredentials(props.username, props.password)
                        .addContactPoint(InetSocketAddress(props.contactPoints[0], props.port))
                        .withLocalDatacenter(props.localDatacenter)
            }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
}
