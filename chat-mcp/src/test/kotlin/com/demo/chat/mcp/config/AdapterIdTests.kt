package com.demo.chat.mcp.config

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The identity rules. One test per accept rule and one per rejection rule. */
class AdapterIdTests {
    // --- accept rules ---

    @Test
    fun `a canonical Long id is accepted`() {
        assertEquals(LongId(7L), parseIdText("7", KeyType.LONG))
    }

    @Test
    fun `a negative canonical Long id is accepted`() {
        // The rejection list is closed and it does not name a negative value.
        // So a negative id is canonical and it is accepted. See the plan.
        assertEquals(LongId(-7L), parseIdText("-7", KeyType.LONG))
    }

    @Test
    fun `a canonical uuid id is accepted`() {
        val text = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(UuidId(java.util.UUID.fromString(text)), parseIdText(text, KeyType.UUID))
    }

    @Test
    fun `a Long above two to the fifty three round-trips without loss`() {
        // 2^53 + 1 is the first Long that a double cannot hold.
        val text = "9007199254740993"
        val id = parseIdText(text, KeyType.LONG) as LongId
        assertEquals(9007199254740993L, id.value)
        assertEquals(text, id.text)
        // The same value through a double loses the last digit. That loss is
        // the reason the id travels as text.
        assertNotEquals(id.value, id.value.toDouble().toLong())
    }

    @Test
    fun `the largest Long round-trips without loss`() {
        val text = Long.MAX_VALUE.toString()
        assertEquals(Long.MAX_VALUE, (parseIdText(text, KeyType.LONG) as LongId).value)
    }

    @Test
    fun `a JSON string id is accepted`() {
        assertEquals(LongId(7L), parseIdElement(JsonPrimitive("7"), KeyType.LONG))
    }

    // --- Long rejection rules ---

    @Test
    fun `a zero Long id is refused`() {
        val failure = assertThrows(ConfigException::class.java) { parseIdText("0", KeyType.LONG) }
        assertTrue(failure.message!!.contains("zero"), failure.message)
    }

    @Test
    fun `a fractional Long id is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("7.5", KeyType.LONG) }
    }

    @Test
    fun `an exponent Long id is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("7e3", KeyType.LONG) }
    }

    @Test
    fun `a Long id with surrounding whitespace is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText(" 7", KeyType.LONG) }
        assertThrows(ConfigException::class.java) { parseIdText("7 ", KeyType.LONG) }
    }

    @Test
    fun `a Long id with a leading plus sign is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("+7", KeyType.LONG) }
    }

    @Test
    fun `a Long id with a leading zero is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("07", KeyType.LONG) }
    }

    @Test
    fun `an empty Long id is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("", KeyType.LONG) }
    }

    @Test
    fun `an overflowing Long id is refused`() {
        val above = "9223372036854775808"
        val failure = assertThrows(ConfigException::class.java) { parseIdText(above, KeyType.LONG) }
        assertTrue(failure.message!!.contains("Long range"), failure.message)
    }

    @Test
    fun `a Long id under the Long range is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("-9223372036854775809", KeyType.LONG) }
    }

    // --- uuid rejection rules ---

    @Test
    fun `an uppercase uuid id is refused`() {
        assertThrows(ConfigException::class.java) {
            parseIdText("550E8400-E29B-41D4-A716-446655440000", KeyType.UUID)
        }
    }

    @Test
    fun `a shortened uuid group is refused`() {
        // UUID.fromString reads a short group. The round-trip check refuses it.
        assertThrows(ConfigException::class.java) {
            parseIdText("550e8400-e29b-41d4-a716-44665544000", KeyType.UUID)
        }
    }

    @Test
    fun `a braced uuid id is refused`() {
        assertThrows(ConfigException::class.java) {
            parseIdText("{550e8400-e29b-41d4-a716-446655440000}", KeyType.UUID)
        }
    }

    @Test
    fun `the zero uuid is refused`() {
        val failure = assertThrows(ConfigException::class.java) {
            parseIdText("00000000-0000-0000-0000-000000000000", KeyType.UUID)
        }
        assertTrue(failure.message!!.contains("zero uuid"), failure.message)
    }

    @Test
    fun `text that is not a uuid is refused`() {
        assertThrows(ConfigException::class.java) { parseIdText("7", KeyType.UUID) }
    }

    // --- the JSON boundary ---

    @Test
    fun `a numeric JSON id is refused before any backend call`() {
        val failure = assertThrows(ConfigException::class.java) {
            parseIdElement(JsonPrimitive(7), KeyType.LONG)
        }
        assertTrue(failure.message!!.contains("JSON string"), failure.message)
    }

    @Test
    fun `a fractional JSON id is refused before any backend call`() {
        assertThrows(ConfigException::class.java) {
            parseIdElement(JsonPrimitive(7.5), KeyType.LONG)
        }
    }

    @Test
    fun `a boolean JSON id is refused`() {
        assertThrows(ConfigException::class.java) {
            parseIdElement(JsonPrimitive(true), KeyType.LONG)
        }
    }

    @Test
    fun `a JSON array id is refused`() {
        assertThrows(ConfigException::class.java) {
            parseIdElement(JsonArray(emptyList()), KeyType.LONG)
        }
    }

    @Test
    fun `a numeric JSON id is refused for the uuid key type too`() {
        assertThrows(ConfigException::class.java) {
            parseIdElement(JsonPrimitive(7), KeyType.UUID)
        }
    }

    // --- the key type rule ---

    @Test
    fun `the two key type names are accepted`() {
        assertEquals(KeyType.LONG, KeyType.of("long"))
        assertEquals(KeyType.UUID, KeyType.of("uuid"))
    }

    @Test
    fun `another key type name is refused`() {
        assertThrows(ConfigException::class.java) { KeyType.of("LONG") }
        assertThrows(ConfigException::class.java) { KeyType.of("int") }
        assertThrows(ConfigException::class.java) { KeyType.of("") }
    }

    @Test
    fun `an absent key type is refused`() {
        assertThrows(ConfigException::class.java) { KeyType.of(null) }
    }
}