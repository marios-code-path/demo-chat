package com.demo.chat.domain.serializers

import com.demo.chat.convert.KeyAssembly
import com.demo.chat.convert.NodeValueRules
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.node.JsonNodeType

/**
 * The chat domain deserializers for Jackson 3.
 *
 * **Spring Boot 4 decodes HTTP with Jackson 3, and a Jackson 3 codec cannot
 * load a Jackson 2 module.** The deserializers in `ChatDeserializers.kt` are
 * Jackson 2, so the domain types reached the wire with no deserializer and
 * the index routes answered 500 with a type definition error. See
 * CHAT-qwmjrixq.
 *
 * **Both generations stay.** Jackson 2 serves the stores and the codecs of
 * this repository, and Jackson 3 serves HTTP. Neither replaces the other.
 *
 * **The wire shapes do not change.** `Key` carries its own wrapper, so a
 * nested key reads through `key.key`, and a message key reads the first value
 * of the wrapper. Those readings are copied from the Jackson 2 originals on
 * purpose.
 *
 * The decisions that both generations must share live in `NodeValueRules` and
 * `KeyAssembly`, so a change reaches Jackson 2 and Jackson 3 together.
 */
object Jackson3NodeToAny {

    /**
     * A Jackson 3 node as a domain value.
     *
     * The node type switch is written per generation, because the node types
     * differ. Every decision inside it is shared.
     */
    fun convert(node: JsonNode): Any? = when (node.nodeType) {
        JsonNodeType.MISSING -> throw ChatException("Missing field")
        JsonNodeType.NULL -> null
        JsonNodeType.BINARY -> NodeValueRules.binary(node.binaryValue())
        JsonNodeType.NUMBER -> NodeValueRules.number(node.asDouble()) { node.asLong() }
        JsonNodeType.STRING -> NodeValueRules.text(node.asString())
        JsonNodeType.BOOLEAN -> node.asBoolean()
        JsonNodeType.OBJECT -> node.properties().associate { it.key to convert(it.value) }
        JsonNodeType.ARRAY -> node.values().map { convert(it) }
        else -> node.asString()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> value(node: JsonNode): T = convert(node) as T

    /** The key that a node holds, by the rule both generations share. */
    fun <T : Any> key(node: JsonNode): Key<T> = KeyAssembly.key(
        value(node.get("id")),
        if (node.has("from")) value<T>(node.get("from")) else null,
        if (node.has("dest")) value<T>(node.get("dest")) else null,
    )

    /** The key inside a wrapper, which is how a nested key is written. */
    fun <T : Any> wrappedKey(node: JsonNode): Key<T> = key(node.get("key").get("key"))
}

class Jackson3MessageKeyDeserializer<T : Any> : ValueDeserializer<MessageKey<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageKey<T> {
        val node: JsonNode = p.readValueAsTree()

        return MessageKey.create(
            Jackson3NodeToAny.value(node.get("id")),
            Jackson3NodeToAny.value(node.get("from")),
            Jackson3NodeToAny.value(node.get("dest")),
        )
    }
}

class Jackson3KeyDeserializer<T : Any> : ValueDeserializer<Key<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Key<T> =
        Jackson3NodeToAny.key(p.readValueAsTree())
}

class Jackson3MessageDeserializer<T : Any, E : Any> : ValueDeserializer<Message<T, E>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Message<T, E> {
        val node: JsonNode = p.readValueAsTree()

        val decoded: E = Jackson3NodeToAny.value(node.get("data"))
        val visible = node.get("record").asBoolean()

        // The key sits inside its own wrapper, and the first value of that
        // wrapper is the key itself.
        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first())

        return when (key) {
            is MessageKey<T> -> Message.create(key, decoded, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class Jackson3KeyValuePairDeserializer<T : Any, E : Any> : ValueDeserializer<KeyValuePair<T, E>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KeyValuePair<T, E> {
        val node: JsonNode = p.readValueAsTree()

        val decoded: E = Jackson3NodeToAny.value(node.get("data"))
        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first())

        return KeyValuePair.create(key, decoded)
    }
}

class Jackson3UserDeserializer<T : Any> : ValueDeserializer<User<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): User<T> {
        val node: JsonNode = p.readValueAsTree()

        return User.create(
            Jackson3NodeToAny.wrappedKey(node),
            node.get("name").asString(),
            node.get("handle").asString(),
            node.get("imageUri").asString(),
        )
    }
}

class Jackson3MembershipDeserializer<T : Any> : ValueDeserializer<TopicMembership<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TopicMembership<T> {
        val node: JsonNode = p.readValueAsTree()

        return TopicMembership.create(
            Jackson3NodeToAny.value(node.get("key")),
            Jackson3NodeToAny.value(node.get("member")),
            Jackson3NodeToAny.value(node.get("memberOf")),
        )
    }
}

class Jackson3TopicDeserializer<T : Any> : ValueDeserializer<MessageTopic<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageTopic<T> {
        val node: JsonNode = p.readValueAsTree()

        return MessageTopic.create(
            Jackson3NodeToAny.wrappedKey(node),
            node.get("data").asString(),
        )
    }
}

class Jackson3AuthMetadataDeserializer<T : Any> : ValueDeserializer<AuthMetadata<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AuthMetadata<T> {
        val node: JsonNode = p.readValueAsTree()

        return AuthMetadata.create(
            Jackson3NodeToAny.wrappedKey<T>(node),
            Jackson3NodeToAny.key<T>(node.get("principal").get("key")),
            Jackson3NodeToAny.key<T>(node.get("target").get("key")),
            node.get("permission").asString(),
            node.get("expires").asLong(),
        )
    }
}
