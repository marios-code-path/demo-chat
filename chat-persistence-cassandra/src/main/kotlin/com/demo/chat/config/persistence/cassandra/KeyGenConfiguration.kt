package com.demo.chat.config.persistence.cassandra

import com.demo.chat.persistence.cassandra.domain.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
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