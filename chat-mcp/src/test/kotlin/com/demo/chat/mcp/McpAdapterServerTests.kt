package com.demo.chat.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class McpAdapterServerTests {
    @Test
    fun `the factory builds a server`() {
        val server: Server = createMcpServer()
        assertNotNull(server)
    }

    /**
     * Task 1 registers no tool. A tool that arrives here means the module
     * moved past its stated scope. Task 4 owns the first two tools.
     */
    @Test
    fun `the server holds no tool yet`() {
        val server = createMcpServer()
        assertEquals(0, server.tools.size)
    }

    @Test
    fun `the server identity is the declared name and version`() {
        assertEquals("demo-chat-mcp", MCP_SERVER_NAME)
        assertEquals("0.0.1", MCP_SERVER_VERSION)
    }
}
