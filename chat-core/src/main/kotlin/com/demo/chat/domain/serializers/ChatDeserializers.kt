package com.demo.chat.domain.serializers

import com.demo.chat.convert.Encoder
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.converter.ConverterRegistry

/**
 * TODO: make deserializers recursively descend into 'data' objects as they can also be JSON.
 */
class KeyDeserializer<T>(private val nodeEncoder: Encoder<JsonNode, T>) : JsonDeserializer<Key<T>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val idNode = node.get("id")

        return if (node.has("dest") && node.has("from")) {
            val destNode = node.get("dest")
            val fromNode = node.get("from")

            MessageKey.create(nodeEncoder.encode(idNode), nodeEncoder.encode(fromNode), nodeEncoder.encode(destNode))
        } else
            Key.funKey(nodeEncoder.encode(idNode))
    }
}

class MessageDeserializer<T, E>(keyCodec: Encoder<JsonNode, T>,
                                val dataCodec: Encoder<JsonNode, E>) : JsonDeserializer<Message<T, E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Message<T, E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.encode(node.get("data"))
        val visible = node.get("record").asBoolean()

        val keyNode = node.get("key")

        var key: Key<T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is MessageKey<T> -> Message.create(key, decoded, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class KeyDataPairDeserializer<T, E>(keyCodec: Encoder<JsonNode, T>,
                                    val dataCodec: Encoder<JsonNode, E>) : JsonDeserializer<KeyDataPair<T, E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): KeyDataPair<T, E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.encode(node.get("data"))

        val keyNode = node.get("key")

        var key: Key<T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is Key<T> -> KeyDataPair.create(key, decoded)
            else -> throw ChatException("Invalid Key Data pair.")
        }
    }
}

class UserDeserializer<T>(keyEncoder: Encoder<JsonNode, T>) : JsonDeserializer<User<T>>() {
    private val kd = KeyDeserializer(keyEncoder)
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): User<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return User.create(key,
                node.get("name").asText(),
                node.get("handle").asText(),
                node.get("imageUri").asText())
    }
}

class MembershipDeserializer<T>(val keyEncoder: Encoder<JsonNode, T>) : JsonDeserializer<TopicMembership<T>>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TopicMembership<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key")
        val memberOfNode = node.get("memberOf")
        val memberNode = node.get("member")

        val key: T = keyEncoder.encode(keyNode)
        val mem: T = keyEncoder.encode(memberNode)
        val mof: T = keyEncoder.encode(memberOfNode)

        return TopicMembership.create(key, mem, mof)
    }
}

class TopicDeserializer<T>(keyEncoder: Encoder<JsonNode, T>) : JsonDeserializer<MessageTopic<T>>() {
    private val kd = KeyDeserializer(keyEncoder)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())
    }
}