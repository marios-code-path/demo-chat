package com.demo.chat.mcp

import com.demo.chat.mcp.config.McpLaunchConfig
import com.demo.chat.mcp.config.PortalSocketSource
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PortalOnlyToolTests {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `portal only mode discovers one register tool for a register id`() {
        val config = McpLaunchConfig.PortalOnly(PortalSocketSource.Fixed(Path.of("/tmp/portal.sock")), "register-index")

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
                portalSocketSource = PortalSocketSource.Fixed(Path.of("/tmp/portal.sock")),
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

    @Test
    fun `each tool call reads the socket file again`() {
        val file = directory.resolve("socket")
        Files.writeString(file, "/tmp/does-not-exist.sock\n")
        val config =
            McpLaunchConfig.PortalOnly(PortalSocketSource.FileBacked(file), "register-index", "register-index")
        val (socket, stop) = startPortal("""{"status":0,"lines":[],"complete":true}""")
        try {
            StdioHarness(config).use { client ->
                client.initialize()
                Files.writeString(file, "$socket\n")

                val result = client.callTool(2, "chat_read_register", "{}")

                assertEquals("0", client.structuredOf(result)["status"]!!.jsonPrimitive.content)
                assertEquals(false, client.isError(result))
            }
        } finally {
            stop()
        }
    }

    @Test
    fun `a missing socket file answers unavailable`() {
        val file = directory.resolve("socket")
        Files.writeString(file, "/tmp/does-not-exist.sock\n")
        val config =
            McpLaunchConfig.PortalOnly(PortalSocketSource.FileBacked(file), "register-index", "register-index")

        StdioHarness(config).use { client ->
            client.initialize()
            Files.delete(file)

            val result = client.callTool(2, "chat_read_register", "{}")

            assertTrue(client.isError(result))
            val code = client.structuredOf(result)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content
            assertEquals("UNAVAILABLE", code)
        }
    }

    /** One portal server that answers one read and then stops. */
    private fun startPortal(reply: String): Pair<Path, () -> Unit> {
        val directory = Files.createTempDirectory("portal")
        val socket = directory.resolve("p")
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socket))
        val thread =
            Thread {
                runCatching {
                    server.accept().use { channel ->
                        Channels.newInputStream(channel).readAllBytes()
                        Channels.newOutputStream(channel).use {
                            it.write((reply + "\n").toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
            }
        thread.isDaemon = true
        thread.start()
        return socket to {
            runCatching { server.close() }
            runCatching { Files.deleteIfExists(socket) }
        }
    }
}
