package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.persistence.cassandra.impl.CassandraNodeIdClaimStore
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/**
 * Registers the cassandra node id claim store.
 *
 * The bean name is explicit. The redis module ships a class with the same
 * simple name, and a classpath that names both backends registers both.
 */
@Configuration("cassandraNodeIdClaimConfiguration")
@ConditionalOnSharedBackend("cassandra")
@Import(NodeIdClaimGuardConfiguration::class)
class NodeIdClaimConfiguration {

    @Bean("cassandraNodeIdClaimStore")
    fun cassandraNodeIdClaimStore(
        template: ReactiveCassandraTemplate,
        properties: CassandraProperties
    ): NodeIdClaimStore = CassandraNodeIdClaimStore(template, keyspaceOf(properties))

    companion object {

        /**
         * The keyspace that scopes a claim, or a startup failure.
         *
         * Spring Boot 4 types the keyspace name as nullable. A claim is unique
         * per key type per store, and the keyspace is what carries the key
         * type. So a null keyspace would leave the claim with no scope, and
         * two deployments could hold one node id without any report.
         *
         * Every cassandra deployment of this repository sets the property, and
         * so does every cassandra test. A null value is a misconfiguration,
         * and this fails the startup rather than the first claim.
         */
        fun keyspaceOf(properties: CassandraProperties): String =
            properties.keyspaceName
                ?: throw IllegalStateException(
                    "spring.cassandra.keyspace-name is not set. A cassandra node id " +
                        "claim is unique per key type per store. The keyspace names " +
                        "that scope, so the claim store cannot start without it."
                )
    }
}
