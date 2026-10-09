package com.demo.chat.mcp.tool

import com.demo.chat.mcp.StdioHarness
import com.demo.chat.mcp.client.messagingFixture
import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.config.LongId
import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import com.demo.chat.mcp.client.SubmissionUnknownException
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MessagingAnswerContractTests {
    private fun request(id: String = "trusted-request") = CallToolRequest(CallToolRequestParams(
        name = "chat_send_message",
        arguments = buildJsonObject { put("requestId", id) },
    ))

    @Test
    fun `unknown metadata uses the validated caller ID and diagnostics omit it`() = runBlocking {
        val saved = System.err
        val captured = java.io.ByteArrayOutputStream()
        val result = try {
            System.setErr(java.io.PrintStream(captured))
            messagingAnswer("chat_send_message", request(), setOf("requestId"), true) {
                throw SubmissionUnknownException("untrusted-exception-id", 201)
            }
        } finally {
            System.setErr(saved)
        }
        assertEquals(JsonPrimitive("trusted-request"), result.meta!!.getValue("requestId"))
        assertFalse(result.meta.toString().contains("untrusted-exception-id"))
        val lines = captured.toString(Charsets.UTF_8).lines().filter(String::isNotBlank)
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("code=OUTCOME_UNKNOWN status=201"))
        assertFalse(lines.single().contains("trusted-request"))
        assertFalse(lines.single().contains("untrusted-exception-id"))
    }

    @Test
    fun `a local submission limit carries no unknown identity`() = runBlocking {
        val result = messagingAnswer("chat_send_message", request(), setOf("requestId"), true) {
            throw ClientException("private failure", FailureReason.LIMIT)
        }
        assertEquals(setOf("code", "message", "retryable"), result.meta!!.keys)
        assertEquals(JsonPrimitive("LIMIT_EXCEEDED"), result.meta!!.getValue("code"))
        assertEquals(JsonPrimitive(false), result.meta!!.getValue("retryable"))
        assertNull(result.structuredContent)
    }

    @Test
    fun `cancellation propagates from a messaging call`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                messagingAnswer("chat_get_message", request(), setOf("requestId"), false) {
                    throw CancellationException("private cancellation")
                }
            }
        }
    }

    private fun withClient(body: (MessagingTestBackend, StdioHarness) -> Unit) {
        MessagingTestBackend().use { backend ->
            val config = testConfig(backend.origin, listOf(LongId(12345))).copy(enableSend = true)
            try {
                StdioHarness(config).use { client ->
                    client.initialize()
                    body(backend, client)
                }
            } finally {
                java.nio.file.Files.deleteIfExists(config.credentialFile)
            }
        }
    }

    private val sendArguments = """{"topicId":"12345","text":"hello λ","requestId":"request:1"}"""

    private fun code(answer: JsonObject): String = answer.getValue("_meta").jsonObject.getValue("code").jsonPrimitive.content

    private fun assertError(answer: JsonObject, expected: String, fields: Set<String> = setOf("code", "message", "retryable")) {
        assertEquals(JsonPrimitive(true), answer["isError"])
        assertEquals(expected, code(answer))
        val meta = answer.getValue("_meta").jsonObject
        assertEquals(fields, meta.keys)
        assertEquals(JsonPrimitive(false), meta["retryable"])
        assertEquals(meta.getValue("message"), answer.getValue("content").jsonArray.single().jsonObject["text"])
    }

    @Test
    fun `all four messaging tools return matching structured and text results`() = withClient { backend, client ->
        backend.answer("/message/list/12345", 200, messagingFixture(KeyType.LONG, "history.ndjson"))
        backend.answer("/message/id/1554361326074068992", 200, messagingFixture(KeyType.LONG, "message.json"))
        backend.answer("/message/submit/12345", 201, messagingFixture(KeyType.LONG, "send-completed.json"))
        backend.answer("/message/command/c-rest", 200, messagingFixture(KeyType.LONG, "status.json"))
        val calls = listOf(
            "chat_list_messages" to """{"topicId":"12345"}""",
            "chat_get_message" to """{"messageId":"1554361326074068992"}""",
            "chat_send_message" to sendArguments,
            "chat_get_command_status" to """{"commandId":"c-rest"}""",
        )
        for ((index, step) in calls.withIndex()) {
            val answer = client.callTool(index + 2, step.first, step.second)
            assertFalse(client.isError(answer))
            assertNull(answer["_meta"])
            assertEquals(client.structuredOf(answer), Json.parseToJsonElement(client.textOf(answer)))
        }
    }

    @Test
    fun `new argument refusals keep three metadata fields and no payload`() = withClient { backend, client ->
        for ((index, arguments) in listOf("{}", """{"topicId":7}""", """{"topicId":null}""",
            """{"topicId":"12345","extra":"private"}""").withIndex()) {
            val answer = client.callTool(index + 2, "chat_list_messages", arguments)
            assertError(answer, "INVALID_INPUT")
            assertNull(answer["structuredContent"])
        }
        val legacy = client.callTool(7, "chat_get_topic", """{"topicId":null}""")
        assertEquals("NOT_AVAILABLE", code(legacy))
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `confirmed send refusals never expose backend text`() = withClient { backend, client ->
        for ((index, pair) in listOf(400 to "INVALID_INPUT", 409 to "REQUEST_CONFLICT",
            401 to "AUTHENTICATION_REQUIRED", 403 to "NOT_AVAILABLE", 404 to "NOT_AVAILABLE").withIndex()) {
            backend.answer("/message/submit/12345", pair.first, "private backend text")
            val answer = client.callTool(index + 2, "chat_send_message", sendArguments)
            assertError(answer, pair.second)
            assertNull(answer["structuredContent"])
            assertFalse(answer.toString().contains("private"))
        }
    }

    @Test
    fun `an incomplete send retains its validated command result`() = withClient { backend, client ->
        backend.answer("/message/submit/12345", 424, messagingFixture(KeyType.LONG, "send-incomplete.json"))
        val answer = client.callTool(2, "chat_send_message", sendArguments)
        assertError(answer, "COMMAND_INCOMPLETE")
        assertEquals("INCOMPLETE", client.structuredOf(answer).getValue("outcome").jsonPrimitive.content)
        assertFalse(answer.toString().contains("private"))
    }

    @Test
    fun `unknown sends expose only the validated request identity`() = withClient { backend, client ->
        backend.answer("/message/submit/12345", 201, "private malformed response")
        val answer = client.callTool(2, "chat_send_message", sendArguments)
        assertError(answer, "OUTCOME_UNKNOWN", setOf("code", "message", "retryable", "requestId"))
        assertEquals(JsonPrimitive("request:1"), answer.getValue("_meta").jsonObject["requestId"])
        assertNull(answer["structuredContent"])
        assertFalse(answer.toString().contains("private"))
        assertFalse(answer.toString().contains("receipt"))
    }

    @Test
    fun `pending and accepted sends remain successful results`() = withClient { backend, client ->
        for ((index, outcome) in listOf("pending", "accepted").withIndex()) {
            backend.answer("/message/submit/12345", 202, messagingFixture(KeyType.LONG, "send-$outcome.json"))
            val answer = client.callTool(index + 2, "chat_send_message", sendArguments)
            assertFalse(client.isError(answer))
            assertEquals(outcome.uppercase(), client.structuredOf(answer).getValue("outcome").jsonPrimitive.content)
            if (outcome == "accepted") assertTrue(client.structuredOf(answer).getValue("backends").jsonObject.isEmpty())
        }
    }
}
