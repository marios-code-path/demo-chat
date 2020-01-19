package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean


open class JacksonModules(val codecKey: Codec<JsonNode, out Any>, val codecData: Codec<JsonNode, out Any>) {

    @Bean
    fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(codecKey))
    }

    @Bean
    fun  userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(codecKey))
    }

    @Bean
    fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(codecKey))
    }

    @Bean
    fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(codecKey, codecData))
    }

    @Bean
    fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(TopicMembership::class.java, MembershipDeserializer(JsonNodeAnyCodec))
    }
}