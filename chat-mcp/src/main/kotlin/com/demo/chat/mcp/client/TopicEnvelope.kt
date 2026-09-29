package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.config.parseIdText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The wrapper that `KeyValuePair` adds around a topic. */
private const val VALUE_WRAPPER: String = "keyValue"

/** The wrapper that `Key` adds around a key. */
private const val KEY_WRAPPER: String = "key"

private const val DATA_FIELD: String = "data"
private const val ID_FIELD: String = "id"
private const val ROOT_FIELD: String = "root"
private const val EMPTY_FIELD: String = "empty"

/**
 * Decode one topic response.
 *
 * Measured on 2026-09-28 against a memory deployment. The body is:
 *
 * ```
 * {"keyValue":{"data":"mcpcontracttopic","key":{"key":{"id":1554361326074068992,
 *  "root":1554361143634427905,"empty":false}}}}
 * ```
 *
 * Two wrappers are present. The outer one comes from `KeyValuePair`, and the
 * inner one comes from `Key`. The field order is not part of the contract.
 *
 * A Long id arrives as a JSON number above 2^53. The exact text is read from
 * the document. No Double and no Float ever holds an id.
 */
fun decodeTopic(body: String, keyType: KeyType): Topic {
    val document =
        try {
            Json.parseToJsonElement(body)
        } catch (failure: SerializationException) {
            throw ClientException("the response is not JSON")
        }
    val wrapped = field(document, VALUE_WRAPPER)
    val topic = objectOf(wrapped, "the '$VALUE_WRAPPER' field")
    val keyWrapper = objectOf(field(topic, KEY_WRAPPER), "the '$KEY_WRAPPER' field")
    val key = objectOf(field(keyWrapper, KEY_WRAPPER), "the inner '$KEY_WRAPPER' field")

    if (readEmpty(field(key, EMPTY_FIELD))) {
        throw ClientException("the returned key is empty")
    }
    return Topic(
        id = canonicalId(field(key, ID_FIELD), ID_FIELD, keyType),
        root = canonicalId(field(key, ROOT_FIELD), ROOT_FIELD, keyType),
        data = text(field(topic, DATA_FIELD), DATA_FIELD),
    )
}

/** Read one field, or refuse the response. */
private fun field(container: JsonElement, name: String): JsonElement {
    val object0 = container as? JsonObject ?: throw ClientException("the response holds no '$name' field")
    return object0[name] ?: throw ClientException("the response holds no '$name' field")
}

/** Require a JSON object. */
private fun objectOf(element: JsonElement, what: String): JsonObject =
    element as? JsonObject ?: throw ClientException("$what is not a JSON object")

/** Require a JSON string. */
private fun text(element: JsonElement, name: String): String {
    val primitive = primitiveOf(element, name)
    if (!primitive.isString) {
        throw ClientException("the '$name' field is not a JSON string")
    }
    return primitive.content
}

/**
 * Read one id as canonical text.
 *
 * The key type fixes the JSON shape. A Long deployment sends a JSON number,
 * and a uuid deployment sends a JSON string. The other shape is refused, so a
 * backend change fails here rather than later.
 *
 * The text is then checked against the id rules of the configuration.
 */
private fun canonicalId(element: JsonElement, name: String, keyType: KeyType): String {
    val primitive = primitiveOf(element, name)
    when (keyType) {
        KeyType.LONG ->
            if (primitive.isString) {
                throw ClientException("the '$name' field is a JSON string, not a JSON number")
            }
        KeyType.UUID ->
            if (!primitive.isString) {
                throw ClientException("the '$name' field is not a JSON string")
            }
    }
    val text = primitive.content
    return try {
        parseIdText(text, keyType).text
    } catch (failure: ConfigException) {
        throw ClientException("the returned '$name' is refused: ${failure.message}")
    }
}

/**
 * Require a JSON value.
 *
 * A JSON null is a primitive with the text `null`. It is refused here, so the
 * four character text never reaches an id or a name.
 */
private fun primitiveOf(element: JsonElement, name: String): JsonPrimitive {
    if (element is JsonNull) {
        throw ClientException("the '$name' field is null")
    }
    return element as? JsonPrimitive
        ?: throw ClientException("the '$name' field is not a JSON value")
}

/** Read the `empty` flag. */
private fun readEmpty(element: JsonElement): Boolean {
    val primitive = primitiveOf(element, EMPTY_FIELD)
    if (primitive.isString) {
        throw ClientException("the '$EMPTY_FIELD' field is not a JSON boolean")
    }
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> throw ClientException("the '$EMPTY_FIELD' field is not a JSON boolean")
    }
}
