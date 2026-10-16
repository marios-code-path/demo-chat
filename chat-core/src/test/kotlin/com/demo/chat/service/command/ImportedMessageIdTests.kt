package com.demo.chat.service.command

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.UUIDUtil
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ImportedMessageIdTests {
    private val firstRoom = UUID.fromString("12345678-1234-4234-8234-123456789abc")
    private val secondRoom = UUID.fromString("87654321-4321-4321-8321-cba987654321")
    private val source = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")

    @Test
    fun `the source id derives the same name-based UUID on a rerun`() {
        val sourceId = "$firstRoom:$source"
        val expected = UUID.nameUUIDFromBytes(sourceId.encodeToByteArray())

        assertThat(ImportedMessageId.derive(sourceId, firstRoom)).isEqualTo(expected)
        assertThat(ImportedMessageId.derive(sourceId, firstRoom)).isEqualTo(expected)
        assertThat(expected.version()).isEqualTo(3)
    }

    @Test
    fun `one source message has a distinct id in each room`() {
        val first = ImportedMessageId.derive("$firstRoom:$source", firstRoom)
        val second = ImportedMessageId.derive("$secondRoom:$source", secondRoom)

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `an unscoped source id is refused`() {
        assertThatThrownBy { ImportedMessageId.derive(source.toString(), firstRoom) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("source id")
    }

    @Test
    fun `a source id for another room is refused`() {
        assertThatThrownBy { ImportedMessageId.derive("$secondRoom:$source", firstRoom) }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("source id")
    }

    @Test
    fun `a UUID key type returns the derived id`() {
        val sourceId = "$firstRoom:$source"

        assertThat(ImportedMessageId.deriveFor(UUIDUtil(), firstRoom, sourceId))
            .isEqualTo(UUID.nameUUIDFromBytes(sourceId.encodeToByteArray()))
    }

    @Test
    fun `a long key type is refused with the type and derivation`() {
        assertThatThrownBy { ImportedMessageId.deriveFor(LongUtil(), 5L, "5:$source") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("Long")
            .hasMessageContaining("name-based UUID")
    }
}
