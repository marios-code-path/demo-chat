package com.demo.chat.test.persistence.redis

import com.demo.chat.persistence.redis.impl.AuthMetaPersistenceRedis
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.persistence.redis.impl.KeyValuePersistenceRedis
import com.demo.chat.persistence.redis.impl.MembershipPersistenceRedis
import com.demo.chat.persistence.redis.impl.MessagePersistenceRedis
import com.demo.chat.persistence.redis.impl.TopicPersistenceRedis
import com.demo.chat.persistence.redis.impl.UserPersistenceRedis
import com.demo.chat.service.UUIDKeyGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.util.UUID

/**
 * Shared bean configuration for the Redis persistence tests: a UUID-based
 * [KeyServiceRedis] plus all six persistence implementations.
 */
@Configuration
class RedisPersistenceTestBeans {

    @Bean
    fun keyServiceRedis(
        stringTemplate: ReactiveStringRedisTemplate,
    ): KeyServiceRedis<UUID> = KeyServiceRedis(stringTemplate, UUIDKeyGenerator(0))

    @Bean
    fun userPersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): UserPersistenceRedis<UUID> = UserPersistenceRedis(keyService, stringTemplate, objectMapper)

    @Bean
    fun topicPersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): TopicPersistenceRedis<UUID> = TopicPersistenceRedis(keyService, stringTemplate, objectMapper)

    @Bean
    fun messagePersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): MessagePersistenceRedis<UUID, String> = MessagePersistenceRedis(keyService, stringTemplate, objectMapper)

    @Bean
    fun membershipPersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): MembershipPersistenceRedis<UUID> = MembershipPersistenceRedis(keyService, stringTemplate, objectMapper)

    @Bean
    fun authMetaPersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): AuthMetaPersistenceRedis<UUID> = AuthMetaPersistenceRedis(keyService, stringTemplate, objectMapper)

    @Bean
    fun keyValuePersistenceRedis(
        keyService: KeyServiceRedis<UUID>,
        stringTemplate: ReactiveStringRedisTemplate,
        objectMapper: ObjectMapper,
    ): KeyValuePersistenceRedis<UUID> = KeyValuePersistenceRedis(keyService, stringTemplate, objectMapper)
}