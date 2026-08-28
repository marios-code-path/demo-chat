package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.ConditionalOnSharedBackend
import com.demo.chat.domain.NodeIdClaimGuardConfiguration
import com.demo.chat.domain.NodeIdClaimStore
import com.demo.chat.persistence.redis.impl.RedisNodeIdClaimStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Registers the redis node id claim store.
 *
 * The bean name is explicit. The cassandra module ships a class with the
 * same simple name, and a classpath that names both backends registers both.
 * The `KeyGenConfiguration` classes carry explicit names for the same
 * reason.
 */
@Configuration("redisNodeIdClaimConfiguration")
@ConditionalOnSharedBackend("redis")
@Import(NodeIdClaimGuardConfiguration::class)
class NodeIdClaimConfiguration {

    @Bean("redisNodeIdClaimStore")
    fun redisNodeIdClaimStore(
        template: ReactiveStringRedisTemplate,
        @Value("\${app.key.type}") keyType: String
    ): NodeIdClaimStore = RedisNodeIdClaimStore(template, keyType)
}
