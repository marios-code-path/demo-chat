package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.KeyType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * The topic envelope contract.
 *
 * `the captured response decodes` reads the bytes that a real memory
 * deployment sent on 2026-09-28. The other tests name one rule each.
 */
class TopicEnvelopeTests {
    private fun captured(): String =
        javaClass.getResourceAsStream("/topic-response.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun refusal(body: String): String =
        assertThrows(ClientException::class.java) { decodeTopic(body, KeyType.LONG) }.message!!

    // --- the contract ---

    @Test
    fun `the captured response decodes`() {
        val topic = decodeTopic(captured(), KeyType.LONG)
        assertEquals("1554361326074068992", topic.id)
        assertEquals("1554361143634427905", topic.root)
        assertEquals("mcpcontracttopic", topic.data)
    }

    @Test
    fun `an id above 2^53 keeps every digit`() {
        // 1554361326074068992 is above 9007199254740992. A Double would answer
        // 1554361326074068992.0 at best, and a nearer value at worst.
        val topic = decodeTopic(captured(), KeyType.LONG)
        assertEquals(19, topic.id.length)
        assertEquals(1554361326074068992L.toString(), topic.id)
    }

    @Test
    fun `the outer wrapper is required`() {
        // The same fields, with the `keyValue` wrapper taken off.
        val body = """{"data":"mcpcontracttopic","key":{"key":{"id":7,"root":8,"empty":false}}}"""
        assertEquals(true, refusal(body).contains("keyValue"))
    }

    @Test
    fun `the inner key wrapper is required`() {
        val body = """{"keyValue":{"data":"a","key":{"id":7,"root":8,"empty":false}}}"""
        assertEquals(true, refusal(body).contains("key"))
    }

    // --- the data field ---

    @Test
    fun `a missing data field is refused`() {
        val body = """{"keyValue":{"key":{"key":{"id":7,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("data"))
    }

    @Test
    fun `a numeric data field is refused`() {
        val body = """{"keyValue":{"data":5,"key":{"key":{"id":7,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("data"))
    }

    @Test
    fun `an empty topic name is accepted`() {
        // An empty name is a value. It is not a missing field.
        val body = """{"keyValue":{"data":"","key":{"key":{"id":7,"root":8,"empty":false}}}}"""
        assertEquals("", decodeTopic(body, KeyType.LONG).data)
    }

    // --- the key ---

    @Test
    fun `an empty key is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7,"root":8,"empty":true}}}}"""
        assertEquals(true, refusal(body).contains("empty"))
    }

    @Test
    fun `a missing id is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("id"))
    }

    @Test
    fun `a missing root is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("root"))
    }

    @Test
    fun `a null id is refused`() {
        // A JSON null reads as the four character text `null` through
        // JsonPrimitive.content. The adapter refuses it before that text can
        // reach an id.
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":null,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("null"))
    }

    @Test
    fun `a null root is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7,"root":null,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("null"))
    }

    @Test
    fun `a string id is refused for a long deployment`() {
        // A backend shape change fails here rather than later.
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":"7","root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("JSON number"))
    }

    @Test
    fun `a numeric uuid is refused for a uuid deployment`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7,"root":8,"empty":false}}}}"""
        val failure =
            assertThrows(ClientException::class.java) { decodeTopic(body, KeyType.UUID) }
        assertEquals(true, failure.message!!.contains("JSON string"))
    }

    @Test
    fun `a fractional id is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7.5,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("base-10"))
    }

    @Test
    fun `an exponent id is refused`() {
        // This form is what a Double conversion produces. The adapter refuses
        // it, so a loss of precision fails loudly.
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":1.554361326074068992E18,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("base-10"))
    }

    @Test
    fun `a zero id is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":0,"root":8,"empty":false}}}}"""
        assertEquals(true, refusal(body).contains("zero"))
    }

    @Test
    fun `a non boolean empty field is refused`() {
        val body = """{"keyValue":{"data":"a","key":{"key":{"id":7,"root":8,"empty":"false"}}}}"""
        assertEquals(true, refusal(body).contains("boolean"))
    }

    @Test
    fun `a body that is not JSON is refused`() {
        assertEquals(true, refusal("not json").contains("JSON"))
    }

    @Test
    fun `a uuid deployment decodes its own shape`() {
        val body =
            """{"keyValue":{"data":"a","key":{"key":{"id":"6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b",""" +
                """"root":"5a4b3c2d-1e0f-4a5b-8c9d-0e1f2a3b4c5d","empty":false}}}}"""
        val topic = decodeTopic(body, KeyType.UUID)
        assertEquals("6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b", topic.id)
        assertEquals("5a4b3c2d-1e0f-4a5b-8c9d-0e1f2a3b4c5d", topic.root)
    }
}
