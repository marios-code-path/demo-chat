package com.demo.chat.domain.serializers

import com.demo.chat.convert.Encoder
import com.demo.chat.convert.JsonNodeToAnyEncoder
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean

open class DefaultChatJacksonModules() : JacksonModules(JsonNodeToAnyEncoder, JsonNodeToAnyEncoder)

open class JacksonModules(private val keyEncoder: Encoder<JsonNode, out Any>,
                          private val dataEncoder: Encoder<JsonNode, out Any>) {

    @Bean
    open fun keyDataPairModule() = SimpleModule("KeyDataPairModule", Version.unknownVersion()).apply {
        addDeserializer(KeyDataPair::class.java, KeyDataPairDeserializer(keyEncoder, dataEncoder))
    }

    @Bean
    open fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(keyEncoder))
    }

    @Bean
    open fun  userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(keyEncoder))
    }

    @Bean
    open fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(keyEncoder))
    }

    @Bean
    open fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(keyEncoder, dataEncoder))
    }

    @Bean
    open fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(TopicMembership::class.java, MembershipDeserializer(JsonNodeToAnyEncoder))
    }
}