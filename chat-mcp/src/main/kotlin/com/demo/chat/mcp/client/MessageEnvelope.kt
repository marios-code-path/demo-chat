package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.parseIdText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeParseException

fun decodeMessage(body: String, keyType: KeyType): JsonObject {
    val message = wireObject(wireField(wireDocument(body), "message"))
    val key = wireMessageKey(wireField(message, "key"), keyType)
    val text = wireText(wireField(message, "data"))
    val record = wireField(message, "record") as? JsonPrimitive ?: wireRefused()
    if (record.isString || record.booleanOrNull == null) wireRefused()
    return buildJsonObject {
        put("messageKey", buildJsonObject {
            put("id", key.getValue("id"))
            put("root", key.getValue("root"))
        })
        put("senderId", key.getValue("from"))
        put("topicId", key.getValue("dest"))
        put("text", text)
        put("timestamp", key.getValue("timestamp"))
    }
}

fun decodeHistory(body: String, keyType: KeyType): List<JsonObject> =
    body.lineSequence().filter(String::isNotBlank).map { decodeMessage(it, keyType) }.toList()

internal fun wireRefused(): Nothing =
    throw ClientException("the backend response does not match the message contract", FailureReason.PROTOCOL)

internal fun wireDocument(body: String): JsonObject = try {
    wireObject(Json.parseToJsonElement(body))
} catch (failure: SerializationException) {
    wireRefused()
}

internal fun wireObject(value: JsonElement): JsonObject = value as? JsonObject ?: wireRefused()

internal fun wireField(value: JsonObject, name: String): JsonElement = value[name] ?: wireRefused()

internal fun wireText(value: JsonElement): String {
    val primitive = value as? JsonPrimitive ?: wireRefused()
    if (value == JsonNull || !primitive.isString) wireRefused()
    return primitive.content
}

internal fun wireId(value: JsonElement, keyType: KeyType): String {
    val primitive = value as? JsonPrimitive ?: wireRefused()
    if (value == JsonNull || primitive.isString != (keyType == KeyType.UUID)) wireRefused()
    return try {
        parseIdText(primitive.content, keyType).text
    } catch (failure: ConfigException) {
        wireRefused()
    }
}

internal fun wireTime(value: JsonElement): String = try {
    Instant.parse(wireText(value)).toString()
} catch (failure: DateTimeParseException) {
    wireRefused()
}

internal fun wireMessageKey(value: JsonElement, keyType: KeyType): JsonObject {
    val key = wireObject(wireField(wireObject(value), "key"))
    val empty = wireField(key, "empty") as? JsonPrimitive ?: wireRefused()
    if (empty.isString || empty.booleanOrNull != false) wireRefused()
    return buildJsonObject {
        for (field in listOf("id", "root", "from", "dest")) put(field, wireId(wireField(key, field), keyType))
        put("timestamp", wireTime(wireField(key, "timestamp")))
    }
}
