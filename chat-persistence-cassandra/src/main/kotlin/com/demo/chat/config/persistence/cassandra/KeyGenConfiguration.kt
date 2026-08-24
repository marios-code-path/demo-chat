package com.demo.chat.config.persistence.cassandra

import com.demo.chat.persistence.cassandra.domain.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Cassandra is the one that differs: its uuid generator is CassandraUUIDKeyGenerator.
@Configuration("cassandraKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "cassandra")
class KeyGenConfiguration {

    // enforce number on nodeid
    @Value("\${app.nodeid:0}")
    lateinit var nodeId: String

    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "long")
    fun longKeyGen(): IKeyGenerator<Long> = LongKeyGenerator(nodeId.toInt())

    @Bean("KeyGenerator")
    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    fun uuidKeyGen(): IKeyGenerator<UUID> { return CassandraUUIDKeyGenerator() }

}