package com.demo.chat.mcp.config

import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The origin rules. One test per accept rule and one per rejection rule. */
class AdapterOriginTests {
    private fun origin(text: String): URI = loadConfig(properties(text), java.nio.file.Path.of("/tmp")).backendBaseUrl

    private fun properties(url: String): java.util.Properties =
        java.util.Properties().apply {
            setProperty("backendBaseUrl", url)
            setProperty("credentialFile", "token.txt")
            setProperty("keyType", "long")
            setProperty("topicIds", "7")
        }

    // --- accept rules ---

    @Test
    fun `a fixed https origin is accepted`() {
        assertEquals("https://chat.example.test", origin("https://chat.example.test").toString())
    }

    @Test
    fun `an explicit port is kept`() {
        assertEquals(8443, origin("https://chat.example.test:8443").port)
    }

    @Test
    fun `a trailing slash is accepted and removed`() {
        assertEquals("https://chat.example.test", origin("https://chat.example.test/").toString())
    }

    @Test
    fun `http is accepted on localhost`() {
        assertEquals("http://localhost", origin("http://localhost").toString())
    }

    @Test
    fun `http is accepted on the loopback address`() {
        assertEquals("127.0.0.1", origin("http://127.0.0.1:8080").host)
    }

    @Test
    fun `http is accepted on the loopback v6 address`() {
        // URI.getHost() returns an IPv6 host with its brackets. The origin
        // text keeps them, on both sides of a comparison.
        assertEquals("http://[::1]:8080", origin("http://[::1]:8080").toString())
    }

    @Test
    fun `a v6 loopback target is compared without a bracket mismatch`() {
        val configured = origin("http://[::1]:8080")
        requireSameOrigin(configured, URI("http://[::1]:8080/topic/id/7"))
    }

    // --- rejection rules ---

    @Test
    fun `http off loopback is refused`() {
        val failure =
            assertThrows(ConfigException::class.java) { origin("http://chat.example.test") }
        assertEquals(true, failure.message!!.contains("loopback"))
    }

    @Test
    fun `a URL with a path is refused`() {
        assertThrows(ConfigException::class.java) { origin("https://chat.example.test/api") }
    }

    @Test
    fun `a URL with a query is refused`() {
        assertThrows(ConfigException::class.java) { origin("https://chat.example.test?a=1") }
    }

    @Test
    fun `a URL with a fragment is refused`() {
        assertThrows(ConfigException::class.java) { origin("https://chat.example.test#top") }
    }

    @Test
    fun `a URL with user info is refused`() {
        assertThrows(ConfigException::class.java) { origin("https://user:secret@chat.example.test") }
    }

    @Test
    fun `a URL with no host is refused`() {
        assertThrows(ConfigException::class.java) { origin("https://") }
    }

    @Test
    fun `another scheme is refused`() {
        assertThrows(ConfigException::class.java) { origin("ftp://chat.example.test") }
    }

    @Test
    fun `a URL with no scheme is refused`() {
        assertThrows(ConfigException::class.java) { origin("chat.example.test") }
    }

    // --- the redirect rule ---

    @Test
    fun `a target on the same origin is accepted`() {
        val configured = URI("https://chat.example.test")
        requireSameOrigin(configured, URI("https://chat.example.test/topic/id/7"))
    }

    @Test
    fun `a target is compared on the effective port`() {
        // The configured origin states no port, so it is 443. A target that
        // states 443 is the same origin.
        requireSameOrigin(URI("https://chat.example.test"), URI("https://chat.example.test:443/topic"))
    }

    @Test
    fun `a target on another host is refused`() {
        val failure = assertThrows(ConfigException::class.java) {
            requireSameOrigin(URI("https://chat.example.test"), URI("https://evil.example.test/topic"))
        }
        assertEquals(true, failure.message!!.contains("origin"))
    }

    @Test
    fun `a target on another scheme is refused`() {
        assertThrows(ConfigException::class.java) {
            requireSameOrigin(URI("https://chat.example.test"), URI("http://chat.example.test/topic"))
        }
    }

    @Test
    fun `a target on another port is refused`() {
        assertThrows(ConfigException::class.java) {
            requireSameOrigin(URI("https://chat.example.test"), URI("https://chat.example.test:8443/topic"))
        }
    }

    @Test
    fun `a relative target is refused`() {
        assertThrows(ConfigException::class.java) {
            requireSameOrigin(URI("https://chat.example.test"), URI("/topic/id/7"))
        }
    }
}