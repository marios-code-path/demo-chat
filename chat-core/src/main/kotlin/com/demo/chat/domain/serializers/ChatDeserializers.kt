package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

class MessageKeyDeserializer<T>(val keyCodec: Codec<JsonNode, out T>) : JsonDeserializer<MessageKey<out T>>() {
    private val kd = KeyDeserializer<T, MessageKey<T>>(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageKey<out T> {
        return kd.deserialize(jp, ctxt)
    }
}

class KeyDeserializer<T, W: Key<out T>>(val codec: Codec<JsonNode, out T>) : JsonDeserializer<W>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): W {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)
        val idNode = node.get("id")

        if (node.has("dest")) {
            val destNode = node.get("dest")

            return if (node.has("userId"))
                UserMessageKey.create(codec.decode(idNode), codec.decode(destNode), codec.decode(node.get("userId"))) as W
            else
                MessageKey.create(codec.decode(idNode), codec.decode(destNode)) as W
        }

        return Key.funKey(codec.decode(idNode)) as W
    }
}

class TextMessageDeserializer<T>(val keyCodec: Codec<JsonNode, out T>) : JsonDeserializer<TextMessage<out T>>() {
    private val kd = MessageDeserializer<T, String, TextMessage<T>>(keyCodec, JsonNodeStringCodec)

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): TextMessage<out T> {
        return kd.deserialize(p, ctxt)
    }

}

class MessageDeserializer<T, E, W: Message<out T, out E>>(val keyCodec: Codec<JsonNode, out T>,
                                val elCodec: Codec<JsonNode, out E>) : JsonDeserializer<W>() {
    private val kd = KeyDeserializer<T, Key<T>>(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): W {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val data = node.get("data")
        val visible = node.get("visible").asBoolean()

        val keyNode = node.get("key")

        var key: Key<out T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        // TODO: Need more elegant solution
        return when(key) {
            is UserMessageKey<out T> -> TextMessage.create(key, JsonNodeStringCodec.decode(data) ,visible)
            else -> Message.create(key, elCodec.decode(data), visible)
        } as W

    }
}

class TopicDeserializer<T>(val keyCodec: Codec<JsonNode, T>) : JsonDeserializer<MessageTopic<out T>>() {
    private val kd = KeyDeserializer<T, Key<T>>(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<out T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<out T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())
    }
}