package com.demo.chat.config

import com.demo.chat.domain.Message
import com.demo.chat.domain.serializers.MessageSerializerRedis
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

interface ConfigurationPropertiesRedis {
    val host: String
    val port: Int
}

class RedisTemplateConfiguration(private val connectionFactory: ReactiveRedisConnectionFactory,
                                 private val objectMapper: ObjectMapper) {
    fun stringTemplate(): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(connectionFactory)

    fun <T> stringMessageTemplate(): ReactiveRedisTemplate<String, Message<T, String>> {
        val keys = StringRedisSerializer()

        val values: RedisSerializer<Message<T, String>> = MessageSerializerRedis(objectMapper)

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

    fun anyTemplate(): ReactiveRedisTemplate<String, Any> {
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