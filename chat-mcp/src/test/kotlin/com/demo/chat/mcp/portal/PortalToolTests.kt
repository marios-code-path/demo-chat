package com.demo.chat.mcp.portal

import com.demo.chat.mcp.StdioHarness
import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.LongId
import com.demo.chat.mcp.tool.FakeTopicBackend
import com.demo.chat.mcp.tool.testConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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

/** The name of the tool under test. */
private const val TOOL: String = "chat_read_register"

/** The fields of the tool answer. */
private const val STATUS: String = "status"
private const val LINES: String = "lines"
private const val COMPLETE: String = "complete"
private const val ERROR: String = "error"

/**
 * Serve one canned reply on each connection. Record every request frame.
 *
 * The production portal answers one request per connection. This fake keeps
 * that shape, so a test sees the exact frame that the tool wrote.
 */
class FakePortal(private val reply: String) : AutoCloseable {
    private val directory: Path = Files.createTempDirectory("fake-portal")

    /** The socket path that the configuration points at. */
    val path: Path = directory.resolve("portal.sock")

    /** Every request frame that reached this portal, in order. */
    val received: MutableList<String> = Collections.synchronizedList(mutableListOf())

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
                            val input = Channels.newInputStream(it)
                            received.add(input.readBytes().toString(StandardCharsets.UTF_8))
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
 * The portal read tool over the real stdio transport.
 *
 * The tool is registered only for a complete portal setting. It carries no
 * selector and no identity, and the register line order survives the call.
 * See CHAT-tcgpzewg.
 */
class PortalToolTests {
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

    private fun tools(client: StdioHarness): Map<String, JsonObject> =
        client.listTools(2)["tools"]!!.jsonArray.associate { element ->
            val tool = element.jsonObject
            tool["name"]!!.jsonPrimitiveText() to tool
        }

    private fun withPortal(
        reply: String,
        block: (StdioHarness, FakePortal) -> Unit,
    ) {
        FakeTopicBackend().use { backend ->
            FakePortal(reply).use { portal ->
                StdioHarness(config(backend, portal)).use { client ->
                    client.initialize()
                    block(client, portal)
                }
            }
        }
    }

    @Test
    fun `the portal tool appears with a complete portal setting`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, _ ->
            assertTrue(tools(client).containsKey(TOOL), "the portal tool is absent")
        }
    }

    @Test
    fun `the portal tool is absent without a portal setting`() {
        FakeTopicBackend().use { backend ->
            StdioHarness(testConfig(backend.origin, listOf(LongId(11)))).use { client ->
                client.initialize()
                assertFalse(tools(client).containsKey(TOOL), "the portal tool is present without a portal")
            }
        }
    }

    @Test
    fun `the tool declares one optional string topic and read only hints`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, _ ->
            val tool = tools(client).getValue(TOOL)

            assertEquals(
                """{"properties":{"topic":{"type":"string"}},"required":[],"type":"object"}""",
                tool["inputSchema"].toString(),
            )
            val annotations = tool["annotations"]!!.jsonObject
            assertEquals("true", annotations["readOnlyHint"]!!.jsonPrimitiveText())
            assertEquals("true", annotations["idempotentHint"]!!.jsonPrimitiveText())
        }
    }

    @Test
    fun `a read answers status lines and complete`() {
        withPortal("""{"status":0,"lines":[{"name":"first"},{"name":"second"}],"complete":true}""") { client, portal ->
            val reply = client.callTool(3, TOOL, """{"topic":"CHAT-123"}""")

            assertFalse(client.isError(reply))
            val structured = client.structuredOf(reply)
            assertEquals(0, structured[STATUS]!!.jsonPrimitive.int)
            assertEquals("true", structured[COMPLETE]!!.jsonPrimitiveText())
            assertEquals(
                listOf("first", "second"),
                structured[LINES]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitiveText() },
            )
            assertEquals(structured.toString(), client.textOf(reply))

            val request = Json.parseToJsonElement(portal.received.single()) as JsonObject
            assertEquals("register-index", request["index"]!!.jsonPrimitiveText())
            assertEquals("CHAT-123", request["topic"]!!.jsonPrimitiveText())
        }
    }

    @Test
    fun `a read without a topic sends no topic field`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, portal ->
            client.callTool(3, TOOL, "{}")

            val request = Json.parseToJsonElement(portal.received.single()) as JsonObject
            assertFalse(request.containsKey("topic"), "the request carried a topic: ${portal.received.single()}")
        }
    }

    @Test
    fun `an incomplete read keeps its lines and sets isError`() {
        withPortal("""{"status":0,"lines":[{"name":"first"}],"complete":false}""") { client, _ ->
            val reply = client.callTool(3, TOOL, "{}")

            assertTrue(client.isError(reply))
            val structured = client.structuredOf(reply)
            assertEquals("false", structured[COMPLETE]!!.jsonPrimitiveText())
            assertEquals(1, structured[LINES]!!.jsonArray.size)
        }
    }

    @Test
    fun `a busy portal answers its code`() {
        withPortal("""{"status":null,"lines":[],"complete":false,"error":{"code":"BUSY"}}""") { client, _ ->
            val reply = client.callTool(3, TOOL, "{}")

            assertTrue(client.isError(reply))
            val structured = client.structuredOf(reply)
            assertEquals("BUSY", structured[ERROR]!!.jsonObject["code"]!!.jsonPrimitiveText())
            assertTrue(structured[STATUS] is JsonNull, "a busy read carries a status")
            assertEquals("false", structured[COMPLETE]!!.jsonPrimitiveText())
        }
    }

    @Test
    fun `a failed read keeps its status and drops partial lines`() {
        withPortal("""{"status":124,"lines":[{"name":"ignored"}],"complete":false,"error":{"code":"TIMEOUT"}}""") { client, _ ->
            val reply = client.callTool(3, TOOL, "{}")

            assertTrue(client.isError(reply))
            val structured = client.structuredOf(reply)
            assertEquals(124, structured[STATUS]!!.jsonPrimitive.int)
            assertEquals("TIMEOUT", structured[ERROR]!!.jsonObject["code"]!!.jsonPrimitiveText())
            assertEquals("false", structured[COMPLETE]!!.jsonPrimitiveText())
            assertTrue(structured[LINES]!!.jsonArray.isEmpty(), "a failed read kept partial lines")
        }
    }

    @Test
    fun `an empty topic is refused`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, portal ->
            val reply = client.callTool(3, TOOL, """{"topic":""}""")

            assertTrue(client.isError(reply))
            assertTrue(client.textOf(reply).contains("is empty"))
            assertTrue(portal.received.isEmpty(), "a refused topic reached the portal")
        }
    }

    @Test
    fun `a non string topic is refused`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, portal ->
            val reply = client.callTool(3, TOOL, """{"topic":5}""")

            assertTrue(client.isError(reply))
            assertTrue(client.textOf(reply).contains("not a JSON string"))
            assertTrue(portal.received.isEmpty(), "a refused topic reached the portal")
        }
    }

    @Test
    fun `an invalid topic name is refused`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, portal ->
            val reply = client.callTool(3, TOOL, """{"topic":"-topic"}""")

            assertTrue(client.isError(reply))
            assertTrue(client.textOf(reply).contains("must match"))
            assertTrue(portal.received.isEmpty(), "a refused topic reached the portal")
        }
    }

    @Test
    fun `an unknown argument is refused`() {
        withPortal("""{"status":0,"lines":[],"complete":true}""") { client, portal ->
            val reply = client.callTool(3, TOOL, """{"index":"register-index"}""")

            assertTrue(client.isError(reply))
            assertTrue(client.textOf(reply).contains("no argument named 'index'"))
            assertTrue(portal.received.isEmpty(), "a refused argument reached the portal")
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveText(): String =
    (this as JsonPrimitive).content
