package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import com.demo.chat.mcp.config.LongId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The two topic rules, driven with no MCP server and no transport.
 *
 * Every case reads a real loopback backend over the production transport, so
 * the status codes are exercised and not mocked.
 */
class TopicToolServiceTests {
    private fun service(
        backend: FakeTopicBackend,
        ids: List<LongId>,
    ): TopicToolService = TopicToolService(testConfig(origin = backend.origin, topicIds = ids))

    @Test
    fun `the list reads every configured topic`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 200, topicBody(22, 7, "beta"))

            val topics = service(backend, listOf(LongId(11), LongId(22))).listTopics()

            assertEquals(listOf("alpha", "beta"), topics.map { it.name })
            assertEquals(listOf("11", "22"), topics.map { it.id })
            assertEquals(listOf("7", "7"), topics.map { it.root })
        }
    }

    @Test
    fun `a denied topic is absent from the list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 403, topicBody(22, 7, "refused"))

            val topics = service(backend, listOf(LongId(11), LongId(22))).listTopics()

            assertEquals(listOf("alpha"), topics.map { it.name })
        }
    }

    @Test
    fun `an absent topic is absent from the list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))

            val topics = service(backend, listOf(LongId(11), LongId(22))).listTopics()

            assertEquals(listOf("alpha"), topics.map { it.name })
        }
    }

    /**
     * A denial is not an error and not a second attempt. The adapter asks for
     * each configured id once.
     */
    @Test
    fun `the list asks for each configured id once`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 403, topicBody(22, 7, "refused"))

            service(backend, listOf(LongId(11), LongId(22))).listTopics()

            assertEquals(listOf("11", "22"), backend.requestedIds.toList())
        }
    }

    @Test
    fun `the list holds nothing when every topic is denied`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 403, topicBody(11, 7, "refused"))

            assertTrue(service(backend, listOf(LongId(11))).listTopics().isEmpty())
        }
    }

    @Test
    fun `a backend failure fails the whole list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))
            backend.answer("22", 500, """{"error":"broken"}""")

            val refused =
                assertThrows(ClientException::class.java) {
                    service(backend, listOf(LongId(11), LongId(22))).listTopics()
                }

            assertEquals(FailureReason.BACKEND, refused.reason)
        }
    }

    @Test
    fun `a refused credential fails the whole list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 401, """{"error":"no"}""")

            val refused =
                assertThrows(ClientException::class.java) {
                    service(backend, listOf(LongId(11))).listTopics()
                }

            assertEquals(FailureReason.AUTHENTICATION, refused.reason)
        }
    }

    @Test
    fun `the single read answers one topic`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 200, topicBody(11, 7, "alpha"))

            val topic = service(backend, listOf(LongId(11))).getTopic("11")

            assertEquals(TopicView(id = "11", root = "7", name = "alpha"), topic)
        }
    }

    /**
     * The allowlist runs before any backend call. A refused argument must leave
     * no trace on the wire.
     */
    @Test
    fun `the single read refuses a topic outside the configured list`() {
        FakeTopicBackend().use { backend ->
            backend.answer("99", 200, topicBody(99, 7, "hidden"))

            val refused =
                assertThrows(ToolException::class.java) {
                    service(backend, listOf(LongId(11))).getTopic("99")
                }

            assertTrue(refused.message!!.contains("not one of the configured topic ids"))
            assertTrue(backend.requestedIds.isEmpty(), "the adapter called the backend for a refused id")
        }
    }

    @Test
    fun `the single read refuses malformed id text`() {
        FakeTopicBackend().use { backend ->
            val refused =
                assertThrows(ToolException::class.java) {
                    service(backend, listOf(LongId(11))).getTopic(" 11 ")
                }

            assertTrue(refused.message!!.contains("the topic id is refused"))
            assertTrue(backend.requestedIds.isEmpty())
        }
    }

    /**
     * The single read is not the list. A topic that the backend does not serve
     * fails this call, and the answer names neither a hidden object nor an
     * absent one.
     */
    @Test
    fun `a denied topic fails the single read`() {
        FakeTopicBackend().use { backend ->
            backend.answer("11", 403, topicBody(11, 7, "refused"))

            val refused =
                assertThrows(ClientException::class.java) {
                    service(backend, listOf(LongId(11))).getTopic("11")
                }

            assertEquals(FailureReason.NOT_AVAILABLE, refused.reason)
        }
    }

    /**
     * A Long id above 2^53 keeps every digit. The value is one below a number
     * that a Double holds exactly, so a Double read would answer ...904.
     */
    @Test
    fun `an id above two to the fifty three keeps every digit`() {
        val id = 1554361143634427905L
        val root = 1554361143634427905L

        FakeTopicBackend().use { backend ->
            backend.answer("$id", 200, topicBody(id, root, "alpha"))

            val topic = service(backend, listOf(LongId(id))).getTopic("1554361143634427905")

            assertEquals("1554361143634427905", topic.id)
            assertEquals("1554361143634427905", topic.root)
        }
    }
}
