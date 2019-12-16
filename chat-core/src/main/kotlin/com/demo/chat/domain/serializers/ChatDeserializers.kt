package com.demo.chat.domain.serializers

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UserMessageKey
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.UUIDDeserializer
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

// TODO: the wrapper logic makes our data look like this: {"Topic":{"key":{"Key":{"id":"9f27ce44-9413-4180-ba57-2482337d6617"}},"data":"Test-Topic-U"}}
//{
//  "Topic": {
//    "key": {
//      "Key": {
//        "id": "9f27ce44-9413-4180-ba57-2482337d6617"
//      }
//    },
//    "data": "Test-Topic-U"
//  }
//}
class KeyDeserializer : JsonDeserializer<Key<Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)
        val idNode = node.get("id")

        if(idNode.nodeType == JsonNodeType.STRING) {
            return try {
                Key.anyKey(UUID.fromString(idNode.asText()))
            } catch (e: Exception) {
                println(e)
                Key.anyKey(idNode.asText())
            }
        }

        return Key.anyKey(idNode.asLong())
    }
}

class TopicDeserializer : JsonDeserializer<MessageTopic<Any>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<Any> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<Any> = KeyDeserializer().deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())

    }
}

class TextMessageDeserializer : JsonDeserializer<TextMessage<UUID>>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TextMessage<UUID> {

        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val text = node.get("data").asText()
        val visible = node.get("visible").asBoolean()

        val keyNode = node.get("key")
        val keyId = UUID.fromString(keyNode.get("id").asText())
        val topicId = UUID.fromString(keyNode.get("dest").asText())
        val userId = UUID.fromString(keyNode.get("userId").asText())

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