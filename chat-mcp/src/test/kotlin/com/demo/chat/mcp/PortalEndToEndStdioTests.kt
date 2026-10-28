package com.demo.chat.mcp

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.LongId
import com.demo.chat.mcp.tool.FakeTopicBackend
import com.demo.chat.mcp.tool.testConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** The tool under test. */
private const val TOOL: String = "chat_read_register"

/** The write tool of the end-to-end proof. */
private const val OPEN_TOOL: String = "chat_open_register_entry"

/** The reply of a complete empty read. */
private const val DEFAULT_REPLY: String = """{"status":0,"lines":[],"complete":true}"""

/**
 * Serve one canned reply per connection, in order. Record every frame.
 *
 * The production portal accepts one request per connection. This fake keeps
 * that shape, so the test sees the exact frame and the connection count.
 */
private class FakePortal(replies: List<String>) : AutoCloseable {
    private val directory: Path = Files.createTempDirectory("e2e-portal")
    private val queue: MutableList<String> = Collections.synchronizedList(replies.toMutableList())
    private val served = AtomicInteger(0)

    /** The socket path that the configuration points at. */
    val path: Path = directory.resolve("portal.sock")

    /** Every request frame that reached this portal, in order. */
    val received: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** The number of connections that this portal accepted. */
    val connections: Int get() = served.get()

    private val server: ServerSocketChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    private val thread: Thread

    init {
        server.bind(UnixDomainSocketAddress.of(path))
        thread =
            Thread {
                while (server.isOpen) {
                    val channel = runCatching { server.accept() }.getOrNull() ?: continue
                    runCatching {
                        channel.use {
                            served.incrementAndGet()
                            val input = Channels.newInputStream(it)
                            received.add(input.readBytes().toString(StandardCharsets.UTF_8))
                            val reply = queue.removeFirstOrNull() ?: DEFAULT_REPLY
                            val output = Channels.newOutputStream(it)
                            output.write(reply.toByteArray(StandardCharsets.UTF_8) + '\n'.code.toByte())
                            output.flush()
                        }
                    }
                }
            }
        thread.isDaemon = true
        thread.start()
    }

    override fun close() {
        runCatching { server.close() }
        thread.join(1_000)
        runCatching { Files.deleteIfExists(path) }
        runCatching { Files.deleteIfExists(directory) }
    }
}

/**
 * The complete portal path over the real stdio transport.
 *
 * A client discovers the tool, names a register, and reads lines in order.
 * Warnings and failures keep their stable shape. See CHAT-jzreskag.
 */
class PortalEndToEndStdioTests {
    private fun config(
        backend: FakeTopicBackend,
        portal: FakePortal,
    ): AdapterConfig =
        testConfig(
            origin = backend.origin,
            topicIds = listOf(LongId(11)),
            portalSocket = portal.path,
            registerName = "register-index",
        )

    private fun withPath(
        replies: List<String>,
        block: (StdioHarness, FakePortal) -> Unit,
    ) {
        FakeTopicBackend().use { backend ->
            FakePortal(replies).use { portal ->
                StdioHarness(config(backend, portal)).use { client ->
                    client.initialize()
                    block(client, portal)
                }
            }
        }
    }

    @Test
    fun `the register tool is discovered`() {
        withPath(listOf(DEFAULT_REPLY)) { client, _ ->
            val tools = client.listTools(2)["tools"]!!.jsonArray
            val names = tools.map { it.jsonObject["name"]!!.jsonPrimitiveText() }

            assertTrue(names.contains(TOOL), "the register tool is absent: $names")
        }
    }

    @Test
    fun `a typed index read keeps its lines and their order`() {
        val reply =
            """{"status":0,"lines":[""" +
                """{"type":"record","kind":"entry","name":"first"},""" +
                """{"type":"warning","kind":"duplicate-pointer","name":"second"}""" +
                """],"complete":true}"""

        withPath(listOf(reply)) { client, _ ->
            val answer = client.callTool(3, TOOL, "{}")

            assertFalse(client.isError(answer))
            val structured = client.structuredOf(answer)
            assertEquals(0, structured["status"]!!.jsonPrimitive.int)
            assertEquals("true", structured["complete"]!!.jsonPrimitiveText())
            assertEquals(
                listOf("record", "warning"),
                structured["lines"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitiveText() },
            )
        }
    }

    @Test
    fun `a topic read names the topic on the wire`() {
        withPath(listOf("""{"status":0,"lines":[{"type":"record","name":"only"}],"complete":true}""")) { client, portal ->
            val answer = client.callTool(3, TOOL, """{"topic":"CHAT-123"}""")

            assertFalse(client.isError(answer))
            assertEquals(1, client.structuredOf(answer)["lines"]!!.jsonArray.size)

            val request = Json.parseToJsonElement(portal.received.single()) as JsonObject
            assertEquals("read-register", request["operation"]!!.jsonPrimitiveText())
            assertEquals("register-index", request["index"]!!.jsonPrimitiveText())
            assertEquals("CHAT-123", request["topic"]!!.jsonPrimitiveText())
        }
    }

    @Test
    fun `a warning keeps the lines and marks the read incomplete`() {
        val reply =
            """{"status":0,"lines":[{"type":"warning","kind":"denied-topic"}],"complete":false}"""

        withPath(listOf(reply)) { client, _ ->
            val answer = client.callTool(3, TOOL, "{}")

            assertTrue(client.isError(answer))
            val structured = client.structuredOf(answer)
            assertEquals("false", structured["complete"]!!.jsonPrimitiveText())
            assertEquals(1, structured["lines"]!!.jsonArray.size)
        }
    }

    @Test
    fun `a failed read answers its error code`() {
        val reply =
            """{"status":124,"lines":[{"type":"record","name":"partial"}],"complete":false,"error":{"code":"TIMEOUT"}}"""

        withPath(listOf(reply)) { client, _ ->
            val answer = client.callTool(3, TOOL, "{}")

            assertTrue(client.isError(answer))
            val structured = client.structuredOf(answer)
            assertEquals(124, structured["status"]!!.jsonPrimitive.int)
            assertEquals("TIMEOUT", structured["error"]!!.jsonObject["code"]!!.jsonPrimitiveText())
            assertEquals("false", structured["complete"]!!.jsonPrimitiveText())
            assertTrue(structured["lines"]!!.jsonArray.isEmpty(), "a failed read kept partial lines")
        }
    }

    @Test
    fun `an open reaches the portal and returns its line`() {
        val reply =
            """{"status":0,"lines":[{"type":"open","state":"created","key":"CHAT-a",""" +
                """"topic":"register-index-fr-snap","pointer_id":"m1"}],"complete":true}"""

        withPath(listOf(reply)) { client, portal ->
            val answer = client.callTool(3, OPEN_TOOL, """{"key":"CHAT-a","short":"snap","title":"Snapshot"}""")

            assertFalse(client.isError(answer))
            val line = client.structuredOf(answer)["lines"]!!.jsonArray.single().jsonObject
            assertEquals("open", line["type"]!!.jsonPrimitiveText())
            assertEquals("created", line["state"]!!.jsonPrimitiveText())

            val request = Json.parseToJsonElement(portal.received.single()) as JsonObject
            assertEquals("open-register-entry", request["operation"]!!.jsonPrimitiveText())
            assertEquals("register-index", request["index"]!!.jsonPrimitiveText())
            assertEquals("CHAT-a", request["key"]!!.jsonPrimitiveText())
            assertEquals("snap", request["short"]!!.jsonPrimitiveText())
            assertEquals("Snapshot", request["title"]!!.jsonPrimitiveText())
        }
    }

    @Test
    fun `each call opens its own connection`() {
        withPath(
            listOf(
                """{"status":0,"lines":[],"complete":true}""",
                """{"status":0,"lines":[],"complete":true}""",
            ),
        ) { client, portal ->
            client.callTool(3, TOOL, "{}")
            client.callTool(4, TOOL, "{}")

            assertEquals(2, portal.connections, "the two calls shared a connection")
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveText(): String =
    (this as JsonPrimitive).content
