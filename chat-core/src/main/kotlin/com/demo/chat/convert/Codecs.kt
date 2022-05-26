package com.demo.chat.convert

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.cbor.CBORFactory

import java.util.*

fun interface Converter<F, E> {
    fun convert(record: F): E
}

object JsonNodeToAnyConverter : Converter<JsonNode, Any> {
    override fun convert(record: JsonNode): Any {
        return when (record.nodeType) {
            JsonNodeType.BINARY -> {
                val f = CBORFactory()
                val mapper = ObjectMapper(f)
                val cborData = mapper.writeValueAsBytes(record.binaryValue())

                val data = mapper.readValue(cborData, UUID::class.java)
                data
            }
            JsonNodeType.NUMBER ->
            {
                val variable = record.asDouble()
                if (variable % 1 == 0.0)
                    record.asLong()
                else
                    variable
            }
            else -> {
                try {
                    UUID.fromString(record.asText())
                } catch (e: Exception) {
                    record.asText()
                }
            }
        }
    }
}