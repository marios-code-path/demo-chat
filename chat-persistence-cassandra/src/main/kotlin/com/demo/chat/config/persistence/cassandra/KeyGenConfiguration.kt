package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import com.demo.chat.persistence.cassandra.domain.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Cassandra is the one that differs: its uuid generator is CassandraUUIDKeyGenerator.
@Configuration("cassandraKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "cassandra")
@Import(NodeIdConfiguration::class)
class KeyGenConfiguration {

    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "long")
    fun longKeyGen(nodeId: NodeId): IKeyGenerator<Long> = LongKeyGenerator(nodeId.value)

    // CassandraUUIDKeyGenerator takes no node id. The NodeId parameter is here on
    // purpose, so that app.nodeid is validated on this path too and one contract
    // covers all three backends.
    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    fun uuidKeyGen(nodeId: NodeId): IKeyGenerator<UUID> = CassandraUUIDKeyGenerator()

}
