package com.demo.chat.config.persistence.redis

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Wires the Redis [IKeyService] when `app.service.core.key=redis`.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["key"], havingValue = "redis")
class RedisKeyServices<T>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val keyGen: IKeyGenerator<T>,
) : KeyServiceBeans<T> {

    @Bean
    override fun keyService(): IKeyService<T> = KeyServiceRedis(stringTemplate, keyGen)
}