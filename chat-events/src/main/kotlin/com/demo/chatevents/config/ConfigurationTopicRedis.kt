package com.demo.chatevents.config

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.Message
import com.demo.chat.domain.serializers.JacksonModules
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.context.annotation.Bean
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.*
import java.lang.reflect.ParameterizedType
import java.util.*

interface ConfigurationPropertiesTopicRedis {
    val host: String
    val port: Int
}

class ConfigurationTopicRedis(val props: ConfigurationPropertiesTopicRedis) {

    @Bean
    fun redisConnection(): ReactiveRedisConnectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(props.host, props.port))

    @Bean
    fun modules(): JacksonModules = JacksonModules(
            JsonNodeAnyCodec, JsonNodeAnyCodec
    )

    @Bean
    fun objectMapper(): ObjectMapper =
            jacksonObjectMapper().registerModule(KotlinModule()).apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
                registerModules(
                        modules().messageModule(),
                        modules().keyModule(),
                        modules().topicModule(),
                        modules().membershipModule(),
                        modules().userModule()
                )
            }!!

    @Bean
    fun <T> stringMessageTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, Message<T, String>> {
        val keys = StringRedisSerializer()
        val mapper = objectMapper()

        // TODO WHYYYYYY
        val values: RedisSerializer<Message<T, String>> = CustomRedisSerializer(mapper)

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Message<T, String>> =
                RedisSerializationContext.newSerializationContext(keys)

        builder.apply {
            key(keys)
            hashKey(keys)
            value(values)
            hashValue(values)
        }

        return ReactiveRedisTemplate(cf, builder.build())
    }

    @Bean
    fun objectTemplate(cf: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, Any> {
        val keys = StringRedisSerializer()

        val builder: RedisSerializationContext.RedisSerializationContextBuilder<String, Any> =
                RedisSerializationContext.newSerializationContext(keys)

        val defaultSerializer = JdkSerializationRedisSerializer(this.javaClass.classLoader)

        builder.key(keys)
        builder.hashKey(keys)
        builder.hashValue(defaultSerializer)

        return ReactiveRedisTemplate(cf, builder.build())
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