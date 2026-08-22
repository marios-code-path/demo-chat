package com.demo.chat.config.persistence.redis

import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/**
 * Provides the [IKeyGenerator] bean for Redis deployments, mirroring the
 * memory/cassandra modules. Selected by `app.key.type` (uuid | long).
 */
@Configuration
class KeyGenConfiguration {
    // enforce number on nodeid
    @Value("\${app.nodeid:0}")
    lateinit var nodeId: String

    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    @Bean("KeyGenerator")
    fun uuidGenerator(): IKeyGenerator<UUID> = UUIDKeyGenerator(nodeId.toInt())

    @ConditionalOnProperty("app.key.type", havingValue = "long")
    @Bean("KeyGenerator")
    fun longGenerator(): IKeyGenerator<Long> = LongKeyGenerator(nodeId.toInt())
}