package com.demo.chat.test

import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.UUIDUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A decoded id converts to the key type. JSON decodes a small Long as an
 * Integer and a UUID as a String. See `CHAT-avduuqwp`, T4 review correction 1.
 */
class TypeUtilAssignTests {
    @Test
    fun `a Long accepts an Integer, a Long and its text`() {
        assertThat(LongUtil().assignFrom(42)).isEqualTo(42L)
        assertThat(LongUtil().assignFrom(42L)).isEqualTo(42L)
        assertThat(LongUtil().assignFrom("42")).isEqualTo(42L)
    }

    @Test
    fun `a UUID accepts a UUID and its text`() {
        val id = UUID.randomUUID()

        assertThat(UUIDUtil().assignFrom(id)).isEqualTo(id)
        assertThat(UUIDUtil().assignFrom(id.toString())).isEqualTo(id)
    }

    /** A CBOR answer for an untyped read is 16 bytes. It was the zero UUID before. See `CHAT-siaetqtr`. */
    @Test
    fun `a UUID accepts the 16 bytes that CBOR writes`() {
        val id = UUID.randomUUID()
        val bytes = java.nio.ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()

        assertThat(UUIDUtil().assignFrom(bytes)).isEqualTo(id)
    }

    @Test
    fun `malformed text fails, so a route can answer an unknown id`() {
        assertThatThrownBy { LongUtil().assignFrom("abc") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { UUIDUtil().assignFrom("abc") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
