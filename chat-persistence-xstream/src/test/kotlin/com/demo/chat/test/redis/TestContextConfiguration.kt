package com.demo.chat.test.redis

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.test.messaging.TestConfigurationPropertiesRedisCluster
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class TestContextConfiguration {
    @Bean
    fun mapper(): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                with(JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)) {
                    registerModules(messageModule(),
                            keyModule(),
                            topicModule(),
                            membershipModule(),
                            userModule())
                }
            }!!

    @Bean
    fun lettuce() = LettuceConnectionFactory(
            RedisStandaloneConfiguration(
                    TestConfigurationPropertiesRedisCluster.host,
                    6379))//TestConfigurationPropertiesRedisCluster.port))
            .apply {
                afterPropertiesSet()
            }

    @Bean
    fun configRedisTemplate() = RedisTemplateConfiguration(lettuce(), mapper())
}