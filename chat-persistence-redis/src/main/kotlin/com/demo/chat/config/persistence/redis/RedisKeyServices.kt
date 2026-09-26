package com.demo.chat.config.persistence.redis

import com.demo.chat.config.KeyServiceBeans
import org.springframework.beans.factory.annotation.Value
import com.demo.chat.domain.TypeUtil
import com.demo.chat.persistence.redis.impl.RootKeyStoreRedis
import com.demo.chat.service.core.RootKeyStore
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.domain.knownkey.RootKeys
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
class RedisKeyServices<T : Any>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    private val keyGen: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    @Value("\${app.key.type}") private val keyType: String,
) : KeyServiceBeans<T> {

    @Bean
    override fun keyService(): IKeyService<T> = KeyServiceRedis(stringTemplate, keyGen, rootKeys, typeUtil, keyType)

    @Bean
    fun rootKeyStore(): RootKeyStore<T> =
        RootKeyStoreRedis(stringTemplate, typeUtil, keyType)
}