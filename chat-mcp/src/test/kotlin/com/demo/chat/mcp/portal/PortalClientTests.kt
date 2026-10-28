package com.demo.chat.mcp.portal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The portal client. One connection carries one request frame and one reply
 * frame. Every transport failure becomes `UNAVAILABLE`. See CHAT-gcjdvyhs.
 */
class PortalClientTests {
    @TempDir
    lateinit var dir: Path

    /** The one request text that the fake portal received. */
    private val received = AtomicReference("")

    /** Releases a fake portal that holds the connection open. */
    private val release = CountDownLatch(1)

    private fun socket(): Path = dir.resolve("portal.sock")

    /**
     * Serve one connection. The handler receives the request text. A null reply
     * holds the connection open, so the client deadline decides.
     */
    private fun portal(handler: (String) -> ByteArray?): Thread {
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socket()))
        val thread =
            Thread {
                runCatching {
                    server.accept().use { channel ->
                        val input = Channels.newInputStream(channel)
                        received.set(input.readBytes().toString(StandardCharsets.UTF_8))
                        val reply = handler(received.get())
                        if (reply != null) {
                            val output = Channels.newOutputStream(channel)
                            output.write(reply)
                            output.flush()
                        } else {
                            // Hold the connection open until the test releases it.
                            release.await(5, TimeUnit.SECONDS)
                        }
                    }
                }
                server.close()
            }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    /** One reply frame: the JSON text and one newline. */
    private fun frame(json: String): ByteArray =
        json.toByteArray(StandardCharsets.UTF_8) + '\n'.code.toByte()

    @Test
    fun `a missing socket answers UNAVAILABLE`() {
        val result = PortalClient(dir.resolve("missing.sock"), "register-index", Duration.ofMillis(100)).read()

        assertNull(result.status)
        assertFalse(result.complete)
        assertTrue(result.lines.isEmpty())
        assertEquals("UNAVAILABLE", result.errorCode)
    }

    @Test
    fun `a complete reply keeps its status and its line order`() {
        val thread =
            portal {
                frame("""{"status":0,"lines":[{"name":"first"},{"name":"second"}],"complete":true}""")
            }

        val result = PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read()

        assertEquals(0, result.status)
        assertTrue(result.complete)
        assertNull(result.errorCode)
        assertEquals(
            listOf("first", "second"),
            result.lines.map { (it as JsonObject)["name"]!!.jsonPrimitive.content },
        )
        thread.join(5_000)
    }

    @Test
    fun `a portal failure keeps its status and its code`() {
        val thread =
            portal {
                frame("""{"status":124,"lines":[],"complete":false,"error":{"code":"TIMEOUT"}}""")
            }

        val result = PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read()

        assertEquals(124, result.status)
        assertFalse(result.complete)
        assertEquals("TIMEOUT", result.errorCode)
        thread.join(5_000)
    }

    @Test
    fun `the request names the operation and the selector`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read()

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertEquals(setOf("operation", "index"), request.keys)
        assertEquals("read-register", request["operation"]!!.jsonPrimitive.content)
        assertEquals("register-index", request["index"]!!.jsonPrimitive.content)
        assertTrue(received.get().endsWith("\n"))
    }

    @Test
    fun `the request carries the topic when the read names one`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read("CHAT-123")

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertEquals("CHAT-123", request["topic"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the request carries no topic for an unfiltered read`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read()

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertFalse(request.containsKey("topic"), "the request carried a topic: ${received.get()}")
    }

    @Test
    fun `open sends the three write fields`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        val result = PortalClient(socket(), "register-index", Duration.ofSeconds(5)).open("CHAT-a", "snap", "Snapshot")

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertEquals(setOf("operation", "index", "key", "short", "title"), request.keys)
        assertEquals("open-register-entry", request["operation"]!!.jsonPrimitive.content)
        assertEquals("register-index", request["index"]!!.jsonPrimitive.content)
        assertEquals("CHAT-a", request["key"]!!.jsonPrimitive.content)
        assertEquals("snap", request["short"]!!.jsonPrimitive.content)
        assertEquals("Snapshot", request["title"]!!.jsonPrimitive.content)
        assertNull(result.errorCode)
    }

    @Test
    fun `post sends the text and a present request id`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        PortalClient(socket(), "register-index", Duration.ofSeconds(5)).post("CHAT-a", "hello", "r-1")

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertEquals(setOf("operation", "index", "key", "text", "requestId"), request.keys)
        assertEquals("post-register-entry", request["operation"]!!.jsonPrimitive.content)
        assertEquals("hello", request["text"]!!.jsonPrimitive.content)
        assertEquals("r-1", request["requestId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `post omits an absent request id`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        PortalClient(socket(), "register-index", Duration.ofSeconds(5)).post("CHAT-a", "hello", null)

        thread.join(5_000)
        val request = Json.parseToJsonElement(received.get()) as JsonObject
        assertFalse(request.containsKey("requestId"), "the request carried a request id: ${received.get()}")
    }

    @Test
    fun `a short reply answers UNAVAILABLE`() {
        val thread = portal { """{"status":0""".toByteArray(StandardCharsets.UTF_8) }

        val result = PortalClient(socket(), "register-index", Duration.ofSeconds(5)).read()

        assertEquals("UNAVAILABLE", result.errorCode)
        assertNull(result.status)
        thread.join(5_000)
    }

    @Test
    fun `an oversized reply answers UNAVAILABLE`() {
        val thread = portal { frame("""{"status":0,"lines":[],"complete":true}""") }

        val result =
            PortalClient(socket(), "register-index", Duration.ofSeconds(5), replyLimit = 16).read()

        assertEquals("UNAVAILABLE", result.errorCode)
        assertNull(result.status)
        thread.join(5_000)
    }

    @Test
    fun `a silent portal answers UNAVAILABLE at the deadline`() {
        val thread = portal { null }

        try {
            val started = System.nanoTime()
            val result = PortalClient(socket(), "register-index", Duration.ofMillis(100)).read()
            val elapsed = Duration.ofNanos(System.nanoTime() - started)

            assertEquals("UNAVAILABLE", result.errorCode)
            assertNull(result.status)
            assertTrue(elapsed >= Duration.ofMillis(100), "elapsed=$elapsed")
        } finally {
            release.countDown()
            thread.join(5_000)
        }
    }
}
