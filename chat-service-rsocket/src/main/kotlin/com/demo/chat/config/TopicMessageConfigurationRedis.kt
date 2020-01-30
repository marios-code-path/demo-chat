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
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import java.util.*

class StringUUIDKeyDecoder : Codec<String, UUID> {
    override fun decode(record: String): UUID {
        return UUID.fromString(record)
    }
}

class UUIDKeyStringEncoder : Codec<UUID, String> {
    override fun decode(record: UUID): String {
        return record.toString()
    }
}

@ConstructorBinding
@ConfigurationProperties("redis-topics")
data class ConfigurationPropertiesRedisTopics(override val host: String = "127.0.0.1",
                                              override val port: Int = 6379) : ConfigurationPropertiesTopicRedis

open class TopicMessagingConfigurationRedis(private val props: ConfigurationPropertiesTopicRedis) {
    @Bean
    fun redisConnection(): ReactiveRedisConnectionFactory =
            LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))

    @Bean
    fun configurationTopicRedis(factory: ReactiveRedisConnectionFactory,
                                mapper: ObjectMapper): ConfigurationTopicRedis = ConfigurationTopicRedis(factory, mapper)

    @Bean
    fun modules(): JacksonModules = JacksonModules(JsonNodeAnyCodec, JsonNodeAnyCodec)

    @Bean
    fun objectMapper(modules: JacksonModules): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                findAndRegisterModules()
                with(modules) {
                    registerModules(
                            messageModule(),
                            keyModule(),
                            topicModule(),
                            membershipModule(),
                            userModule())
                }
            }!!

    @Bean
    fun topicMessagingRedis(config: ConfigurationTopicRedis): ChatTopicMessagingService<*, *> =
            TopicMessagingServiceRedisPubSub(
                    KeyConfigurationPubSub("all_topics",
                            "st_topic_",
                            "l_user_topics_",
                            "l_topic_users_"),
                    config.stringTemplate(),
                    config.stringMessageTemplate(),
                    StringUUIDKeyDecoder(),
                    UUIDKeyStringEncoder()
            )
}