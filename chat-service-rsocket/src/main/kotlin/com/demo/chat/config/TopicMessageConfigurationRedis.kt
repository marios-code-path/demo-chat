package com.demo.chat.config

import com.demo.chat.codec.Codec
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.ChatTopicMessagingService
import com.demo.chatevents.config.ConfigurationPropertiesTopicRedis
import com.demo.chatevents.config.ConfigurationTopicRedis
import com.demo.chatevents.service.KeyConfigurationPubSub
import com.demo.chatevents.service.TopicMessagingServiceRedisPubSub
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

open class ConnectionConfigurationRedis(private val props: ConfigurationPropertiesTopicRedis,
                                        private val mapper: ObjectMapper) {
    @Bean
    fun redisConnection(): ReactiveRedisConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))

    @Bean
    fun configurationTopicRedis(factory: ReactiveRedisConnectionFactory): ConfigurationTopicRedis =
            ConfigurationTopicRedis(factory, mapper)
}

class TopicMessagingConfigurationRedis(private val config: ConfigurationTopicRedis) {
    fun topicMessagingRedis(): ChatTopicMessagingService<*, *> =
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