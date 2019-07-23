package com.demo.chatevents.config

import com.demo.chatevents.topic.ChatMessage
import com.demo.chatevents.topic.JoinAlertMessage
import com.demo.chatevents.topic.TopicData
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

interface ConfigurationPropertiesTopicRedis {
    val host: String
    val port: Int
}

class ConfigurationTopicRedis(val props: ConfigurationPropertiesTopicRedis) {

    @Bean
    fun redisConnection(): ReactiveRedisConnectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))

    @Bean
    fun objectMapper(): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                registerSubtypes(JoinAlertMessage::class.java, ChatMessage::class.java, TopicData::class.java)
            }.findAndRegisterModules()!!

    @Bean
    fun topicTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, TopicData> {
        val keys = StringRedisSerializer()
        val values = Jackson2JsonRedisSerializer(TopicData::class.java)
        values.setObjectMapper(objectMapper())           // KOTLIN USERS : use setObjectMapper!

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, TopicData> =
                RedisSerializationContext.newSerializationContext(keys)

        builder.key(keys)
        builder.value(values)
        builder.hashKey(keys)
        builder.hashValue(values)

        return ReactiveRedisTemplate(cf, builder.build())
    }

    @Bean
    fun objectTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, Object> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Object> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = JdkSerializationRedisSerializer(this.javaClass.classLoader)

        builder.key(keys)
        builder.hashKey(keys)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(cf, builder.build())
    }

}