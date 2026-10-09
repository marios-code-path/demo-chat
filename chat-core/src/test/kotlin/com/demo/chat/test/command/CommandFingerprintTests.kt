package com.demo.chat.test.command

import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.InvalidRequestIdException
import com.demo.chat.service.command.CommandFingerprint
import com.demo.chat.service.command.RequestIds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/** The golden values were computed with an independent Perl encoder on 2026-10-07. */
class CommandFingerprintTests {
    private val op = CommandOperation.RECORD_MESSAGE

    @Test
    fun `a Long sender and destination give the golden value`() {
        assertThat(CommandFingerprint.of(op, "10", "100", "hello"))
            .isEqualTo("3d0ac229ec15f3e7d52cea0f5b49a615aae9ef406a503cea25c132933efec7ad")
    }

    @Test
    fun `UUID ids give the golden value`() {
        assertThat(
            CommandFingerprint.of(op, "123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174001", "hello")
        ).isEqualTo("8c43a7a92b7ff67570bed080836504c4cda6c9e5d2378b45d8ec527e4cb6f390")
    }

    @Test
    fun `content encodes as UTF-8`() {
        assertThat(CommandFingerprint.of(op, "10", "100", "héllo"))
            .isEqualTo("5b4d05ac2ae5ac4bd7cecffb20c5b680aab709a629c31d1cf1c5634238ce115e")
    }

    @Test
    fun `swapping sender and destination changes the value`() {
        assertThat(CommandFingerprint.of(op, "100", "10", "hello"))
            .isEqualTo("8e4209aaaf2e65ef16c2a73d3e7d58217dd2f028c5da1e8cea99f2da48c185ad")
    }

    @Test
    fun `a change in each field changes the value`() {
        val base = CommandFingerprint.of(op, "10", "100", "hello")
        assertThat(CommandFingerprint.of(op, "11", "100", "hello")).isNotEqualTo(base)
        assertThat(CommandFingerprint.of(op, "10", "101", "hello")).isNotEqualTo(base)
        assertThat(CommandFingerprint.of(op, "10", "100", "hello!")).isNotEqualTo(base)
    }

    @Test
    fun `an import fingerprint includes time and publication`() {
        val base = CommandFingerprint.of(
            CommandOperation.IMPORT_MESSAGE, "10", "100", "hello", Instant.parse("2026-10-08T10:00:00.001Z"), false,
        )
        assertThat(
            CommandFingerprint.of(
                CommandOperation.IMPORT_MESSAGE, "10", "100", "hello", Instant.parse("2026-10-08T10:00:00.002Z"), false,
            )
        ).isNotEqualTo(base)
        assertThat(
            CommandFingerprint.of(
                CommandOperation.IMPORT_MESSAGE, "10", "100", "hello", Instant.parse("2026-10-08T10:00:00.001Z"), true,
            )
        ).isNotEqualTo(base)
    }

    @Test
    fun `a non-text value is refused`() {
        assertThatThrownBy { CommandFingerprint.of(op, "10", "100", 42) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("String")
    }

    @Test
    fun `a request ID of visible ASCII up to 128 characters passes`() {
        assertThat(RequestIds.requireValid("a1-B2_c3.~!")).isEqualTo("a1-B2_c3.~!")
        assertThat(RequestIds.requireValid("x".repeat(128))).hasSize(128)
    }

    @Test
    fun `an empty, long, spaced, or non-ASCII request ID is refused`() {
        listOf(null, "", "x".repeat(129), "has space", "café").forEach { bad ->
            assertThatThrownBy { RequestIds.requireValid(bad) }.isInstanceOf(InvalidRequestIdException::class.java)
        }
    }
}
