package com.demo.chat.mcp

import com.demo.chat.mcp.config.McpLaunchConfig
import java.nio.file.Path
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PortalOnlyToolTests {
    @Test
    fun `portal only mode discovers one register tool for a register id`() {
        val config = McpLaunchConfig.PortalOnly(Path.of("/tmp/portal.sock"), "register-index")

        StdioHarness(config).use { client ->
            client.initialize()
            val names = client.listTools(2)["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }

            assertEquals(listOf("chat_read_register"), names)
        }
    }

    @Test
    fun `portal only mode discovers three register tools for a register name`() {
        val config =
            McpLaunchConfig.PortalOnly(
                portalSocket = Path.of("/tmp/portal.sock"),
                registerSelector = "register-index",
                registerName = "register-index",
            )

        StdioHarness(config).use { client ->
            client.initialize()
            val names = client.listTools(2)["tools"]!!.jsonArray
                .map { it.jsonObject["name"]!!.jsonPrimitive.content }

            assertEquals(
                listOf("chat_read_register", "chat_open_register_entry", "chat_post_register_entry"),
                names,
            )
        }
    }
}
