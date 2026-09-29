package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import com.demo.chat.mcp.config.loadConfig
import com.demo.chat.mcp.config.parseIdText
import java.net.URI
import java.nio.file.Path
import java.util.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** The topic reader. One test per rule. */
class TopicClientTests {
    private val captured: String =
        javaClass.getResourceAsStream("/topic-response.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    /** One fake transport that records what it was asked for. */
    private class FakeHttp(private val body: String) : BackendHttp {
        var lastTarget: URI? = null
        var lastCredential: String? = null
        var calls: Int = 0

        override fun get(target: URI, credential: String): String {
            lastTarget = target
            lastCredential = credential
            calls += 1
            return body
        }
    }

    private fun config(
        directory: Path,
        token: String = "a-token",
        keyType: String = "long",
        url: String = "https://chat.example.test",
    ): com.demo.chat.mcp.config.AdapterConfig {
        directory.resolve("token.txt").toFile().writeText("$token\n")
        val properties =
            Properties().apply {
                setProperty("backendBaseUrl", url)
                setProperty("credentialFile", "token.txt")
                setProperty("keyType", keyType)
                setProperty("topicIds", "7")
            }
        return loadConfig(properties, directory)
    }

    @Test
    fun `the call reads the configured route with the configured credential`(@TempDir directory: Path) {
        val config = config(directory)
        val http = FakeHttp(captured)
        val topic = TopicClient(config, http).readTopic(parseIdText("1554361326074068992", KeyType.LONG))

        assertEquals("https://chat.example.test/topic/id/1554361326074068992", http.lastTarget.toString())
        assertEquals("a-token", http.lastCredential)
        assertEquals("1554361326074068992", topic.id)
    }

    @Test
    fun `a long id above 2^53 enters the path as exact digits`(@TempDir directory: Path) {
        val config = config(directory)
        val http = FakeHttp(captured)
        TopicClient(config, http).readTopic(parseIdText("1554361326074068992", KeyType.LONG))

        // The path alone is read here. The configured host carries dots.
        assertEquals("/topic/id/1554361326074068992", http.lastTarget!!.path)
        assertEquals(false, http.lastTarget!!.path.contains("E"))
        assertEquals(false, http.lastTarget!!.path.contains("."))
        assertEquals(false, http.lastTarget!!.path.contains("%"))
    }

    @Test
    fun `the credential is read again at each request`(@TempDir directory: Path) {
        val config = config(directory)
        val http = FakeHttp(captured)
        val client = TopicClient(config, http)
        val id = parseIdText("7", KeyType.LONG)

        client.readTopic(id)
        assertEquals("a-token", http.lastCredential)

        directory.resolve("token.txt").toFile().writeText("a-later-token\n")
        client.readTopic(id)
        assertEquals("a-later-token", http.lastCredential)
        assertEquals(2, http.calls)
    }

    @Test
    fun `an absent credential file fails at the request`(@TempDir directory: Path) {
        val config = config(directory)
        directory.resolve("token.txt").toFile().delete()
        val http = FakeHttp(captured)

        assertThrows(com.demo.chat.mcp.config.ConfigException::class.java) {
            TopicClient(config, http).readTopic(parseIdText("7", KeyType.LONG))
        }
        // No request went out.
        assertEquals(0, http.calls)
    }

    @Test
    fun `the route is built from the configured origin alone`() {
        val origin = URI("https://chat.example.test")
        assertEquals(
            "https://chat.example.test/topic/id/7",
            topicUri(origin, parseIdText("7", KeyType.LONG)).toString(),
        )
    }

    @Test
    fun `a credential with a control character is refused by the guard`() {
        val failure =
            assertThrows(ClientException::class.java) { usableCredential("a\nb") }
        assertEquals(true, failure.message!!.contains("control character"))
    }

    @Test
    fun `a plain credential passes the guard`() {
        assertEquals("a-token", usableCredential("a-token"))
    }

    @Test
    fun `a uuid route carries the canonical text`() {
        val id = parseIdText("6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b", KeyType.UUID)
        assertEquals(
            "https://chat.example.test/topic/id/6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b",
            topicUri(URI("https://chat.example.test"), id).toString(),
        )
    }
}
