package com.demo.chat.test.index

import com.demo.chat.domain.ChatException
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.TypedKeyValueIndexFields
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class TypedKeyValueIndexFieldsTests {
    private data class Sample(val name: String, val size: Int)

    private val sampleEntry = KeyValueIndexFieldsEntry(
        Sample::class.java,
        KeyValueIndexFields { value ->
            val sample = value as Sample
            listOf(Pair("name", sample.name), Pair("size", sample.size.toString()))
        }
    )

    @Test
    fun `a registered type returns its fields`() {
        val fields = TypedKeyValueIndexFields(listOf(sampleEntry))

        Assertions.assertThat(fields.fieldsOf(Sample("one", 2)))
            .containsExactly(Pair("name", "one"), Pair("size", "2"))
    }

    @Test
    fun `an unregistered type names the registered types`() {
        val fields = TypedKeyValueIndexFields(listOf(sampleEntry))

        Assertions.assertThatThrownBy { fields.fieldsOf("a string value") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("java.lang.String")
            .hasMessageContaining(Sample::class.java.name)
    }

    @Test
    fun `an empty registry says that nothing is registered`() {
        val fields = TypedKeyValueIndexFields(emptyList())

        Assertions.assertThatThrownBy { fields.fieldsOf("a string value") }
            .isInstanceOf(ChatException::class.java)
            .hasMessageContaining("No key value index fields are registered at all")
    }
}
