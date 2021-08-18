package com.demo.chat.domain.serializers

import com.demo.chat.codec.Decoder
import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean

open class DefaultChatJacksonModules() : JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)

open class JacksonModules(private val keyDecoder: Decoder<JsonNode, out Any>,
                          private val dataDecoder: Decoder<JsonNode, out Any>) {

    @Bean
    open fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(keyDecoder))
    }

    @Bean
    open fun  userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(keyDecoder))
    }

    @Bean
    open fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(keyDecoder))
    }

    @Bean
    open fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(keyDecoder, dataDecoder))
    }

    @Bean
    open fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(TopicMembership::class.java, MembershipDeserializer(JsonNodeAnyDecoder))
    }

}