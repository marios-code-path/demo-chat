package com.demo.chat.mcp.config

import com.demo.chat.mcp.loadConfigForStartup
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PortalOnlyConfigTests {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `portal only configuration needs no HTTP settings`() {
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nportalSocket=/tmp/portal.sock\nregisterName=register-index\n")

        val config = loadMcpConfigFile(path)

        assertTrue(config is McpLaunchConfig.PortalOnly)
        config as McpLaunchConfig.PortalOnly
        assertEquals(Path.of("/tmp/portal.sock"), config.portalSocketSource.resolve())
        assertEquals("register-index", config.registerSelector)
    }

    @Test
    fun `portal only reads the socket path from a file`() {
        val socketFile = directory.resolve("socket")
        Files.writeString(socketFile, "/tmp/portal.sock\n")
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=register-index\n")

        val config = loadMcpConfigFile(path) as McpLaunchConfig.PortalOnly

        assertEquals(Path.of("/tmp/portal.sock"), config.portalSocketSource.resolve())
    }

    @Test
    fun `portal only rejects both socket keys`() {
        val path = directory.resolve("adapter.properties")
        Files.writeString(
            path,
            "mode=portal-only\nportalSocket=/tmp/a.sock\nportalSocketFile=/tmp/b\nregisterName=r\n",
        )

        val failure = assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }

        assertTrue(failure.message!!.contains("portalSocket"), failure.message)
    }

    @Test
    fun `portal only rejects neither socket key`() {
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nregisterName=r\n")

        val failure = assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }

        assertTrue(failure.message!!.contains("portalSocket"), failure.message)
    }

    @Test
    fun `portal only rejects a relative path in the socket file`() {
        val socketFile = directory.resolve("socket")
        Files.writeString(socketFile, "portal.sock\n")
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=r\n")

        assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }
    }

    @Test
    fun `portal only rejects an empty socket file`() {
        val socketFile = directory.resolve("socket")
        Files.writeString(socketFile, "")
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nportalSocketFile=$socketFile\nregisterName=r\n")

        assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }
    }

    @Test
    fun `portal only configuration rejects HTTP settings`() {
        val path = directory.resolve("adapter.properties")
        Files.writeString(
            path,
            "mode=portal-only\nportalSocket=/tmp/portal.sock\nregisterName=register-index\n" +
                "credentialFile=token.txt\n",
        )

        val failure = assertThrows(ConfigException::class.java) { loadMcpConfigFile(path) }

        assertTrue(failure.message!!.contains("credentialFile"), failure.message)
    }

    @Test
    fun `startup accepts portal only settings without a credential file`() {
        val path = directory.resolve("adapter.properties")
        Files.writeString(path, "mode=portal-only\nportalSocket=/tmp/portal.sock\nregisterName=register-index\n")

        val config = loadConfigForStartup(listOf("--config", path.toString()), emptyMap())

        assertEquals(
            McpLaunchConfig.PortalOnly(PortalSocketSource.Fixed(Path.of("/tmp/portal.sock")), "register-index", "register-index"),
            config,
        )
    }
}
