package com.demo.chat.domain.serializers

import com.demo.chat.codec.JsonNodeAnyCodec
import com.demo.chat.codec.JsonNodeStringCodec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.module.SimpleModule


class ChatModules {

    fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(MessageKey::class.java, MessageKeyDeserializer(JsonNodeAnyCodec))
        addDeserializer(Key::class.java, KeyDeserializer(JsonNodeAnyCodec))
    }

    fun  userModule() = SimpleModule("UserModule", Version.unknownVersion()).apply {
        addDeserializer(User::class.java, UserDeserializer(JsonNodeAnyCodec))
    }

    fun topicModule() = SimpleModule("TopicModule", Version.unknownVersion()).apply {
        addDeserializer(MessageTopic::class.java, TopicDeserializer(JsonNodeAnyCodec))
    }

    fun messageModule() = SimpleModule("MessageModule", Version.unknownVersion()).apply {
        addDeserializer(Message::class.java, MessageDeserializer(JsonNodeAnyCodec, JsonNodeStringCodec))
        addDeserializer(TextMessage::class.java, TextMessageDeserializer(JsonNodeAnyCodec))
    }

    fun membershipModule() = SimpleModule("MembershipModule", Version.unknownVersion()).apply {
        addDeserializer(Membership::class.java, MembershipDeserializer(JsonNodeAnyCodec))
    }
}