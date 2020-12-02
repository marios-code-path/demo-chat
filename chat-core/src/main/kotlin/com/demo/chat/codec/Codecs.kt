package com.demo.chat.codec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.util.*

fun interface Decoder<F, E> {
    fun decode(record: F): E
}

// TODO IN PRODUCTION: Hrmmm.. CBOR configuration needs to be careful -
// TODO CBOR encoded messages in a json body is what we are attempting
// TODO to alleviate.
object JsonNodeAnyDecoder : Decoder<JsonNode, Any> {
    override fun decode(record: JsonNode): Any {
        return when (record.nodeType) {
            JsonNodeType.BINARY -> {
                val f = CBORFactory()
                val mapper = ObjectMapper(f)
                val cborData = mapper.writeValueAsBytes(record.binaryValue())

                val data = mapper.readValue(cborData, UUID::class.java)
                data
            }
            JsonNodeType.NUMBER -> record.asLong()
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