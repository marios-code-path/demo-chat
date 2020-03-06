package com.demo.chat.codec

import com.demo.chat.domain.Key
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import java.util.*

interface Codec<F, E> {
    fun decode(record: F): E
}

// TODO IN PRODUCTION: We are getting Base64-encoded?!?!?
object JsonNodeAnyCodec : Codec<JsonNode, Any> {
    override fun decode(record: JsonNode): Any {
        println("Record: ${record}")
        println("PROPERTY: ${record.nodeType}")
        return when (record.nodeType) {
            JsonNodeType.BINARY -> {
                val f = CBORFactory()
                val mapper = ObjectMapper(f)
                val cborData = mapper.writeValueAsBytes(record.binaryValue())
                println("CBOR: $cborData")

                 val data = mapper.readValue(cborData, UUID::class.java)
                println("DATA: $data")

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

class EmptyStringCodec: Codec<Unit, String> {
    override fun decode(record: Unit): String {
        return ""
    }
}

class EmptyNumberCodec: Codec<Unit, Number> {
    override fun decode(record: Unit): Number {
        return 0
    }
}

class EmptyUUIDCodec: Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUID(0L, 0L)
    }
}