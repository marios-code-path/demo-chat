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
}
