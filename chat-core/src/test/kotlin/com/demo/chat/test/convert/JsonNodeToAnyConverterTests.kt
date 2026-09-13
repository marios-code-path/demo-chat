package com.demo.chat.test.convert

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * How the converter reads a JSON null.
 *
 * An explicit null and an absent field are different facts. A null node is a
 * value the writer chose. A missing node is a field the document never held.
 *
 * The converter had no null branch, so a null node fell through to `asText()`
 * and became the four character string `"null"`. A typed field then failed to
 * bind, and a String field took that text in silence.
 */
class JsonNodeToAnyConverterTests {

    private val mapper = ObjectMapper()

    @Suppress("UNCHECKED_CAST")
    private fun objectOf(json: String): Map<String, Any?> =
        JsonNodeToAnyConverter.convert(mapper.readTree(json)) as Map<String, Any?>

    @Test
    fun `a null object field stays null`() {
        val converted = objectOf("""{"finishedAt":null,"nodeId":7}""")

        Assertions.assertThat(converted["finishedAt"]).isNull()
        Assertions.assertThat(converted["nodeId"]).isEqualTo(7L)
    }

    // The quiet half of the defect. A String field takes any text, so a wrong
    // value here never raises anything. It just becomes wrong.
    @Test
    fun `a null string field never becomes the text null`() {
        val converted = objectOf("""{"failureSummary":null}""")

        Assertions.assertThat(converted["failureSummary"]).isNull()
        // The assertion that bites. The old branch produced this exact text.
        Assertions.assertThat(converted["failureSummary"]).isNotEqualTo("null")
    }

    @Test
    fun `a null array element stays null`() {
        val converted = objectOf("""{"items":["a",null,"b"]}""")

        @Suppress("UNCHECKED_CAST")
        val items = converted["items"] as List<Any?>
        Assertions.assertThat(items).containsExactly("a", null, "b")
    }

    @Test
    fun `a nested null stays null`() {
        val converted = objectOf("""{"outer":{"inner":null}}""")

        @Suppress("UNCHECKED_CAST")
        val outer = converted["outer"] as Map<String, Any?>
        Assertions.assertThat(outer).containsKey("inner")
        Assertions.assertThat(outer["inner"]).isNull()
    }

    // A field the document never held is not a value. It stays an error, so
    // the null branch does not quietly cover a missing one.
    @Test
    fun `a missing node still fails`() {
        val missing = mapper.readTree("""{"a":1}""").path("absent")

        Assertions
            .assertThatThrownBy { JsonNodeToAnyConverter.convert(missing) }
            .hasMessageContaining("Missing field")
    }

    @Test
    fun `an explicit null and an absent field differ`() {
        val converted = objectOf("""{"present":null}""")

        Assertions.assertThat(converted).containsKey("present")
        Assertions.assertThat(converted["present"]).isNull()
        Assertions.assertThat(converted).doesNotContainKey("absent")
    }
}
