package com.demo.deploy.app

import com.demo.chatevents.config.ConfigurationPropertiesRedisCluster
import com.demo.chatevents.config.ConfigurationRedisTemplate
import com.demo.deploy.config.ConnectionConfigurationRedis
import com.demo.deploy.config.app.AppTopicMessagingRedis
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory

@Profile("redis-events")
class EventsStarterRedis {

    @Configuration
    class AppsTopicMessaging : AppTopicMessagingRedis()

    @Bean
    fun redisTemplate(factory: ReactiveRedisConnectionFactory,
                      mapper: ObjectMapper): ConfigurationRedisTemplate =
            ConfigurationRedisTemplate(factory, mapper)


    @Profile("redis-cluster")
    @ConstructorBinding
    @ConfigurationProperties("redis-topics")
    data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                                  override val port: Int = 6379) : ConfigurationPropertiesRedisCluster

    @EnableConfigurationProperties(ConfigurationPropertiesRedisTopics::class)
    class ServicesDiscovery {}
}