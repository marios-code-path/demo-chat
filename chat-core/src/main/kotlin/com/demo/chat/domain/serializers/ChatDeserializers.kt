package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

class MessageKeyDeserializer(val keyCodec: Codec<JsonNode, out Any>) : JsonDeserializer<MessageKey<out Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageKey<out Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)
        val idNode = node.get("id")
        val destNode = node.get("dest")

        return MessageKey.create(keyCodec.decode(idNode), keyCodec.decode(destNode))
    }
}

class KeyDeserializer : JsonDeserializer<Key<out Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<out Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)
        val idNode = node.get("id")

        return when (idNode.nodeType) {
            JsonNodeType.NUMBER -> Key.anyKey(idNode.asLong())
            JsonNodeType.STRING -> {
                try {
                    Key.anyKey(UUID.fromString(idNode.asText()))
                } catch (e: Exception) {
                    Key.anyKey(idNode.asText())
                }
            }
            else -> Key.anyKey(idNode.asText())
        }
    }
}

class MessageDeserializer(val keyCodec: Codec<JsonNode, out Any>): JsonDeserializer<Message<out Any, out Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Message<out Any, out Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val text = node.get("data").asText()
        val visible = node.get("visible").asBoolean()

        val keyNode = node.get("key")

        var key: Key<out Any>
        when {
            keyNode.has("Key") -> key = KeyDeserializer().deserialize(keyNode.get("Key").traverse(oc), ctxt)
            keyNode.has("MessageKey") -> key = MessageKeyDeserializer(keyCodec).deserialize(keyNode.get("MessageKey").traverse(oc), ctxt)
            else -> throw ChatException("Invalid Message Key")
        }

        return Message.create(
                key,
                text,
                visible
        )
    }
}

class TextMessageDeserializer<T>(val keyCodec: Codec<JsonNode, T>) : JsonDeserializer<TextMessage<T>>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TextMessage<T> {

        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val text = node.get("data").asText()
        val visible = node.get("visible").asBoolean()

        val keyNode = node.get("key").get("MessageKey")
        val keyId = keyCodec.decode(keyNode.get("id"))
        val topicId = keyCodec.decode(keyNode.get("dest"))
        val userId = keyCodec.decode(keyNode.get("userId"))

        return TextMessage.create(
                UserMessageKey.create(
                        keyId,
                        topicId,
                        userId
                ),
                text,
                visible
        )
    }
}

class TopicDeserializer : JsonDeserializer<MessageTopic<out Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<out Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<out Any> = KeyDeserializer().deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())

    }
}