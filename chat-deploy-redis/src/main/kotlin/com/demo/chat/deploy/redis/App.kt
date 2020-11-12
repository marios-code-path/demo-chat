package com.demo.chat.deploy.redis

import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chat.deploy.config.TopicMessagingConfigurationRedis
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory

@Profile("redis-events")
class App {
    @Bean
    fun topicMessagingRedis(props: RedisTemplateConfiguration): ChatTopicMessagingService<*, *> =
            TopicMessagingConfigurationRedis(props).topicMessagingRedisPubSub()

    @Bean
    fun redisTemplate(factory: ReactiveRedisConnectionFactory,
                      mapper: ObjectMapper): RedisTemplateConfiguration =
            RedisTemplateConfiguration(factory, mapper)


    @Profile("redis-cluster")
    @ConstructorBinding
    @ConfigurationProperties("redis-topics")
    data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                                  override val port: Int = 6379) : ConfigurationPropertiesRedis

    @EnableConfigurationProperties(ConfigurationPropertiesRedisTopics::class)
    class ServicesDiscovery {}
}