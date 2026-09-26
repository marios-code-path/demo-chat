package com.demo.chat.domain.serializers

import com.demo.chat.convert.Converter
import com.demo.chat.convert.KeyAssembly
import com.demo.chat.domain.*
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

/**
 * TODO: make deserializers recursively descend into 'data' objects as they can also be JSON.
 */
class MessageKeyDeserializer<T>(private val nodeConverter: Converter<JsonNode, T>) : JsonDeserializer<MessageKey<T>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageKey<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        return MessageKey.of(
            nodeConverter.convert(node.get("id"))!!,
            requireRoot(jp, node, nodeConverter),
            nodeConverter.convert(node.get("from"))!!,
            nodeConverter.convert(node.get("dest"))!!
        )
    }
}

class KeyDeserializer<T>(private val nodeConverter: Converter<JsonNode, T>) : JsonDeserializer<Key<T>>() {
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val id = nodeConverter.convert(node.get("id"))!!
        val root = requireRoot(jp, node, nodeConverter)
        val empty = node.get("empty")?.asBoolean() ?: false
        val from = if (node.has("from")) nodeConverter.convert(node.get("from")) else null
        val dest = if (node.has("dest")) nodeConverter.convert(node.get("dest")) else null

        @Suppress("UNCHECKED_CAST")
        return KeyAssembly.key(id as Any, root as Any, empty, from, dest) as Key<T>
    }
}

/** This function reads the required root of a key node. See `KeyAssembly`. */
private fun <T> requireRoot(jp: JsonParser, node: JsonNode, nodeConverter: Converter<JsonNode, T>): T {
    val rootNode = node.get("root")
    if (rootNode == null || rootNode.isNull) throw JsonMappingException.from(jp, KeyAssembly.MISSING_ROOT)
    return nodeConverter.convert(rootNode)!!
}

class MessageDeserializer<T, E>(
    keyCodec: Converter<JsonNode, T>,
    val dataCodec: Converter<JsonNode, E>
) : JsonDeserializer<Message<T, E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Message<T, E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.convert(node.get("data"))!!
        val visible = node.get("record").asBoolean()

        val keyNode = node.get("key")

        var key: Key<T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is MessageKey<T> -> Message.create(key, decoded, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class KeyValuePairDeserializer<T, E>(
    keyCodec: Converter<JsonNode, T>,
    private val dataCodec: Converter<JsonNode, E>
) : JsonDeserializer<KeyValuePair<T, E>>() {
    private val kd = KeyDeserializer(keyCodec)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): KeyValuePair<T, E> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val decoded = dataCodec.convert(node.get("data"))!!

        val keyNode = node.get("key")

        var key: Key<T> = kd.deserialize(keyNode.first().traverse(oc), ctxt)

        return when (key) {
            is Key<T> -> KeyValuePair.create(key, decoded)
            else -> throw ChatException("Invalid Key Data pair.")
        }
    }
}

class UserDeserializer<T>(keyConverter: Converter<JsonNode, T>) : JsonDeserializer<User<T>>() {
    private val kd = KeyDeserializer(keyConverter)
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): User<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return User.create(
            key,
            node.get("name").asText(),
            node.get("handle").asText(),
            node.get("imageUri").asText()
        )
    }
}

class MembershipDeserializer<T>(val keyConverter: Converter<JsonNode, T>) : JsonDeserializer<TopicMembership<T>>() {

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): TopicMembership<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key")
        val memberOfNode = node.get("memberOf")
        val memberNode = node.get("member")

        val key: T = keyConverter.convert(keyNode)!!
        val mem: T = keyConverter.convert(memberNode)!!
        val mof: T = keyConverter.convert(memberOfNode)!!

        return TopicMembership.create(key, mem, mof)
    }
}

class TopicDeserializer<T>(keyConverter: Converter<JsonNode, T>) : JsonDeserializer<MessageTopic<T>>() {
    private val kd = KeyDeserializer(keyConverter)

    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): MessageTopic<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        return MessageTopic.create(key, node.get("data").asText())
    }
}

class AuthMetadataDeserializer<T>(keyConverter: Converter<JsonNode, T>) : JsonDeserializer<AuthMetadata<T>>() {
    private val kd = KeyDeserializer(keyConverter)
    override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): AuthMetadata<T> {
        val oc: ObjectCodec = jp?.codec!!
        val node: JsonNode = oc.readTree(jp)

        val keyNode = node.get("key").get("key")
        val key: Key<T> = kd.deserialize(keyNode.traverse(oc), ctxt)

        val pplNode = node.get("principal").get("key")
        val principal: Key<T> = kd.deserialize(pplNode.traverse(oc), ctxt)

        val targNode = node.get("target").get("key")
        val target: Key<T> = kd.deserialize(targNode.traverse(oc), ctxt)

        return AuthMetadata.create(
            key,
            principal,
            target,
            node.get("permission").asText(),
            node.get("expires").asLong()
        )
    }

}