package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.codec.JsonNodeStringCodec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

// Just De-serializer Doodles here...
// TODO for Deserializers - This must not be the final version
class KeyDeserializer<T>(val codec: Codec<JsonNode, out T>) : JsonDeserializer<Key<out T>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<out T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)
        val idNode = node.get("id")

        if (node.has("dest")) {
            val destNode = node.get("dest")

            return if (node.has("userId"))
                UserMessageKey.create(codec.decode(idNode), codec.decode(destNode), codec.decode(node.get("userId")))
            else
                MessageKey.create(codec.decode(idNode), codec.decode(destNode))
        }

        return Key.funKey(codec.decode(idNode))
    }
}

class MessageDeserializer<T, E>(keyCodec: Codec<JsonNode, out T>,
                                val dataCodec: Codec<JsonNode, out E>) : JsonDeserializer<Message<out T, out E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Message<out T, out E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.decode(node.get("data"))
        val visible = node.get("record").asBoolean()

        val keyNode = node.get("key")

        var key: Key<out T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is MessageKey<out T> -> Message.create(key, decoded, visible)
            is Key<out T> -> throw ChatException("Invalid Destination")
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class UserDeserializer<T>(keyCodec: Codec<JsonNode, T>) : JsonDeserializer<User<out T>>() {
    private val kd = KeyDeserializer(keyCodec)
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): User<out T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<out T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return User.create(key,
                node.get("name").asText(),
                node.get("handle").asText(),
                node.get("imageUri").asText())
    }
}

class MembershipDeserializer<T>(keyCodec: Codec<JsonNode, T>) : JsonDeserializer<Membership<out T>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Membership<out T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val memberOfNode = node.get("memberOf").get("Key")
        val memberNode = node.get("member").get("Key")

        val key: Key<out T> = kd.deserialize(keyNode.traverse(oc), ctxt)
        val mem: Key<out T> = kd.deserialize(memberNode.traverse(oc), ctxt)
        val mof: Key<out T> = kd.deserialize(memberOfNode.traverse(oc), ctxt)

        return Membership.create(key,
                mem,
                mof)
    }
}

class TopicDeserializer<T>(keyCodec: Codec<JsonNode, T>) : JsonDeserializer<MessageTopic<out T>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<out T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<out T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())
    }
}