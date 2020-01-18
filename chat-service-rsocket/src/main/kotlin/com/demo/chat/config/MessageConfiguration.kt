package com.demo.chat.config

import com.demo.chat.ExcludeFromTests
import com.demo.chat.codec.Codec
import com.demo.chat.service.ChatTopicService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicServiceMemory
import com.demo.chatevents.service.TopicServiceRedisPubSub
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.util.*


class KeyStringEncoder<T> : Codec<T, String> {
    override fun decode(record: T): String {
        return record.toString()
    }
}

class StringUUIDKeyDecoder : Codec<String, UUID> {
    override inline fun decode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDKeyStringEncoder : Codec<UUID, String> {
    override fun decode(record: UUID): String {
        return record.toString()
    }
}

@Profile("memory-topics")
@Configuration
class TopicConfigurationMemory<T: UUID, V> {
    @Bean
    fun topicServiceInMemory(): ChatTopicService<T, V> = TopicServiceMemory()
}

@Profile("redis-topics")
@ConstructorBinding
@ConfigurationProperties("redis-topics")
data class ConfigurationProperiesRedisTopics(override val host: String = "127.0.0.1",
                                             override val port: Int = 6379) : ConfigurationPropertiesTopicRedis

@ExcludeFromTests
@Profile("redis-topics")
@Configuration
class TopicConfigurationRedis {
    @Bean
    fun configurationTopicRedis(props: ConfigurationPropertiesTopicRedis): ConfigurationTopicRedis = ConfigurationTopicRedis(props)

    @Bean
    fun topicServiceRedis(config: ConfigurationTopicRedis): ChatTopicService<*, *> {
        val factory = config.redisConnection()

        return TopicServiceRedisPubSub(
                KeyConfigurationPubSub("all_topics",
                        "st_topic_",
                        "l_user_topics_",
                        "l_topic_users_"),
                ReactiveStringRedisTemplate(factory),
                config.stringMessageTemplate(factory),
                StringUUIDKeyDecoder(),
                UUIDKeyStringEncoder()
        )
    }
}