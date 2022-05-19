package com.demo.chat.deploy.config

import com.demo.chat.codec.Decoder
import com.demo.chat.config.ConfigurationPropertiesRedis
import com.demo.chat.config.RedisTemplateConfiguration
import com.demo.chat.service.PubSubService
import com.demo.chat.service.impl.memory.messaging.KeyConfigurationPubSub
import com.demo.chat.service.impl.memory.messaging.PubSubServiceRedis
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.ConversionService
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import java.util.*
import kotlin.reflect.typeOf

class StringUUIDDecoder : Decoder<String, UUID> {
    override fun decode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDStringDecoder : Decoder<UUID, String> {
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
    fun topicMessagingRedisPubSub(): PubSubService<*, *> =
            PubSubServiceRedis(
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