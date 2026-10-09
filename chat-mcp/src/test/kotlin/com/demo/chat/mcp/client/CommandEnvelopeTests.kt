package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandEnvelopeTests {
    @Test
    fun `every send outcome projects its receipt and backend progress`() {
        for (kind in KeyType.entries) {
            for ((outcome, status) in listOf("completed" to 201, "accepted" to 202, "pending" to 202, "incomplete" to 424)) {
                val result = decodeSend(BackendResponse(status, messagingFixture(kind, "send-$outcome.json")), kind)
                assertEquals(setOf("receipt", "outcome", "backends"), result.keys)
                assertEquals(outcome.uppercase(), result.getValue("outcome").jsonPrimitive.content)
                val receipt = result.getValue("receipt").jsonObject
                assertEquals(setOf("commandId", "messageKey"), receipt.keys)
                assertEquals("c-rest", receipt.getValue("commandId").jsonPrimitive.content)
                assertEquals(setOf("id", "root"), receipt.getValue("messageKey").jsonObject.keys)
                if (outcome == "accepted") assertTrue(result.getValue("backends").jsonObject.isEmpty())
                else assertEquals(setOf("PERSISTENCE", "INDEX", "VECTOR", "PUBSUB"), result.getValue("backends").jsonObject.keys)
            }
        }
    }

    @Test
    fun `status projects all states without the owner or backend reasons`() {
        for (kind in KeyType.entries) {
            val result = decodeStatus(messagingFixture(kind, "status.json"), kind, "c-rest")
            assertEquals(setOf("commandId", "requestId", "receipt", "backends", "version"), result.keys)
            assertEquals(2147483648L, result.getValue("version").jsonPrimitive.long)
            val backends = result.getValue("backends").jsonObject
            assertEquals(setOf("PENDING", "SUCCEEDED", "UNCERTAIN", "FAILED"), backends.values.map {
                it.jsonObject.getValue("state").jsonPrimitive.content
            }.toSet())
            for (backend in backends.values) assertEquals(setOf("state", "attempts", "nextRecoveryAt"), backend.jsonObject.keys)
            assertEquals(JsonNull, backends.getValue("PERSISTENCE").jsonObject.getValue("nextRecoveryAt"))
            assertFalse(result.toString().contains("private"))
        }
    }

    @Test
    fun `confirmed refusals are classified before parsing JSON`() {
        for (status in listOf(400, 409, 401, 403, 404)) {
            val failure = assertThrows(ClientException::class.java) {
                decodeSend(BackendResponse(status, "not JSON"), KeyType.LONG)
            }
            assertEquals(status, failure.status)
        }
    }

    @Test
    fun `outcomes must match their HTTP status`() {
        for (status in listOf(200, 202, 424)) {
            assertThrows(ClientException::class.java) {
                decodeSend(BackendResponse(status, messagingFixture(KeyType.LONG, "send-completed.json")), KeyType.LONG)
            }
        }
    }

    @Test
    fun `invalid backend state counters and recovery times are refused`() {
        val invalid = listOf(
            listOf("backends", "INDEX", "state") to JsonPrimitive("UNKNOWN"),
            listOf("backends", "INDEX", "attempts") to JsonPrimitive(-1),
            listOf("backends", "INDEX", "attempts") to JsonPrimitive("1"),
            listOf("backends", "INDEX", "attempts") to JsonPrimitive(1.5),
            listOf("backends", "INDEX", "nextRecoveryAt") to JsonPrimitive("not a time"),
            listOf("version") to JsonPrimitive(-1),
            listOf("version") to JsonPrimitive(1.5),
            listOf("version") to JsonPrimitive("1"),
            listOf("receipt", "messageKey", "key", "root") to null,
        )
        for ((path, value) in invalid) {
            val body = alteredFixture(KeyType.LONG, "status.json", path, value)
            assertThrows(ClientException::class.java) { decodeStatus(body, KeyType.LONG, "c-rest") }
        }
    }

    @Test
    fun `both status command identifiers must match the requested command`() {
        assertThrows(ClientException::class.java) {
            decodeStatus(messagingFixture(KeyType.LONG, "status.json"), KeyType.LONG, "another-command")
        }
        val body = alteredFixture(KeyType.LONG, "status.json", listOf("receipt", "commandId"), JsonPrimitive("another-command"))
        assertThrows(ClientException::class.java) { decodeStatus(body, KeyType.LONG, "c-rest") }
    }
}
