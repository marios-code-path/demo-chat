package com.demo.chat.domain.serializers

import com.demo.chat.domain.Message
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.data.redis.serializer.RedisSerializer

class MessageSerializerRedis<T>(private val om: ObjectMapper) : RedisSerializer<Message<T, String>> {
    override fun serialize(t: Message<T, String>?): ByteArray {
        return om.writeValueAsBytes(t)
    }

    override fun deserialize(bytes: ByteArray?): Message<T, String>? {
       if(bytes == null)
           return null

        return om.readValue<Message<T, String>>(bytes)
    }

}