package com.demo.chat.deploy.config

import com.demo.chat.codec.Codec
import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.PubSubTopicExchangeService
import com.demo.chat.service.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.service.impl.memory.messaging.PubSubMessagingServiceRedisPubSub
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

// TODO derive redis connection props from standard spring.redis properties (LettuceConnectionConfiguration)
open class ConnectionConfigurationRedis(private val props: ConfigurationPropertiesRedis) {
    @Bean
    open fun redisConnection(): ReactiveRedisConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))
}

class TopicMessagingConfigurationRedis(private val config: RedisTemplateConfiguration) {
    fun topicMessagingRedisPubSub(): PubSubTopicExchangeService<*, *> =
            PubSubMessagingServiceRedisPubSub(
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