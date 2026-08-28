package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdConfiguration
import com.demo.chat.service.LongKeyGenerator
import com.demo.chat.service.UUIDKeyGenerator
import com.demo.chat.service.core.IKeyGenerator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.util.*

/**
 * Provides the [IKeyGenerator] bean for Redis deployments, mirroring the
 * memory/cassandra modules. Selected by `app.key.type` (uuid | long).
 */
// One key generator per deployment. The selector that picks the key
// backend picks its generator too, so exactly one of these registers.
// Ungated they all registered, and two of them share this simple class
// name - a classpath carrying both failed to start on a conflicting bean
// definition rather than on anything meaningful. Redis is selected explicitly or not at all.
@Configuration("redisKeyGenConfiguration")
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "redis")
@Import(NodeIdConfiguration::class)
class KeyGenConfiguration {

    @ConditionalOnProperty("app.key.type", havingValue = "uuid")
    @Bean("KeyGenerator")
    fun uuidGenerator(nodeId: NodeId): IKeyGenerator<UUID> = UUIDKeyGenerator(nodeId.value)

    @ConditionalOnProperty("app.key.type", havingValue = "long")
    @Bean("KeyGenerator")
    fun longGenerator(nodeId: NodeId): IKeyGenerator<Long> = LongKeyGenerator(nodeId.value)
}
