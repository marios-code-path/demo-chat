package com.demo.chat.test.redis

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class TestContextConfiguration {
    @Bean
    fun mapper(): ObjectMapper =
        jacksonObjectMapper().registerModule(KotlinModule()).apply {
            with(JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)) {
                registerModules(
                    JavaTimeModule(),
                    messageModule(),
                    keyModule(),
                    topicModule(),
                    membershipModule(),
                    userModule()
                )
            }
        }!!

    fun lettuce(
        host: String,
        port: String
    ) = LettuceConnectionFactory(
        RedisStandaloneConfiguration(
            host,
            port.toInt()
        )
    )
        .apply {
            afterPropertiesSet()
        }

    @Bean
    fun configRedisTemplate(@Value("\${spring.redis.host}") host: String,
                            @Value("\${spring.redis.port:6379}") port: String) = RedisTemplateConfiguration(lettuce(host, port), mapper())
}