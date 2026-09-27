package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.persistence.redis.impl.AuthMetaPersistenceRedis
import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import com.demo.chat.persistence.redis.impl.MembershipPersistenceRedis
import com.demo.chat.persistence.redis.impl.MessagePersistenceRedis
import com.demo.chat.persistence.redis.impl.TopicPersistenceRedis
import com.demo.chat.persistence.redis.impl.UserPersistenceRedis
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Wires the full Redis persistence stack when `app.service.core.persistence=redis`.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "redis")
class RedisPersistenceServices<T, V>(
    private val stringTemplate: ReactiveStringRedisTemplate,
    @Qualifier(JACKSON_2_OBJECT_MAPPER) private val objectMapper: ObjectMapper,
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
) : PersistenceServiceBeans<T, V> {

    @Bean
    override fun userPersistence(): UserPersistence<T> =
        UserPersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)

    @Bean
    override fun topicPersistence(): TopicPersistence<T> =
        TopicPersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)

    @Bean
    override fun messagePersistence(): MessagePersistence<T, V> =
        MessagePersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)

    @Bean
    override fun membershipPersistence(): MembershipPersistence<T> =
        MembershipPersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)

    @Bean
    override fun authMetaPersistence(): AuthMetaPersistence<T> =
        AuthMetaPersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)

    @Bean
    override fun keyValuePersistence(): KeyValueStore<T, Any> =
        KeyValuePersistenceRedis(keyService, rootKeys, stringTemplate, objectMapper)
}