package com.demo.chat.test.persistence.redis

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Test context for the Redis persistence tests.
 *
 * Unlike the xstream test context, the mapper here registers ALL domain
 * modules (including authMetadataModule and keyDataPairModule), and a
 * ReactiveStringRedisTemplate bean is provided directly.
 */
class RedisPersistenceTestContext {

    @Bean
    fun mapper(): ObjectMapper =
        jacksonObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
            with(JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)) {
                registerModules(
                    JavaTimeModule(),
                    *allModules().toTypedArray()
                )
            }
        }!!

    @Bean
    fun stringTemplate(
        @Value("\${spring.redis.host}") host: String,
        @Value("\${spring.redis.port:6379}") port: String,
    ): ReactiveStringRedisTemplate {
        val factory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port.toInt())).apply {
            afterPropertiesSet()
        }
        return ReactiveStringRedisTemplate(factory)
    }
}