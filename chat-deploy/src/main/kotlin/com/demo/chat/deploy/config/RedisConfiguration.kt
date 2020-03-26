package com.demo.chat.deploy.config

import com.demo.chat.codec.Codec
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chatevents.config.ConfigurationPropertiesRedisCluster
import com.demo.chatevents.config.ConfigurationRedisTemplate
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicMessagingServiceRedisPubSub
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.util.*

class StringUUIDDecoder : Codec<String, UUID> {
    override fun decode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDStringDecoder : Codec<UUID, String> {
    override fun decode(record: UUID): String {
        return record.toString()
    }
}

// TODO derive redis connection propes from standard spring.redis properties (LettuceConnectionConfiguration)
open class ConnectionConfigurationRedis(private val props: ConfigurationPropertiesRedisCluster) {
    @Bean
    open fun redisConnection(): ReactiveRedisConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))
}

class TopicMessagingConfigurationRedis(private val config: ConfigurationRedisTemplate) {
    open fun topicMessagingRedisPubSub(): ChatTopicMessagingService<*, *> =
            TopicMessagingServiceRedisPubSub(
                    KeyConfigurationPubSub("all_topics",
                            "st_topic_",
                            "l_user_topics_",
                            "l_topic_users_"),
                    config.stringTemplate(),
                    config.stringMessageTemplate(),
                    StringUUIDDecoder(),
                    UUIDStringDecoder()
            )
}