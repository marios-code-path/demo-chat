package com.demo.chat.test

import com.demo.chat.domain.ExactIds
import com.demo.chat.domain.KeyInputException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

/**
 * A caller id converts exactly or fails with an input error. See
 * `CHAT-avduuqwp`, T4 review.
 */
class ExactIdsTests {
    private fun refusedLong(value: Any) =
        assertThatThrownBy { ExactIds.long(value) }.isInstanceOf(KeyInputException::class.java)

    @Test
    fun `an integer converts to a Long`() {
        assertThat(ExactIds.long(42)).isEqualTo(42L)
        assertThat(ExactIds.long(42L)).isEqualTo(42L)
        assertThat(ExactIds.long(BigInteger.valueOf(42))).isEqualTo(42L)
        assertThat(ExactIds.long("42")).isEqualTo(42L)
        assertThat(ExactIds.long("-7")).isEqualTo(-7L)
        assertThat(ExactIds.long(Long.MAX_VALUE.toString())).isEqualTo(Long.MAX_VALUE)
    }

    @Test
    fun `a fraction is refused, even with a zero fraction`() {
        refusedLong(42.9)
        refusedLong(42.0)
        refusedLong(42.9f)
        refusedLong(BigDecimal("42"))
        refusedLong("42.9")
    }

    @Test
    fun `a value outside the Long range is refused rather than wrapped`() {
        refusedLong(BigInteger("18446744073709551658"))
        refusedLong("18446744073709551658")
        refusedLong(BigInteger("9223372036854775808"))
    }

    @Test
    fun `an unsupported shape is refused`() {
        refusedLong(true)
        refusedLong(mapOf("id" to 42))
        refusedLong(listOf(42))
        refusedLong(" 42")
        refusedLong("42x")
        refusedLong("")
    }

    @Test
    fun `a UUID converts from a UUID and from its canonical text`() {
        val id = UUID.randomUUID()

        assertThat(ExactIds.uuid(id)).isEqualTo(id)
        assertThat(ExactIds.uuid(id.toString())).isEqualTo(id)
    }

    @Test
    fun `a UUID refuses text that is not canonical and every number`() {
        assertThatThrownBy { ExactIds.uuid("1-1-1-1-1") }.isInstanceOf(KeyInputException::class.java)
        assertThatThrownBy { ExactIds.uuid(42) }.isInstanceOf(KeyInputException::class.java)
        assertThatThrownBy { ExactIds.uuid(mapOf("id" to "x")) }.isInstanceOf(KeyInputException::class.java)
    }

    /** CBOR writes a UUID as 16 bytes, most significant half first. See `CHAT-siaetqtr`. */
    @Test
    fun `a UUID converts from the 16 bytes that CBOR writes`() {
        val id = UUID.randomUUID()
        val bytes = java.nio.ByteBuffer.allocate(16).putLong(id.mostSignificantBits).putLong(id.leastSignificantBits).array()

        assertThat(ExactIds.uuid(bytes)).isEqualTo(id)
    }

    @Test
    fun `a UUID refuses bytes of any other length`() {
        for (length in listOf(0, 8, 15, 17)) {
            assertThatThrownBy { ExactIds.uuid(ByteArray(length)) }
                .isInstanceOf(KeyInputException::class.java)
                .hasMessage("A key id of $length bytes is not a UUID.")
        }
    }

    @Test
    fun `a Long still refuses bytes`() {
        assertThatThrownBy { ExactIds.long(ByteArray(8)) }
            .isInstanceOf(KeyInputException::class.java)
            .hasMessage("A key id cannot be a byte[].")
    }
}
