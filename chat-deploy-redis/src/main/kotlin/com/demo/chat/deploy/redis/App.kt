package com.demo.chat.deploy.redis

import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.deploy.redis.config.TopicMessagingConfigurationRedis
import com.demo.chat.service.TopicPubSubService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory

class App {
    @Bean
    fun topicMessagingRedis(props: RedisTemplateConfiguration): TopicPubSubService<*, *> =
        TopicMessagingConfigurationRedis(props).topicMessagingRedisPubSub()

    @Bean
    fun redisTemplate(factory: ReactiveRedisConnectionFactory,
                      mapper: ObjectMapper): RedisTemplateConfiguration =
            RedisTemplateConfiguration(factory, mapper)


    @ConstructorBinding
    @ConfigurationProperties("redis-topics")
    data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                                  override val port: Int = 6379) : ConfigurationPropertiesRedis

    @EnableConfigurationProperties(ConfigurationPropertiesRedisTopics::class)
    class ServicesDiscovery {}
}