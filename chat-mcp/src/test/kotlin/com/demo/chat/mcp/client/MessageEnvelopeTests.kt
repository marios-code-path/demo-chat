package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessageEnvelopeTests {
    @Test
    fun `Long and UUID messages project exact identifiers`() {
        for (kind in KeyType.entries) {
            val result = decodeMessage(messagingFixture(kind, "message.json"), kind)
            val expected = if (kind == KeyType.LONG) "1554361326074068992" else "6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b"
            assertEquals(expected, result.getValue("messageKey").jsonObject.getValue("id").jsonPrimitive.content)
            assertEquals(setOf("messageKey", "senderId", "topicId", "text", "timestamp"), result.keys)
            assertEquals("hello λ", result.getValue("text").jsonPrimitive.content)
            assertEquals("2026-10-09T12:00:00Z", result.getValue("timestamp").jsonPrimitive.content)
            assertTrue(result.getValue("senderId").jsonPrimitive.isString)
            assertTrue(result.getValue("topicId").jsonPrimitive.isString)
        }
    }

    @Test
    fun `histories preserve order and ignore blank lines`() {
        for (kind in KeyType.entries) {
            val history = messagingFixture(kind, "history.ndjson")
            val first = decodeMessage(messagingFixture(kind, "message.json"), kind)
            assertEquals(listOf(first, first), decodeHistory("\n$history\n", kind))
            assertEquals(emptyList<JsonObject>(), decodeHistory("\n  \n", kind))
        }
    }

    @Test
    fun `a malformed final history record refuses the whole response`() {
        val body = messagingFixture(KeyType.LONG, "history.ndjson") + "{broken}\n"
        assertThrows(ClientException::class.java) { decodeHistory(body, KeyType.LONG) }
    }

    @Test
    fun `message keys refuse absent roots and wrong scalar types`() {
        val invalid = listOf(
            listOf("message", "key", "key", "root") to null,
            listOf("message", "key", "key", "id") to JsonPrimitive("1554361326074068992"),
            listOf("message", "key", "key", "from") to JsonPrimitive(1.5),
            listOf("message", "key", "key", "dest") to JsonNull,
            listOf("message", "key", "key", "empty") to JsonPrimitive(true),
            listOf("message", "key", "key", "timestamp") to JsonPrimitive("invalid"),
            listOf("message", "data") to JsonPrimitive(7),
        )
        for ((path, value) in invalid) {
            val body = alteredFixture(KeyType.LONG, "message.json", path, value)
            assertEquals(FailureReason.PROTOCOL, assertThrows(ClientException::class.java) {
                decodeMessage(body, KeyType.LONG)
            }.reason)
        }
    }

    @Test
    fun `UUID fields require canonical strings`() {
        for (value in listOf(JsonPrimitive(7), JsonPrimitive("6F1E0B3A-2C4D-4E5F-8A9B-0C1D2E3F4A5B"), JsonNull)) {
            val body = alteredFixture(KeyType.UUID, "message.json", listOf("message", "key", "key", "id"), value)
            assertThrows(ClientException::class.java) { decodeMessage(body, KeyType.UUID) }
        }
    }
}

internal fun messagingFixture(kind: KeyType, shape: String): String =
    MessageEnvelopeTests::class.java.getResourceAsStream("/messaging/${kind.name.lowercase()}-$shape")!!
        .use { it.readBytes().toString(Charsets.UTF_8) }

internal fun alteredFixture(kind: KeyType, shape: String, path: List<String>, value: JsonElement?): String {
    fun replace(current: JsonObject, remaining: List<String>): JsonObject = JsonObject(current.toMutableMap().apply {
        val field = remaining.first()
        if (remaining.size == 1) {
            if (value == null) remove(field) else put(field, value)
        } else put(field, replace(getValue(field).jsonObject, remaining.drop(1)))
    })
    return replace(Json.parseToJsonElement(messagingFixture(kind, shape)).jsonObject, path).toString()
}
