package com.demo.chat.config.deploy.cassandra.dse

import com.demo.chat.config.persistence.cassandra.NodeIdClaimConfiguration
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
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

    // One contract, in one place. CHAT-dwbupbau set it: getKeyspaceName is
    // abstract in the base class, no library default exists, and a
    // cassandra deployment with no keyspace cannot scope a node id claim.
    override fun getKeyspaceName(): String = NodeIdClaimConfiguration.keyspaceOf(props)

    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer {
        // The base class supplies localhost when nothing sets the contact
        // points, and this keeps that optional behaviour. It is read here
        // rather than inside the lambda, because super does not resolve there.
        val contactPoint = props.contactPoints?.firstOrNull() ?: super.getContactPoints()

        // **Credentials are applied only when both are configured.** Spring
        // Boot 4 types them as nullable, and no deployment yml and no test in
        // this repository sets either one, measured on 2026-09-18. So the
        // earlier code passed two nulls to the driver on every launch. An
        // unset credential means an anonymous connection, which is a normal
        // cassandra deployment, and it is what the absent property asks for.
        val username = props.username
        val password = props.password

        return SessionBuilderConfigurer { sessionBuilder ->
            if (username != null && password != null) {
                sessionBuilder.withAuthCredentials(username, password)
            }
            sessionBuilder.addContactPoint(InetSocketAddress(contactPoint, props.port))
        }
    }
}
