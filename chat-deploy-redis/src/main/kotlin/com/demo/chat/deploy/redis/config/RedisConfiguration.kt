package com.demo.chat.deploy.redis.config

import com.demo.chat.convert.Converter
import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.pubsub.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.pubsub.impl.memory.messaging.TopicPubSubServiceRedis
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.util.*

class StringUUIDConverter : Converter<String, UUID> {
    override fun convert(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDStringConverter : Converter<UUID, String> {
    override fun convert(record: UUID): String {
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
    fun topicMessagingRedisPubSub(): TopicPubSubService<*, *> =
            TopicPubSubServiceRedis(
                    KeyConfigurationPubSub("all_topics",
                            "st_topic_",
                            "l_user_topics_",
                            "l_topic_users_"),
                    config.stringTemplate(),
                    config.stringMessageTemplate(),
                    StringUUIDConverter(),
                    UUIDStringConverter()
            )
}