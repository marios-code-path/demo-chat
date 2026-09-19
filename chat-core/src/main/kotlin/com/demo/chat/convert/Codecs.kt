package com.demo.chat.convert

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.internal.throwMissingFieldException

import java.util.*

interface Converter<F, E> {//} : org.springframework.core.convert.converter.Converter<F, E> {
    fun convert(source: F): E
}

/**
 * Reads one JSON node as a plain value.
 *
 * The result is nullable, and a null node answers with null.
 *
 * An explicit null and an absent field are different facts. A null node is a
 * value the writer chose. A missing node is a field the document never held,
 * and that stays an error. An earlier version had no null branch, so a null
 * node fell through to `asText()` and became the four character string
 * `"null"`. A typed field then failed to bind, and a String field took that
 * text in silence.
 */
object JsonNodeToAnyConverter : Converter<JsonNode, Any?> {
    override fun convert(record: JsonNode): Any? {
        return when (record.nodeType) {
            JsonNodeType.MISSING -> throw Exception("Missing field")
            JsonNodeType.NULL -> null
            // The three scalar rules live in NodeValueRules, because the
            // Jackson 3 converter must decide the same way. See CHAT-qwmjrixq.
            JsonNodeType.BINARY -> NodeValueRules.binary(record.binaryValue())
            JsonNodeType.NUMBER -> NodeValueRules.number(record.asDouble()) { record.asLong() }
            JsonNodeType.STRING -> NodeValueRules.text(record.asText())
            JsonNodeType.BOOLEAN -> record.asBoolean()
            JsonNodeType.OBJECT -> record.fields().asSequence().associate { it.key to convert(it.value) }
            JsonNodeType.ARRAY -> record.elements().asSequence().map { convert(it) }.toList()
            else -> record.asText()
        }
    }
}