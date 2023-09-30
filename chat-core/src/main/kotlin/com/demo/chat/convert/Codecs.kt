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

object JsonNodeToAnyConverter : Converter<JsonNode, Any> {
    override fun convert(record: JsonNode): Any {
        return when (record.nodeType) {
            JsonNodeType.MISSING -> throw Exception("Missing field")
            JsonNodeType.BINARY -> {
                val cborFactory = CBORFactory()
                val mapper = ObjectMapper(cborFactory)
                val cborData = mapper.writeValueAsBytes(record.binaryValue())

                try {
                    val data = mapper.readValue(cborData, UUID::class.java)
                    data
                } catch (e: Exception) {
                    cborData.toString()
                }
            }
            JsonNodeType.NUMBER -> {
                val variable = record.asDouble()
                if (variable % 1 == 0.0)
                    record.asLong()
                else
                    variable
            }
            JsonNodeType.STRING -> {
                try {
                    UUID.fromString(record.asText())
                } catch (e: Exception) {
                    record.asText()
                }
            }
            JsonNodeType.BOOLEAN -> record.asBoolean()
            JsonNodeType.OBJECT -> record.fields().asSequence().associate { it.key to convert(it.value) }
            JsonNodeType.ARRAY -> record.elements().asSequence().map { convert(it) }.toList()
            else -> record.asText()
        }
    }
}