package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.EntryEncoding
import com.demo.chat.index.lucene.storage.IndexEntry
import com.demo.chat.index.lucene.storage.IndexFields
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EntryEncodingTests {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /** Format 1 for key "7" with a repeated field name. The fields keep encoder order. */
    @Test
    fun `format 1 bytes are pinned`() {
        val bytes = EntryEncoding.encode("7", listOf("name" to "a", "name" to "b"))
        assertThat(hex(bytes)).isEqualTo(
            "00000001" + "00000001" + "37" + "00000002" +
                "00000004" + "6e616d65" + "00000001" + "61" +
                "00000004" + "6e616d65" + "00000001" + "62"
        )
    }

    /** The length prefix counts UTF-8 bytes. "é" is two bytes. */
    @Test
    fun `a multi-byte value carries its byte length`() {
        val bytes = EntryEncoding.encode("1", listOf("n" to "é"))
        assertThat(hex(bytes)).endsWith("00000001" + "6e" + "00000002" + "c3a9")
    }

    @Test
    fun `field order changes the bytes`() {
        val ab = EntryEncoding.encode("1", listOf("a" to "x", "b" to "y"))
        val ba = EntryEncoding.encode("1", listOf("b" to "y", "a" to "x"))
        assertThat(ab).isNotEqualTo(ba)
    }

    @Test
    fun `the entry document holds the reserved fields`() {
        val entry = IndexEntry.of("42", listOf("handle" to "h"))
        assertThat(entry.document.get(IndexFields.STORED_KEY)).isEqualTo("42")
        assertThat(entry.document.getBinaryValue(IndexFields.ENTRY).bytes).isEqualTo(entry.bytes)
        assertThat(entry.document.getField(IndexFields.EXACT_KEY).stringValue()).isEqualTo("42")
    }

    @Test
    fun `an encoder field cannot use a reserved name`() {
        assertThatThrownBy { IndexEntry.of("1", listOf(IndexFields.ENTRY to "x")) }
            .hasMessageContaining("'_enc'")
        assertThatThrownBy { IndexEntry.of("1", listOf(IndexFields.EXACT_KEY to "x")) }
            .hasMessageContaining("'_key'")
    }
}
