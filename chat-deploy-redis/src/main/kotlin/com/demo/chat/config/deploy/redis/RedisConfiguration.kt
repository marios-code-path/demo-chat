package com.demo.chat.config.deploy.redis

import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

@Configuration
@EnableConfigurationProperties(ConfigurationPropertiesRedisTopics::class)
class RedisConfiguration(private val props: ConfigurationPropertiesRedis) {

    @Bean
    fun redisTemplateConfiguration(
        factory: ReactiveRedisConnectionFactory,
        mapper: ObjectMapper
    ): RedisTemplateConfiguration =
        RedisTemplateConfiguration(factory, mapper)

    @Bean
    fun redisConnection(): ReactiveRedisConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))
}