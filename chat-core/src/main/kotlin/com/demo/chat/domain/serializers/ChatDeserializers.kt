package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

class KeyDeserializer<T>(val codec: Codec<JsonNode, T>) : JsonDeserializer<Key<T>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val idNode = node.get("id")

        return if (node.has("dest") && node.has("from")) {
            val destNode = node.get("dest")
            val fromNode = node.get("from")

            MessageKey.create(codec.decode(idNode), codec.decode(fromNode), codec.decode(destNode))
        } else
            Key.funKey(codec.decode(idNode))
    }
}

class MessageDeserializer<T, E>(keyCodec: Codec<JsonNode, T>,
                                val dataCodec: Codec<JsonNode, E>) : JsonDeserializer<Message<T, E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Message<T, E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.decode(node.get("data"))
        val visible = node.get("record").asBoolean()

        val keyNode = node.get("key")

        var key: Key<T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is MessageKey<T> -> Message.create(key, decoded, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class UserDeserializer<T>(keyCodec: Codec<JsonNode, T>) : JsonDeserializer<User<T>>() {
    private val kd = KeyDeserializer(keyCodec)
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): User<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return User.create(key,
                node.get("name").asText(),
                node.get("handle").asText(),
                node.get("imageUri").asText())
    }
}

class MembershipDeserializer<T>(val keyCodec: Codec<JsonNode, T>) : JsonDeserializer<TopicMembership<T>>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TopicMembership<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key")
        val memberOfNode = node.get("memberOf")
        val memberNode = node.get("member")

        val key: T = keyCodec.decode(keyNode)
        val mem: T = keyCodec.decode(memberNode)
        val mof: T = keyCodec.decode(memberOfNode)

        return TopicMembership.create(key, mem, mof)
    }
}

class TopicDeserializer<T>(keyCodec: Codec<JsonNode, T>) : JsonDeserializer<MessageTopic<T>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("Key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())
    }
}