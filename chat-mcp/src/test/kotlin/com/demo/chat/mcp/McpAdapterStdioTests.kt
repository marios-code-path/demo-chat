package com.demo.chat.mcp

import com.demo.chat.mcp.client.BackendHttp
import com.demo.chat.mcp.client.JdkBackendHttp
import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.AdapterId
import com.demo.chat.mcp.tool.testConfig
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpAdapterStdioTests {
    /**
     * A configuration that reaches no backend.
     *
     * These cases read the transport alone, so the adapter never opens a
     * socket.
     */
    private fun config(): AdapterConfig =
        testConfig(origin = URI("http://127.0.0.1:1"), topicIds = emptyList<AdapterId>())

    /** A transport that reaches no network and records its own close. */
    private class RecordingHttp : BackendHttp {
        var closed: Boolean = false
            private set

        override fun get(target: URI, credential: String): String =
            error("this case reaches no backend")

        override fun close() {
            closed = true
        }
    }

    /**
     * An end of file on the input closes the transport. This is the path that
     * ends the process when a client closes stdin.
     *
     * The input is an empty stream and not an empty Buffer. A Buffer holds no
     * end of file marker, so a read from one waits for data forever.
     */
    @Test
    fun `an end of file input closes the transport`() {
        runBlocking {
            val input = ByteArrayInputStream(ByteArray(0)).asSource().buffered()
            val output = ByteArrayOutputStream().asSink().buffered()
            JdkBackendHttp(config().backendBaseUrl).use { http ->
                val server = createMcpServer(config(), http)
                val transport = StdioServerTransport(input, output)
                val closed = CompletableDeferred<Unit>()
                transport.onClose { closed.complete(Unit) }

                server.createSession(transport)

                val arrived = withTimeoutOrNull(SHUTDOWN_BOUND_MILLIS) { closed.await() }
                assertNotNull(arrived, "the transport did not close within the bound")
            }
        }
    }

    /**
     * A server close callback runs from Server.close() alone. An end of file
     * does not reach it. This test pins that reading, because the shutdown
     * path depends on it.
     */
    @Test
    fun `an end of file does not reach the server close callback`() {
        runBlocking {
            val input = ByteArrayInputStream(ByteArray(0)).asSource().buffered()
            val output = ByteArrayOutputStream().asSink().buffered()
            JdkBackendHttp(config().backendBaseUrl).use { http ->
                val server = createMcpServer(config(), http)
                val transport = StdioServerTransport(input, output)
                val serverClosed = CompletableDeferred<Unit>()
                server.onClose { serverClosed.complete(Unit) }

                server.createSession(transport)
                transport.close()

                val arrived = withTimeoutOrNull(500) { serverClosed.await() }
                assertNull(arrived, "the server close callback ran without Server.close()")
            }
        }
    }

    /**
     * The adapter owns one transport for its process, and the stdio path
     * closes it. A process that never closed the transport would keep its
     * watchdog thread for its whole life.
     *
     * This drives the production stdio path with an input that reaches end of
     * file at once. The process streams and the exit call belong to
     * `runStdioAdapter`, which a test must not call.
     */
    @Test
    fun `the stdio path closes the transport it owns`() {
        val http = RecordingHttp()
        val input = ByteArrayInputStream(ByteArray(0)).asSource().buffered()
        val output = ByteArrayOutputStream().asSink().buffered()

        serveStdio(config(), http, input, output)

        assertTrue(http.closed, "the stdio path did not close the transport it owns")
    }
}
