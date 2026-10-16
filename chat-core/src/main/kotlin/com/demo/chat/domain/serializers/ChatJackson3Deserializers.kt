package com.demo.chat.domain.serializers

import com.demo.chat.convert.KeyAssembly
import com.demo.chat.convert.NodeValueRules
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.MessagePointerRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.PointerMessage
import com.demo.chat.domain.SimpleMessageKey
import java.time.Instant
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import tools.jackson.core.JsonParser
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.cfg.DateTimeFeature
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
    fun <T : Any> key(node: JsonNode, context: DeserializationContext? = null): Key<T> = KeyAssembly.key(
        value(node.get("id")),
        root(node),
        node.get("empty")?.asBoolean() ?: false,
        if (node.has("from")) value<T>(node.get("from")) else null,
        if (node.has("dest")) value<T>(node.get("dest")) else null,
        Jackson3NodeToAny.timestamp(node, context),
    )

    fun timestamp(node: JsonNode, context: DeserializationContext? = null): Instant? {
        val timestamp = node.get("timestamp")?.takeUnless { it.isNull } ?: return null
        val milliseconds = timestamp.isIntegralNumber &&
            context?.isEnabled(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS) == false
        return try {
            KeyAssembly.timestamp(timestamp.asString(), timestamp.isNumber, milliseconds)
        } catch (cause: RuntimeException) {
            throw DatabindException.from(context?.parser, "Invalid message timestamp.", cause)
        }
    }

    /** This function reads the required root of a key node. See `KeyAssembly`. */
    fun <T : Any> root(node: JsonNode): T {
        val rootNode = node.get("root")
        if (rootNode == null || rootNode.isNull) throw DatabindException.from(null as JsonParser?, KeyAssembly.MISSING_ROOT)
        return value(rootNode)
    }

    /** The key inside a wrapper, which is how a nested key is written. */
    fun <T : Any> wrappedKey(node: JsonNode, context: DeserializationContext? = null): Key<T> = key(node.get("key").get("key"), context)
}

/** This function retains decimal precision without changing the caller's reader settings. */
private fun readTimestampTree(parser: JsonParser, context: DeserializationContext): JsonNode =
    context.bufferForInputBuffering(parser).use { buffer ->
        buffer.forceUseOfBigDecimal(true)
        buffer.copyCurrentStructure(parser)
        buffer.asParser(context).use { context.readTree(it) }
    }

class Jackson3MessageKeyDeserializer<T : Any> : ValueDeserializer<MessageKey<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageKey<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        return SimpleMessageKey(
            Jackson3NodeToAny.value(node.get("id")),
            Jackson3NodeToAny.root(node),
            Jackson3NodeToAny.value(node.get("from")),
            Jackson3NodeToAny.value(node.get("dest")),
            Jackson3NodeToAny.timestamp(node, ctxt) ?: Instant.now(),
        )
    }
}

class Jackson3KeyDeserializer<T : Any> : ValueDeserializer<Key<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Key<T> =
        Jackson3NodeToAny.key(readTimestampTree(p, ctxt), ctxt)
}

class Jackson3MessageDeserializer<T : Any, E : Any> : ValueDeserializer<Message<T, E>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Message<T, E> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        val decoded: E = Jackson3NodeToAny.value(node.get("data"))
        val visible = node.get("record").asBoolean()

        // The key sits inside its own wrapper, and the first value of that
        // wrapper is the key itself.
        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first(), ctxt)

        return when (key) {
            is MessageKey<T> -> Message.create(key, decoded, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

/** A pointer. See `PointerMessageDeserializer` and `CHAT-erkoxybm`. */
class Jackson3PointerMessageDeserializer<T : Any> : ValueDeserializer<PointerMessage<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PointerMessage<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first(), ctxt)
        val target = Jackson3NodeToAny.key<T>(node.get("target").values().first(), ctxt)
        // The flag is read, and not defaulted. A decoder that dropped it read a
        // removed pointer as a live one. See `CHAT-erkoxybm`.
        val visible = node.get("record").asBoolean()

        return when (key) {
            is MessageKey<T> -> PointerMessage(key, target, visible)
            else -> throw ChatException("Invalid Message Key")
        }
    }
}

class Jackson3KeyValuePairDeserializer<T : Any, E : Any> : ValueDeserializer<KeyValuePair<T, E>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): KeyValuePair<T, E> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        val decoded: E = Jackson3NodeToAny.value(node.get("data"))
        val key = Jackson3NodeToAny.key<T>(node.get("key").values().first(), ctxt)

        return KeyValuePair.create(key, decoded)
    }
}

class Jackson3UserDeserializer<T : Any> : ValueDeserializer<User<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): User<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        return User.create(
            Jackson3NodeToAny.wrappedKey(node, ctxt),
            node.get("name").asString(),
            node.get("handle").asString(),
            node.get("imageUri").asString(),
        )
    }
}

class Jackson3MembershipDeserializer<T : Any> : ValueDeserializer<TopicMembership<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TopicMembership<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        return TopicMembership.create(
            Jackson3NodeToAny.value(node.get("key")),
            Jackson3NodeToAny.value(node.get("member")),
            Jackson3NodeToAny.value(node.get("memberOf")),
        )
    }
}

class Jackson3TopicDeserializer<T : Any> : ValueDeserializer<MessageTopic<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageTopic<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        return MessageTopic.create(
            Jackson3NodeToAny.wrappedKey(node, ctxt),
            node.get("data").asString(),
        )
    }
}

class Jackson3AuthMetadataDeserializer<T : Any> : ValueDeserializer<AuthMetadata<T>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AuthMetadata<T> {
        val node: JsonNode = readTimestampTree(p, ctxt)

        return AuthMetadata.create(
            Jackson3NodeToAny.wrappedKey<T>(node, ctxt),
            Jackson3NodeToAny.key<T>(node.get("principal").get("key"), ctxt),
            Jackson3NodeToAny.key<T>(node.get("target").get("key"), ctxt),
            node.get("permission").asString(),
            node.get("expires").asLong(),
        )
    }
}

// The id requests that the RSocket server decodes. See `CHAT-oeyxthsy`.
//
// A request type is generic in its key type, and the handler cannot resolve
// that type. The controllers are generic, so Jackson binds a raw id with no
// type. A UUID id then decoded as a `String`, and the registry lookup missed.
// These deserializers read each id by the rule that a `Key` id uses. The wire
// shape does not change.

/** A required field. A missing field fails the decode and names the field. */
private fun required(node: JsonNode, field: String, context: DeserializationContext): JsonNode {
    val value = node.get(field)
    if (value == null || value.isNull) throw DatabindException.from(context.parser, "The request has no $field.")
    return value
}

/** A required id field, read by the rule that a `Key` id uses. */
private fun requiredId(node: JsonNode, field: String, context: DeserializationContext): Any =
    Jackson3NodeToAny.value(required(node, field, context))

/** A message text is a value, not an id, so it keeps the plain Jackson reading. */
private fun requiredValue(node: JsonNode, field: String, context: DeserializationContext): Any =
    context.readTreeAsValue(required(node, field, context), Any::class.java)

/** A required text field, such as a request id. */
private fun requiredText(node: JsonNode, field: String, context: DeserializationContext): String =
    required(node, field, context).asString()

// Each request below reads its tree with readTree. The type property is read
// first, so the parser can start at a field name, and readTree reads from there.

class Jackson3ByIdRequestDeserializer : ValueDeserializer<ByIdRequest<Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ByIdRequest<Any> =
        ByIdRequest(requiredId(ctxt.readTree(p), "id", ctxt))
}

class Jackson3MessageImportRequestDeserializer : ValueDeserializer<MessageImportRequest<Any, Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageImportRequest<Any, Any> {
        val node: JsonNode = ctxt.readTree(p)

        return MessageImportRequest(
            requiredValue(node, "msg", ctxt),
            requiredId(node, "from", ctxt),
            requiredId(node, "dest", ctxt),
            ctxt.readTreeAsValue(required(node, "timestamp", ctxt), Instant::class.java),
            requiredText(node, "messageId", ctxt),
            node.get("publish")?.asBoolean() ?: false,
        )
    }
}

class Jackson3MembershipRequestDeserializer : ValueDeserializer<MembershipRequest<Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MembershipRequest<Any> {
        val node: JsonNode = ctxt.readTree(p)

        return MembershipRequest(requiredId(node, "uid", ctxt), requiredId(node, "roomId", ctxt))
    }
}

class Jackson3MessageSendRequestDeserializer : ValueDeserializer<MessageSendRequest<Any, Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageSendRequest<Any, Any> {
        val node: JsonNode = ctxt.readTree(p)

        return MessageSendRequest(
            requiredValue(node, "msg", ctxt),
            requiredId(node, "from", ctxt),
            requiredId(node, "dest", ctxt),
        )
    }
}

class Jackson3MessageSubmitRequestDeserializer : ValueDeserializer<MessageSubmitRequest<Any, Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessageSubmitRequest<Any, Any> {
        val node: JsonNode = ctxt.readTree(p)

        return MessageSubmitRequest(
            requiredValue(node, "msg", ctxt),
            requiredId(node, "dest", ctxt),
            requiredText(node, "requestId", ctxt),
        )
    }
}

class Jackson3MessagePointerRequestDeserializer : ValueDeserializer<MessagePointerRequest<Any>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MessagePointerRequest<Any> {
        val node: JsonNode = ctxt.readTree(p)

        return MessagePointerRequest(
            requiredId(node, "dest", ctxt),
            requiredId(node, "targetId", ctxt),
            requiredText(node, "targetDomain", ctxt),
            requiredText(node, "requestId", ctxt),
        )
    }
}
