package com.demo.chat.mcp.config

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * One adapter id.
 *
 * The text form is canonical. A Long id is exact base-10 text, so a value
 * above 2^53 keeps every digit. A uuid id is lowercase text.
 */
sealed interface AdapterId {
    val text: String
}

/** A Long id. */
data class LongId(val value: Long) : AdapterId {
    override val text: String get() = value.toString()
}

/** A uuid id. */
data class UuidId(val value: UUID) : AdapterId {
    override val text: String get() = value.toString()
}

/** Canonical base-10 text. A leading zero, a plus sign and a space all fail here. */
private val CANONICAL_LONG = Regex("-?(0|[1-9][0-9]*)")

private val ZERO_UUID = UUID(0L, 0L)

/** Parse canonical id text for the configured key type. */
fun parseIdText(text: String, keyType: KeyType): AdapterId =
    when (keyType) {
        KeyType.LONG -> LongId(requireCanonicalLong(text))
        KeyType.UUID -> UuidId(requireCanonicalUuid(text))
    }

/**
 * Parse an id from a JSON value.
 *
 * Both tool schemas declare every id as a string. A JSON number is refused
 * here, before any backend call. So a client that sends `5` or `5.5` fails at
 * the boundary, and no Double or Float ever holds an id.
 */
fun parseIdElement(element: JsonElement, keyType: KeyType): AdapterId {
    val primitive =
        element as? JsonPrimitive
            ?: throw ConfigException("an id must be a JSON string")
    if (!primitive.isString) {
        throw ConfigException("an id must be a JSON string, not a JSON number or boolean")
    }
    return parseIdText(primitive.content, keyType)
}

/**
 * Require canonical base-10 text for a Long id.
 *
 * The text must hold no whitespace, no plus sign, no leading zero, no
 * fraction and no exponent. The value must fit the Long range and must not be
 * zero.
 */
fun requireCanonicalLong(text: String): Long {
    if (!CANONICAL_LONG.matches(text)) {
        throw ConfigException("'$text' is not canonical base-10 integer text")
    }
    val value =
        text.toLongOrNull()
            ?: throw ConfigException("'$text' is above the Long range")
    if (value == 0L) {
        throw ConfigException("an id must not be zero")
    }
    return value
}

/**
 * Require canonical lowercase text for a uuid id.
 *
 * The text must round-trip through [UUID.toString] unchanged. That refuses an
 * uppercase value, a brace, a `urn:` prefix and a shortened group. The zero
 * uuid is refused.
 */
fun requireCanonicalUuid(text: String): UUID {
    val value =
        runCatching { UUID.fromString(text) }.getOrNull()
            ?: throw ConfigException("'$text' is not a uuid")
    if (value.toString() != text) {
        throw ConfigException("'$text' is not canonical lowercase uuid text")
    }
    if (value == ZERO_UUID) {
        throw ConfigException("an id must not be the zero uuid")
    }
    return value
}