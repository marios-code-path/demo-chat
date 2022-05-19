package com.demo.chat.codec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import org.springframework.core.convert.converter.Converter

import java.util.*

@Deprecated("Use KeyConverter (Spring Converter) Instead")
fun interface Decoder<F, E> {
    fun decode(record: F): E
}

class KeyConverter<T, L>(val decoder: Decoder<JsonNode, Any>) : Converter<JsonNode, Any> {
    override fun convert(record: JsonNode): Any = decoder::decode
}

// TODO IN PRODUCTION: Hrmmm.. CBOR configuration needs to be careful -
// TODO CBOR encoded messages in a json body is what we are attempting
// TODO to alleviate.
object JsonKeyDecoder : Decoder<JsonNode, Any> {
    override fun decode(record: JsonNode): Any {
        return when (record.nodeType) {
            JsonNodeType.BINARY -> {
                val f = CBORFactory()
                val mapper = ObjectMapper(f)
                val cborData = mapper.writeValueAsBytes(record.binaryValue())

                val data = mapper.readValue(cborData, UUID::class.java)
                data
            }
            JsonNodeType.NUMBER -> record.asLong() // TODO this should check for Double too
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