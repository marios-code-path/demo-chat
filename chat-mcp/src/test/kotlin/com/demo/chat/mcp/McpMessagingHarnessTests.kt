package com.demo.chat.mcp

import com.demo.chat.mcp.client.alteredFixture
import com.demo.chat.mcp.client.messagingFixture
import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.tool.MessagingTestBackend
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class McpMessagingHarnessTests {
    @TempDir lateinit var directory: Path

    @Test
    fun `invalid call scripts fail before an adapter starts`() {
        val harness = Path.of(System.getProperty("user.dir"), "src/test/client/harness.mjs")
        for ((index, payload) in listOf("null", "{}", "[{\"name\":\"tool\"}]", "[{\"name\":\"tool\",\"arguments\":[]}]").withIndex()) {
            val file = directory.resolve("invalid-$index.json")
            val errors = directory.resolve("invalid-$index.log")
            Files.writeString(file, payload)
            val process = ProcessBuilder("node", harness.toString(), "--calls-file", file.toString(), "--", "/program-that-must-not-start")
                .redirectErrorStream(true).redirectOutput(errors.toFile()).start()
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                assertEquals(1, process.exitValue())
                assertTrue(Files.readString(errors).contains("the call script must contain tool names and argument objects"))
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
    }

    private fun script(vararg calls: Pair<String, JsonObject>): JsonArray = JsonArray(calls.map { (name, arguments) ->
        buildJsonObject { put("name", name); put("arguments", arguments) }
    })

    private fun arguments(vararg fields: Pair<String, String>): JsonObject = buildJsonObject {
        fields.forEach { (name, value) -> put(name, value) }
    }

    private fun sendArguments() = arguments("topicId" to "12345", "text" to "hello λ", "requestId" to "request:1")

    private fun runHarness(
        backend: MessagingTestBackend,
        calls: JsonArray,
        send: Boolean = true,
        kind: KeyType = KeyType.LONG,
        rooms: String = "12345",
    ): JsonObject {
        val run = Files.createTempDirectory(directory, "run-")
        val token = run.resolve("credential.txt")
        Files.writeString(token, "test-token")
        val config = run.resolve("adapter.properties")
        Files.writeString(config, "backendBaseUrl=${backend.origin}\ncredentialFile=$token\n" +
            "keyType=${kind.name.lowercase()}\ntopicIds=$rooms\nenableSend=$send\n")
        val callFile = run.resolve("calls.json")
        Files.writeString(callFile, calls.toString())
        val harness = Path.of(System.getProperty("user.dir"), "src/test/client/harness.mjs")
        assertTrue(Files.isRegularFile(harness))
        val classpath = System.getProperty("surefire.test.class.path") ?: System.getProperty("java.class.path")
        val output = run.resolve("output.json")
        val errors = run.resolve("errors.txt")
        val process = ProcessBuilder(
            "node", harness.toString(), "--calls-file", callFile.toString(), "--",
            Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", classpath,
            "com.demo.chat.mcp.McpAdapterMainKt", "--config", config.toString(),
        ).redirectOutput(output.toFile()).redirectError(errors.toFile()).start()
        try {
            assertTrue(process.waitFor(10L + 35L * calls.size, TimeUnit.SECONDS), "the messaging harness exceeded its bound")
            assertEquals(0, process.exitValue(), Files.readString(errors))
            val transcript = Json.parseToJsonElement(Files.readString(output)).jsonObject
            assertEquals(JsonNull, transcript.getValue("connectError"))
            assertTrue(transcript.getValue("stdoutParseFailures").jsonArray.isEmpty())
            for (line in transcript.getValue("stdoutLines").jsonArray) {
                assertEquals("2.0", Json.parseToJsonElement(line.jsonPrimitive.content).jsonObject.getValue("jsonrpc").jsonPrimitive.content)
            }
            val exit = transcript.getValue("exit").jsonObject
            assertEquals(JsonPrimitive(true), exit.getValue("withinBound"))
            assertEquals(JsonPrimitive(0), exit.getValue("code"))
            return transcript
        } finally {
            if (process.isAlive) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                process.waitFor(5, TimeUnit.SECONDS)
            }
            Files.deleteIfExists(token)
        }
    }

    private fun results(transcript: JsonObject): List<JsonObject> = transcript.getValue("calls").jsonArray.map { it.jsonObject }
    private fun code(result: JsonObject): String = result.getValue("meta").jsonObject.getValue("code").jsonPrimitive.content

    @Test
    fun `the pinned client discovers five read tools or six tools with sending`() {
        MessagingTestBackend().use { backend ->
            val disabled = runHarness(backend, script(), send = false)
            val enabled = runHarness(backend, script())
            assertEquals(5, disabled.getValue("tools").jsonArray.size)
            assertFalse(disabled.getValue("tools").jsonArray.any { it.jsonObject.getValue("name").jsonPrimitive.content == "chat_send_message" })
            assertEquals(6, enabled.getValue("tools").jsonArray.size)
        }
    }

    @Test
    fun `the pinned client reads and sends exact Long and UUID identifiers`() {
        for (kind in KeyType.entries) MessagingTestBackend().use { backend ->
            val room = if (kind == KeyType.LONG) "12345" else "00000000-0000-4000-8000-000000003039"
            val id = if (kind == KeyType.LONG) "1554361326074068992" else "6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b"
            backend.answer("/message/list/$room", 200, messagingFixture(kind, "history.ndjson"))
            backend.answer("/message/id/$id", 200, messagingFixture(kind, "message.json"))
            backend.answer("/message/submit/$room", 201, messagingFixture(kind, "send-completed.json"))
            backend.answer("/message/command/c-rest", 200, messagingFixture(kind, "status.json"))
            val transcript = runHarness(backend, script(
                "chat_list_messages" to arguments("topicId" to room),
                "chat_get_message" to arguments("messageId" to id),
                "chat_send_message" to arguments("topicId" to room, "text" to "hello λ", "requestId" to "request:1"),
                "chat_get_command_status" to arguments("commandId" to "c-rest"),
            ), kind = kind, rooms = room)
            val results = results(transcript)
            assertEquals(4, results.size)
            for (result in results) {
                assertEquals(JsonPrimitive(false), result.getValue("isError"))
                assertEquals(JsonNull, result.getValue("meta"))
                assertEquals(result.getValue("structuredContent"), Json.parseToJsonElement(result.getValue("text").jsonPrimitive.content))
            }
            val returned = results[1].getValue("structuredContent").jsonObject.getValue("message").jsonObject
            assertEquals(JsonPrimitive(id), returned.getValue("messageKey").jsonObject.getValue("id"))
            assertEquals("request:1", backend.requests.single { it.method == "POST" }.requestId)
        }
    }

    @Test
    fun `the pinned client handles pending accepted and incomplete results`() {
        MessagingTestBackend().use { backend ->
            for (outcome in listOf("pending", "accepted", "incomplete")) {
                backend.answer("/message/submit/12345", if (outcome == "incomplete") 424 else 202,
                    messagingFixture(KeyType.LONG, "send-$outcome.json"))
                val result = results(runHarness(backend, script("chat_send_message" to sendArguments()))).single()
                assertEquals(outcome.uppercase(), result.getValue("structuredContent").jsonObject.getValue("outcome").jsonPrimitive.content)
                if (outcome == "incomplete") {
                    assertEquals(JsonPrimitive(true), result.getValue("isError"))
                    assertEquals("COMMAND_INCOMPLETE", code(result))
                    assertEquals(setOf("code", "message", "retryable"), result.getValue("meta").jsonObject.keys)
                } else {
                    assertEquals(JsonPrimitive(false), result.getValue("isError"))
                    assertEquals(JsonNull, result.getValue("meta"))
                    if (outcome == "accepted") assertTrue(result.getValue("structuredContent").jsonObject.getValue("backends").jsonObject.isEmpty())
                }
            }
        }
    }

    @Test
    fun `lost and redirected submissions return unknown without resending`() {
        for (redirect in listOf(false, true)) {
            MessagingTestBackend { exchange ->
                if (redirect) {
                    exchange.responseHeaders.add("Location", "/redirect-target")
                    exchange.sendResponseHeaders(307, -1)
                }
                exchange.close()
                true
            }.use { backend ->
                val transcript = runHarness(backend, script("chat_send_message" to sendArguments()))
                val result = results(transcript).single()
                assertEquals("OUTCOME_UNKNOWN", code(result))
                val meta = result.getValue("meta").jsonObject
                assertEquals(setOf("code", "message", "retryable", "requestId"), meta.keys)
                assertEquals(JsonPrimitive("request:1"), meta.getValue("requestId"))
                assertEquals(JsonPrimitive(false), meta.getValue("retryable"))
                assertEquals(JsonNull, result.getValue("structuredContent"))
                assertEquals(1, backend.requests.size)
                assertFalse(transcript.getValue("stderrLines").toString().contains("request:1"))
            }
        }
    }

    @Test
    fun `validation preserves the legacy error and prevents new backend calls`() {
        MessagingTestBackend().use { backend ->
            val transcript = runHarness(backend, script(
                "chat_get_topic" to buildJsonObject { put("topicId", JsonNull) },
                "chat_list_messages" to buildJsonObject { put("topicId", 7) },
                "chat_send_message" to arguments("topicId" to "12345", "text" to " ", "requestId" to "request:1"),
                "chat_get_command_status" to arguments("commandId" to "has space"),
            ))
            assertEquals(listOf("NOT_AVAILABLE", "INVALID_INPUT", "INVALID_INPUT", "INVALID_INPUT"), results(transcript).map(::code))
            assertTrue(backend.requests.isEmpty())
        }
    }

    @Test
    fun `scope limits and backend errors expose no sensitive response values`() {
        MessagingTestBackend().use { backend ->
            val hidden = alteredFixture(KeyType.LONG, "message.json", listOf("message", "key", "key", "dest"), JsonPrimitive(9007199254740991L))
            backend.answer("/message/id/77", 200, hidden)
            backend.answer("/message/list/12345", 200,
                messagingFixture(KeyType.LONG, "message.json").replace("\n", "") + "\n" + "x".repeat(1048577))
            backend.answer("/message/command/c-rest", 500, "private backend URL and credential")
            val transcript = runHarness(backend, script(
                "chat_get_message" to arguments("messageId" to "77"),
                "chat_list_messages" to arguments("topicId" to "12345"),
                "chat_get_command_status" to arguments("commandId" to "c-rest"),
            ))
            assertEquals(listOf("NOT_AVAILABLE", "LIMIT_EXCEEDED", "BACKEND_UNAVAILABLE"), results(transcript).map(::code))
            val visible = results(transcript).toString() + transcript.getValue("stderrLines").toString()
            for (secret in listOf("hello λ", "1554361326074068992", "9007199254740991", "private backend", "test-token")) {
                assertFalse(visible.contains(secret), "a response exposed a sensitive value")
            }
            assertTrue(results(transcript).all { it.getValue("structuredContent") == JsonNull })
        }
    }

    @Test
    fun `a confirmed request conflict remains nonretryable`() {
        MessagingTestBackend().use { backend ->
            backend.answer("/message/submit/12345", 409, "private conflict")
            val result = results(runHarness(backend, script("chat_send_message" to sendArguments()))).single()
            assertEquals("REQUEST_CONFLICT", code(result))
            assertEquals(JsonPrimitive(false), result.getValue("meta").jsonObject.getValue("retryable"))
            assertEquals(JsonPrimitive("the request ID conflicts with an earlier submission"), result.getValue("meta").jsonObject.getValue("message"))
        }
    }
}
