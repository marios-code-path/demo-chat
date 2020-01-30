package com.demo.chatevents.config

import com.demo.chat.domain.Message
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

interface ConfigurationPropertiesTopicRedis {
    val host: String
    val port: Int
}

class ConfigurationTopicRedis(private val connectionFactory: ReactiveRedisConnectionFactory,
                              private val objectMapper: ObjectMapper) {
    fun stringTemplate(): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(connectionFactory)

    fun <T> stringMessageTemplate(): ReactiveRedisTemplate<String, Message<T, String>> {
        val keys = StringRedisSerializer()

        val values: RedisSerializer<Message<T, String>> = CustomRedisSerializer(objectMapper)

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Message<T, String>> =
                RedisSerializationContext.newSerializationContext(keys)

        builder.apply {
            key(keys)
            hashKey(keys)
            value(values)
            hashValue(values)
        }

        return ReactiveRedisTemplate(connectionFactory, builder.build())
    }

    fun objectTemplate(): ReactiveRedisTemplate<String, Any> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Any> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = JdkSerializationRedisSerializer(this.javaClass.classLoader)

        builder.key(keys)
        builder.hashKey(keys)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(connectionFactory, builder.build())
    }
}

class CustomRedisSerializer<T>(private val om: ObjectMapper) : RedisSerializer<Message<T, String>> {
    override fun serialize(t: Message<T, String>?): ByteArray {
        return om.writeValueAsBytes(t)
    }

    override fun deserialize(bytes: ByteArray?): Message<T, String>? {
       if(bytes == null)
           return null

        return om.readValue<Message<T, String>>(bytes)
    }

}