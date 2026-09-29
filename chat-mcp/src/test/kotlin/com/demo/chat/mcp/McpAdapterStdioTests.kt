package com.demo.chat.mcp

import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class McpAdapterStdioTests {
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
            val server = createMcpServer()
            val transport = StdioServerTransport(input, output)
            val closed = CompletableDeferred<Unit>()
            transport.onClose { closed.complete(Unit) }

            server.createSession(transport)

            val arrived = withTimeoutOrNull(SHUTDOWN_BOUND_MILLIS) { closed.await() }
            assertNotNull(arrived, "the transport did not close within the bound")
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
            val server = createMcpServer()
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
