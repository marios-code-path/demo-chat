package com.demo.chat.config.deploy.cassandra.dse

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.net.InetSocketAddress

@Configuration
@DependsOn("KeyGenerator", "TypeUtil")
@Profile("cassandra-contact-point", "default")
class ContactPointConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
        SessionBuilderConfigurer { sessionBuilder ->

            sessionBuilder
                .withAuthCredentials(props.username, props.password)
                .addContactPoint(InetSocketAddress(props.contactPoints[0], props.port))
        }

}