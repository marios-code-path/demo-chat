package com.demo.chat.config

import com.demo.chat.service.ChatTopicService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicServiceMemory
import com.demo.chatevents.service.TopicServiceRedisPubSub
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

@Profile("memory-topics")
class TopicConfigurationMemory {
    @Bean
    fun topicServiceInMemory(): ChatTopicService = TopicServiceMemory()
}

@Profile("redis-topics")
@ConfigurationProperties("redis-topics")
data class ConfigurationRedisTopics(override val host: String,
                                    override val port: Int) : ConfigurationPropertiesTopicRedis

@Profile("redis-topics")
class TopicConfigurationRedis {

    @Bean
    fun redisConnectionFactory(): ReactiveRedisConnectionFactory = LettuceConnectionFactory()

    @Bean
    fun topicServiceRedis(props: ConfigurationPropertiesTopicRedis): TopicServiceRedisPubSub {
        val topicConfigRedis = ConfigurationTopicRedis(props)

        val factory = redisConnectionFactory()

        return TopicServiceRedisPubSub(
                KeyConfigurationPubSub("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(factory),
                topicConfigRedis.topicTemplate(factory)
        )
    }
}