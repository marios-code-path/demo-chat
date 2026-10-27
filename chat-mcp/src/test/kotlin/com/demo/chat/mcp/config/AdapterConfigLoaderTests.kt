package com.demo.chat.mcp.config

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The configuration rules. One test per accept rule and one per rejection rule. */
class AdapterConfigLoaderTests {
    private val baseDirectory: Path = Path.of("/tmp/adapter-base")

    private fun properties(vararg pairs: Pair<String, String>): Properties =
        Properties().apply {
            setProperty("backendBaseUrl", "https://chat.example.test")
            setProperty("credentialFile", "token.txt")
            setProperty("keyType", "long")
            setProperty("topicIds", "7")
            pairs.forEach { (key, value) -> setProperty(key, value) }
        }

    // --- accept rules ---

    @Test
    fun `a complete configuration loads`() {
        val config = loadConfig(properties(), baseDirectory)
        assertEquals("https://chat.example.test", config.backendBaseUrl.toString())
        assertEquals(baseDirectory.resolve("token.txt"), config.credentialFile)
        assertEquals(KeyType.LONG, config.keyType)
        assertEquals(listOf<AdapterId>(LongId(7L)), config.topicIds)
    }

    @Test
    fun `a relative credential file resolves against the configuration directory`() {
        val config = loadConfig(properties("credentialFile" to "secrets/token.txt"), baseDirectory)
        assertEquals(baseDirectory.resolve("secrets/token.txt"), config.credentialFile)
    }

    @Test
    fun `send and search default to false`() {
        val config = loadConfig(properties(), baseDirectory)
        assertFalse(config.enableSend)
        assertFalse(config.enableSearch)
    }

    @Test
    fun `send and search accept an explicit true`() {
        val config = loadConfig(properties("enableSend" to "true", "enableSearch" to "true"), baseDirectory)
        assertTrue(config.enableSend)
        assertTrue(config.enableSearch)
    }

    @Test
    fun `several topic ids load in order`() {
        val config = loadConfig(properties("topicIds" to "3,1,2"), baseDirectory)
        assertEquals(listOf<AdapterId>(LongId(3L), LongId(1L), LongId(2L)), config.topicIds)
    }

    @Test
    fun `the separator whitespace of a topic list is removed`() {
        val config = loadConfig(properties("topicIds" to "3, 1, 2"), baseDirectory)
        assertEquals(listOf<AdapterId>(LongId(3L), LongId(1L), LongId(2L)), config.topicIds)
    }

    @Test
    fun `one hundred topic ids load`() {
        val text = (1..MAX_TOPIC_IDS).joinToString(",")
        assertEquals(MAX_TOPIC_IDS, loadConfig(properties("topicIds" to text), baseDirectory).topicIds.size)
    }

    // --- topic id rejection rules ---

    @Test
    fun `an absent topic list is refused`() {
        val failure = assertThrows(ConfigException::class.java) { loadConfig(properties("topicIds" to ""), baseDirectory) }
        assertTrue(failure.message!!.contains("at least one"), failure.message)
    }

    @Test
    fun `an empty topic list is refused`() {
        assertThrows(ConfigException::class.java) { loadConfig(properties("topicIds" to " , , "), baseDirectory) }
    }

    @Test
    fun `one hundred and one topic ids are refused`() {
        val text = (1..(MAX_TOPIC_IDS + 1)).joinToString(",")
        val failure = assertThrows(ConfigException::class.java) { loadConfig(properties("topicIds" to text), baseDirectory) }
        assertTrue(failure.message!!.contains("above the limit"), failure.message)
    }

    @Test
    fun `a repeated topic id is refused`() {
        val failure = assertThrows(ConfigException::class.java) {
            loadConfig(properties("topicIds" to "7,7"), baseDirectory)
        }
        assertTrue(failure.message!!.contains("repeats"), failure.message)
    }

    @Test
    fun `a non canonical topic id is refused`() {
        assertThrows(ConfigException::class.java) { loadConfig(properties("topicIds" to "7,08"), baseDirectory) }
    }

    @Test
    fun `a topic id with inner whitespace is refused`() {
        assertThrows(ConfigException::class.java) { loadConfig(properties("topicIds" to "7 , 1 2"), baseDirectory) }
    }

    // --- key rejection rules ---

    @Test
    fun `an absent backend URL is refused`() {
        val props = properties()
        props.remove("backendBaseUrl")
        val failure = assertThrows(ConfigException::class.java) { loadConfig(props, baseDirectory) }
        assertTrue(failure.message!!.contains("backendBaseUrl"), failure.message)
    }

    @Test
    fun `an absent key type is refused`() {
        val props = properties()
        props.remove("keyType")
        assertThrows(ConfigException::class.java) { loadConfig(props, baseDirectory) }
    }

    @Test
    fun `an unknown key is refused and the message names it`() {
        val failure = assertThrows(ConfigException::class.java) {
            loadConfig(properties("operationPolicy" to "anything"), baseDirectory)
        }
        assertTrue(failure.message!!.contains("operationPolicy"), failure.message)
    }

    @Test
    fun `a boolean key with another value is refused`() {
        assertThrows(ConfigException::class.java) {
            loadConfig(properties("enableSend" to "yes"), baseDirectory)
        }
    }

    // --- the portal settings ---

    @Test
    fun `the portal settings are absent by default`() {
        val config = loadConfig(properties(), baseDirectory)
        assertNull(config.portalSocket)
        assertNull(config.registerName)
        assertNull(config.registerId)
        assertNull(config.registerSelector)
    }

    @Test
    fun `a complete portal configuration loads`() {
        val config =
            loadConfig(
                properties("portalSocket" to "/run/chat/portal.sock", "registerName" to "register-index"),
                baseDirectory,
            )
        assertEquals(Path.of("/run/chat/portal.sock"), config.portalSocket)
        assertEquals("register-index", config.registerSelector)
    }

    @Test
    fun `the register name wins when both selectors exist`() {
        val config =
            loadConfig(
                properties(
                    "portalSocket" to "/tmp/chat-portal.sock",
                    "registerName" to "register-index",
                    "registerId" to "123e4567-e89b-12d3-a456-426614174000",
                ),
                baseDirectory,
            )
        assertEquals("register-index", config.registerSelector)
    }

    @Test
    fun `the register id is the selector when the name is absent`() {
        val config =
            loadConfig(
                properties(
                    "portalSocket" to "/tmp/chat-portal.sock",
                    "registerId" to "123e4567-e89b-12d3-a456-426614174000",
                ),
                baseDirectory,
            )
        assertEquals("123e4567-e89b-12d3-a456-426614174000", config.registerSelector)
    }

    @Test
    fun `a relative portal socket is refused`() {
        val failure =
            assertThrows(ConfigException::class.java) {
                loadConfig(
                    properties("portalSocket" to "run/portal.sock", "registerName" to "register-index"),
                    baseDirectory,
                )
            }
        assertTrue(failure.message!!.contains("absolute"), failure.message)
    }

    @Test
    fun `a portal socket without a selector is refused`() {
        val failure =
            assertThrows(ConfigException::class.java) {
                loadConfig(properties("portalSocket" to "/tmp/chat-portal.sock"), baseDirectory)
            }
        assertTrue(failure.message!!.contains("registerName"), failure.message)
    }

    @Test
    fun `a selector without a portal socket is refused`() {
        val failure =
            assertThrows(ConfigException::class.java) {
                loadConfig(properties("registerName" to "register-index"), baseDirectory)
            }
        assertTrue(failure.message!!.contains("portalSocket"), failure.message)
    }

    @Test
    fun `an invalid register name is refused`() {
        assertThrows(ConfigException::class.java) {
            loadConfig(
                properties("portalSocket" to "/tmp/chat-portal.sock", "registerName" to "-register"),
                baseDirectory,
            )
        }
    }

    @Test
    fun `an invalid register id is refused`() {
        assertThrows(ConfigException::class.java) {
            loadConfig(
                properties("portalSocket" to "/tmp/chat-portal.sock", "registerId" to "has space"),
                baseDirectory,
            )
        }
    }

    // --- the credential file ---

    @Test
    fun `the credential is read from the file`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("token.txt")
        Files.writeString(file, "  a-secret-token\n")
        assertEquals("a-secret-token", readCredentialFile(file))
    }

    @Test
    fun `an absent credential file is refused`(
        @TempDir directory: Path,
    ) {
        assertThrows(ConfigException::class.java) { readCredentialFile(directory.resolve("missing.txt")) }
    }

    @Test
    fun `an empty credential file is refused`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("token.txt")
        Files.writeString(file, "   \n")
        val failure = assertThrows(ConfigException::class.java) { readCredentialFile(file) }
        assertTrue(failure.message!!.contains("holds no token"), failure.message)
    }

    @Test
    fun `the configuration holds no token`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("token.txt")
        Files.writeString(file, "a-secret-token")
        val config = loadConfig(properties("credentialFile" to file.toString()), directory)
        // The token is never a field of the configuration, so no log line and
        // no toString can carry it.
        assertFalse(config.toString().contains("a-secret-token"))
    }

    // --- the configuration file and the arguments ---

    @Test
    fun `the configuration file is read and its directory resolves the credential path`(
        @TempDir directory: Path,
    ) {
        val file = directory.resolve("adapter.properties")
        Files.writeString(
            file,
            """
            backendBaseUrl=https://chat.example.test
            credentialFile=token.txt
            keyType=uuid
            topicIds=550e8400-e29b-41d4-a716-446655440000
            """.trimIndent(),
        )
        val config = loadConfigFile(file)
        assertEquals(KeyType.UUID, config.keyType)
        assertEquals(directory.resolve("token.txt"), config.credentialFile)
    }

    @Test
    fun `an absent configuration file is refused`(
        @TempDir directory: Path,
    ) {
        assertThrows(ConfigException::class.java) { loadConfigFile(directory.resolve("missing.properties")) }
    }

    @Test
    fun `the argument and the equals form both name the file`() {
        val expected = Path.of("/etc/chat-mcp.properties")
        assertEquals(expected, configPathFrom(listOf("--config", "/etc/chat-mcp.properties"), emptyMap()))
        assertEquals(expected, configPathFrom(listOf("--config=/etc/chat-mcp.properties"), emptyMap()))
    }

    @Test
    fun `the environment variable names the file when no argument does`() {
        val expected = Path.of("/etc/chat-mcp.properties")
        assertEquals(expected, configPathFrom(emptyList(), mapOf(CONFIG_ENVIRONMENT to "/etc/chat-mcp.properties")))
    }

    @Test
    fun `the argument wins over the environment variable`() {
        val expected = Path.of("/etc/from-argument.properties")
        val actual =
            configPathFrom(
                listOf("--config", "/etc/from-argument.properties"),
                mapOf(CONFIG_ENVIRONMENT to "/etc/from-environment.properties"),
            )
        assertEquals(expected, actual)
    }

    @Test
    fun `a credential argument is refused`() {
        // A credential must never arrive as an argument, because an argument
        // is visible in the process list.
        assertThrows(ConfigException::class.java) {
            configPathFrom(listOf("--config", "/etc/chat-mcp.properties", "--token", "a-secret"), emptyMap())
        }
    }

    @Test
    fun `an unknown argument is refused`() {
        val failure = assertThrows(ConfigException::class.java) {
            configPathFrom(listOf("--verbose"), emptyMap())
        }
        assertTrue(failure.message!!.contains("--verbose"), failure.message)
    }

    @Test
    fun `a repeated config argument is refused`() {
        assertThrows(ConfigException::class.java) {
            configPathFrom(listOf("--config", "/a.properties", "--config", "/b.properties"), emptyMap())
        }
    }

    @Test
    fun `a config argument with no path is refused`() {
        assertThrows(ConfigException::class.java) { configPathFrom(listOf("--config"), emptyMap()) }
    }

    @Test
    fun `an absent configuration path is refused`() {
        val failure = assertThrows(ConfigException::class.java) { configPathFrom(emptyList(), emptyMap()) }
        assertTrue(failure.message!!.contains(CONFIG_ENVIRONMENT), failure.message)
    }
}