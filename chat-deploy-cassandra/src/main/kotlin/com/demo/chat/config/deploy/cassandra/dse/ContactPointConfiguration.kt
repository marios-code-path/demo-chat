package com.demo.chat.config.deploy.cassandra.dse

import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.config.AbstractReactiveCassandraConfiguration
import org.springframework.data.cassandra.config.SessionBuilderConfigurer
import java.net.InetSocketAddress

/**
 * Contact-point based Cassandra configuration for self-hosted / local
 * deployments. Activated by the `cassandra-contact-point` or `default`
 * Spring profile.
 *
 * Spring Boot 3.x auto-configures the reactive Cassandra session before
 * bean-conditional configuration classes — the `@DependsOn` that was
 * previously here was a workaround for a Spring Boot 2.x ordering issue
 * that no longer exists.
 */
@Configuration
@Profile("cassandra-contact-point", "default")
class ContactPointConfiguration(private val props: CassandraProperties) : AbstractReactiveCassandraConfiguration() {

    override fun getKeyspaceName(): String = props.keyspaceName

    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
        SessionBuilderConfigurer { sessionBuilder ->
            sessionBuilder
                .withAuthCredentials(props.username, props.password)
                .addContactPoint(InetSocketAddress(props.contactPoints[0], props.port))
        }
}
