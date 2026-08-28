package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.persistence.cassandra.impl.CassandraNodeIdClaimStore
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties
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
    ): NodeIdClaimStore = CassandraNodeIdClaimStore(template, properties.keyspaceName)
}
