package com.demo.chat.domain.serializers

import com.demo.chat.convert.Converter
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean

open class JacksonModules(
    private val keyConverter: Converter<JsonNode, out Any>,
    private val dataConverter: Converter<JsonNode, out Any>
) {

    @Bean
    open fun keyDataPairModule() = SimpleModule("KeyDataPairModule", Version.unknownVersion()).apply {
        addDeserializer(KeyDataPair::class.java, KeyDataPairDeserializer(keyConverter, dataConverter))
    }

    @Bean
    open fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(keyConverter))
        addDeserializer(MessageKey::class.java, MessageKeyDeserializer(keyConverter))
    }

    @Bean
    open fun userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(keyConverter))
    }

    @Bean
    open fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(keyConverter))
    }

    @Bean
    open fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(keyConverter, dataConverter))
    }

    @Bean
    open fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(TopicMembership::class.java, MembershipDeserializer(JsonNodeToAnyConverter))
    }

    @Bean
    open fun authMetadataModule() = SimpleModule("AuthMetadataModule", Version.unknownVersion()).apply {
        addDeserializer(AuthMetadata::class.java, AuthMetadataDeserializer(JsonNodeToAnyConverter))
    }
}