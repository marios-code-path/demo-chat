package com.demo.chat.mcp

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.LongId
import com.demo.chat.mcp.tool.FakeTopicBackend
import com.demo.chat.mcp.tool.testConfig
import com.demo.chat.mcp.tool.topicBody
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two acceptance criteria of Task 4.
 *
 * The tools answer over stdio. A denied topic is absent from the list, and its
 * name appears in no response and in no stderr line.
 */
class McpAdapterToolStdioTests {
    private val hiddenName: String = "refused-topic-name"

    private fun config(backend: FakeTopicBackend, ids: List<LongId>): AdapterConfig =
        testConfig(origin = backend.origin, topicIds = ids)

    @Test
    fun `both tools answer over stdio`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()

                val list = client.callTool(2, "chat_list_topics", "{}")
                assertFalse(client.isError(list))
                val topics = client.structuredOf(list)["topics"]!!.jsonArray
                assertEquals(1, topics.size)
                assertEquals("alpha", topics[0].jsonObject["name"]!!.jsonPrimitiveText())

                val read = client.callTool(3, "chat_get_topic", """{"topicId":"11"}""")
                assertFalse(client.isError(read))
                val topic = client.structuredOf(read)["topic"]!!.jsonObject
                assertEquals("11", topic["id"]!!.jsonPrimitiveText())
                assertEquals("alpha", topic["name"]!!.jsonPrimitiveText())
            }
        }
    }

    /**
     * Every id is a JSON string, including a Long id above 2^53.
     */
    @Test
    fun `both tools answer every id as a JSON string`() {
        val id = 1554361143634427905L

        FakeTopicBackend().use { backend ->
            backend.answer("$id", 200, topicBody(id, 7, "alpha"))
            val config = config(backend, listOf(LongId(id)))

            StdioHarness(config).use { client ->
                client.initialize()
                val list = client.callTool(2, "chat_list_topics", "{}")
                val text = client.textOf(list)

                assertTrue(text.contains("\"id\":\"1554361143634427905\""), "the id is not exact string text: $text")
                assertTrue(text.contains("\"root\":\"7\""), "the root is not string text: $text")
            }
        }
    }

    @Test
    fun `the tool list declares both tools`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val tools = client.listTools(2)["tools"]!!.jsonArray
                val names = tools.map { it.jsonObject["name"]!!.jsonPrimitiveText() }

                assertEquals(listOf("chat_list_topics", "chat_get_topic", "chat_list_messages",
                    "chat_get_message", "chat_get_command_status"), names)
            }
        }
    }

    /**
     * The wire schema of each tool.
     *
     * The SDK writes exactly `$schema`, `properties`, `required`, `$defs` and
     * `type`. It cannot carry `additionalProperties` at a tool schema root, so
     * the adapter enforces "no unknown argument" in code. See decision 11 in
     * the plan. The nested topic schema does carry it, and this pins that.
     */
    @Test
    fun `the wire schema of each tool is exact`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val tools =
                    client.listTools(2)["tools"]!!.jsonArray.associateBy {
                        it.jsonObject["name"]!!.jsonPrimitiveText()
                    }

                assertEquals(
                    """{"properties":{},"required":[],"type":"object"}""",
                    tools.getValue("chat_list_topics").jsonObject["inputSchema"].toString(),
                )
                assertEquals(
                    """{"properties":{"topicId":{"type":"string"}},"required":["topicId"],"type":"object"}""",
                    tools.getValue("chat_get_topic").jsonObject["inputSchema"].toString(),
                )

                val topic = """
                    {"type":"object","properties":{"id":{"type":"string"},"root":{"type":"string"},"name":{"type":"string"}},"required":["id","root","name"],"additionalProperties":false}
                """.trimIndent()
                assertEquals(
                    """{"properties":{"topics":{"type":"array","items":$topic}},"required":["topics"],"type":"object"}""",
                    tools.getValue("chat_list_topics").jsonObject["outputSchema"].toString(),
                )
                assertEquals(
                    """{"properties":{"topic":$topic},"required":["topic"],"type":"object"}""",
                    tools.getValue("chat_get_topic").jsonObject["outputSchema"].toString(),
                )

                val annotations = tools.getValue("chat_list_topics").jsonObject["annotations"]!!.jsonObject
                assertEquals("true", annotations["readOnlyHint"]!!.jsonPrimitiveText())
                assertEquals("true", annotations["idempotentHint"]!!.jsonPrimitiveText())
            }
        }
    }

    /**
     * A denied topic leaves no trace. The backend answers the denial with a
     * body that carries the name, so an echoed payload would show it.
     */
    @Test
    fun `a denied topic is absent from the list and from stderr`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 403, topicBody(22, 7, hiddenName))
            val config = config(backend, listOf(LongId(11), LongId(22)))

            val captured = ByteArrayOutputStream()
            val original = System.err
            try {
                System.setErr(PrintStream(captured, true, "UTF-8"))
                StdioHarness(config).use { client ->
                    client.initialize()
                    val list = client.callTool(2, "chat_list_topics", "{}")
                    val topics = client.structuredOf(list)["topics"]!!.jsonArray

                    assertEquals(1, topics.size)
                    assertEquals("alpha", topics[0].jsonObject["name"]!!.jsonPrimitiveText())
                    assertFalse(client.textOf(list).contains(hiddenName))

                    val read = client.callTool(3, "chat_get_topic", """{"topicId":"22"}""")
                    assertTrue(client.isError(read))
                    assertFalse(client.textOf(read).contains(hiddenName))

                    assertTrue(
                        client.rawFrames.none { it.contains(hiddenName) },
                        "a response carried the denied topic name",
                    )
                }
            } finally {
                System.setErr(original)
            }

            val stderr = captured.toString("UTF-8")
            assertFalse(stderr.contains(hiddenName), "stderr carried the denied topic name: $stderr")
            assertFalse(stderr.contains("test-credential"), "stderr carried the credential: $stderr")
        }
    }

    @Test
    fun `the single read refuses a topic outside the configured list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("99", 200, topicBody(99, 7, "hidden"))
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val read = client.callTool(2, "chat_get_topic", """{"topicId":"99"}""")

                assertTrue(client.isError(read))
                assertTrue(client.textOf(read).contains("not one of the configured topic ids"))
                assertTrue(backend.requestedIds.isEmpty(), "the adapter called the backend for a refused id")
            }
        }
    }

    @Test
    fun `the list tool refuses an unknown argument`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val list = client.callTool(2, "chat_list_topics", """{"limit":5}""")

                assertTrue(client.isError(list))
                assertTrue(client.textOf(list).contains("no argument named 'limit'"))
                assertTrue(backend.requestedIds.isEmpty())
            }
        }
    }

    @Test
    fun `the single read refuses a missing argument`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val read = client.callTool(2, "chat_get_topic", "{}")

                assertTrue(client.isError(read))
                assertTrue(client.textOf(read).contains("requires an argument named 'topicId'"))
                assertTrue(backend.requestedIds.isEmpty())
            }
        }
    }

    /**
     * The schema declares every id as a string. A JSON number is refused at the
     * boundary, so no Double ever holds an id.
     */
    @Test
    fun `the single read refuses a JSON number id`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val read = client.callTool(2, "chat_get_topic", """{"topicId":11}""")

                assertTrue(client.isError(read))
                assertTrue(client.textOf(read).contains("is not a JSON string"))
                assertTrue(backend.requestedIds.isEmpty())
            }
        }
    }

    /**
     * A backend failure answers an error, and the answer carries no backend text.
     *
     * The backend answers 500 with a body. Neither the status, the body, nor the
     * sentence of the transport exception may reach the client. Task 7 rule 4
     * requires this. The status and the body are the two strings a pass-through
     * would publish, so this test names both.
     */
    @Test
    fun `a backend failure answers an error`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 500, """{"error":"broken"}""")
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val list = client.callTool(2, "chat_list_topics", "{}")

                assertTrue(client.isError(list))
                assertEquals("the backend did not answer the call", client.textOf(list))
                assertFalse(client.textOf(list).contains("500"), "the answer names the backend status")
                assertFalse(client.textOf(list).contains("broken"), "the answer repeats the backend body")
                assertFalse(
                    client.metaOf(list).toString().contains("500"),
                    "the application data names the backend status",
                )
            }
        }
    }

    /**
     * Task 7 rules 2 and 3. A failure carries `code`, `message` and
     * `retryable` on the wire, under the name the MCP wire uses.
     */
    @Test
    fun `a failed call carries the three application fields`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 500, """{"error":"broken"}""")
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val list = client.callTool(2, "chat_list_topics", "{}")

                val meta = client.metaOf(list)
                assertEquals(setOf("code", "message", "retryable"), meta.keys)
                assertEquals("BACKEND_UNAVAILABLE", meta["code"]!!.jsonPrimitiveText())
                assertEquals("false", meta["retryable"]!!.jsonPrimitiveText())
            }
        }
    }

    /** A good answer carries no application error data. The SDK omits the field. */
    @Test
    fun `a good call carries no application error data`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val list = client.callTool(2, "chat_list_topics", "{}")

                assertFalse(client.hasMeta(list), "a successful answer carried application error data")
            }
        }
    }

    /**
     * Task 7 rule 4. A denied object and an absent object answer one sentence.
     *
     * The two backend statuses differ, and the answer must not separate them.
     */
    @Test
    fun `a denied topic and an absent topic answer the same sentence`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 403, topicBody(11, 7, "refused"))
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val denied = client.callTool(2, "chat_get_topic", """{"topicId":"11"}""")

                assertEquals("NOT_AVAILABLE", client.metaOf(denied)["code"]!!.jsonPrimitiveText())
                assertFalse(client.textOf(denied).contains("403"), "the answer named the backend status")
            }
        }
    }

    @Test
    fun `an absent topic answers the same sentence as a denied one`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                val absent = client.callTool(2, "chat_get_topic", """{"topicId":"11"}""")

                assertEquals("NOT_AVAILABLE", client.metaOf(absent)["code"]!!.jsonPrimitiveText())
                assertFalse(client.textOf(absent).contains("404"), "the answer named the backend status")
                assertTrue(client.textOf(absent).contains("does not serve this object"))
            }
        }
    }

    /**
     * Task 7 rule 1. A malformed envelope answers an SDK protocol error.
     *
     * The error is a JSON-RPC error and not a tool result. Stdout still carries
     * protocol frames alone, because every frame parses as JSON.
     */
    @Test
    fun `a malformed envelope answers a protocol error`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                client.send("""{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"arguments":{}}}""")

                val message = client.awaitMessage(9)
                assertNotNull(message["error"], "the malformed envelope answered no protocol error: $message")
                assertNull(message["result"], "the malformed envelope answered a result: $message")
                assertTrue(backend.requestedIds.isEmpty(), "the adapter called the backend for a malformed request")
            }
        }
    }

    /** No stdout line is a non frame, whatever the request. */
    @Test
    fun `stdout carries protocol frames alone after a malformed envelope`() {
        FakeTopicBackend().use { backend ->
            val config = config(backend, listOf(LongId(11)))

            StdioHarness(config).use { client ->
                client.initialize()
                client.send("""{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"arguments":{}}}""")
                client.awaitMessage(9)

                assertTrue(client.rawFrames.isNotEmpty(), "the adapter wrote no frame")
                client.rawFrames.forEach { line ->
                    val frame = Json.parseToJsonElement(line).jsonObject
                    assertEquals("2.0", frame["jsonrpc"]!!.jsonPrimitiveText(), "a stdout line is not a frame: $line")
                }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveText(): String =
    (this as JsonPrimitive).content
