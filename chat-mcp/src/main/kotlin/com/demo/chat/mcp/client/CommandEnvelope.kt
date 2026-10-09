package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import kotlinx.serialization.json.*

fun decodeSend(response: BackendResponse, keyType: KeyType): JsonObject {
    if (response.status in setOf(400, 409, 401, 403, 404)) {
        throw ClientException("the backend refused the submission", reasonFor(response.status), response.status)
    }
    val source = wireDocument(response.body)
    val outcome = wireText(wireField(source, "outcome"))
    val expected = when (outcome) {
        "COMPLETED" -> 201
        "ACCEPTED", "PENDING" -> 202
        "INCOMPLETE" -> 424
        else -> wireRefused()
    }
    if (response.status != expected) wireRefused()
    return buildJsonObject {
        put("receipt", wireReceipt(wireField(source, "receipt"), keyType))
        put("outcome", outcome)
        put("backends", wireBackends(wireField(source, "backends")))
    }
}

fun decodeStatus(body: String, keyType: KeyType, commandId: String): JsonObject {
    val source = wireDocument(body)
    val returnedId = wireText(wireField(source, "commandId"))
    if (returnedId != commandId) wireRefused()
    wireId(wireField(source, "owner"), keyType)
    val receipt = wireReceipt(wireField(source, "receipt"), keyType)
    if (receipt.getValue("commandId").jsonPrimitive.content != commandId) wireRefused()
    return buildJsonObject {
        put("commandId", returnedId)
        put("requestId", wireText(wireField(source, "requestId")))
        put("receipt", receipt)
        put("backends", wireBackends(wireField(source, "backends")))
        put("version", wireCounter(wireField(source, "version")))
    }
}

private fun wireReceipt(value: JsonElement, keyType: KeyType): JsonObject {
    val receipt = wireObject(value)
    val commandId = wireText(wireField(receipt, "commandId"))
    if (commandId.length !in 1..128 || commandId.any { it !in '!'..'~' }) wireRefused()
    val key = wireMessageKey(wireField(receipt, "messageKey"), keyType)
    return buildJsonObject {
        put("commandId", commandId)
        put("messageKey", buildJsonObject {
            put("id", key.getValue("id"))
            put("root", key.getValue("root"))
        })
    }
}

private fun wireBackends(value: JsonElement): JsonObject = buildJsonObject {
    for ((name, element) in wireObject(value)) {
        if (name !in setOf("PERSISTENCE", "INDEX", "VECTOR", "PUBSUB")) wireRefused()
        val backend = wireObject(element)
        val state = wireText(wireField(backend, "state"))
        if (state !in setOf("PENDING", "SUCCEEDED", "FAILED", "UNCERTAIN")) wireRefused()
        val recovery = wireField(backend, "nextRecoveryAt")
        val reason = wireField(backend, "reason")
        if (reason != JsonNull) wireText(reason)
        put(name, buildJsonObject {
            put("state", state)
            put("attempts", wireCounter(wireField(backend, "attempts")))
            put("nextRecoveryAt", if (recovery == JsonNull) JsonNull else JsonPrimitive(wireTime(recovery)))
        })
    }
}

private fun wireCounter(value: JsonElement): Long {
    val primitive = value as? JsonPrimitive ?: wireRefused()
    if (primitive.isString || !Regex("[0-9]+").matches(primitive.content)) wireRefused()
    return primitive.longOrNull?.takeIf { it >= 0 } ?: wireRefused()
}
