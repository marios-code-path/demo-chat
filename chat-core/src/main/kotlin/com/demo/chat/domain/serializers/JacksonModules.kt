package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean

open class JacksonModules(private val codecKey: Codec<JsonNode, out Any>,
                          private val codecData: Codec<JsonNode, out Any>) {

    @Bean
    open fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(codecKey))
    }

    @Bean
    open fun  userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(codecKey))
    }

    @Bean
    open fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(codecKey))
    }

    @Bean
    open fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(codecKey, codecData))
    }

    @Bean
    open fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(TopicMembership::class.java, MembershipDeserializer(JsonNodeAnyCodec))
    }
}