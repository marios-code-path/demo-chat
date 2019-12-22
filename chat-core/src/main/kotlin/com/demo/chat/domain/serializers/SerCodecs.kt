package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.demo.chat.domain.ChatException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

open class DelegatingCodec<F, E> : Codec<F, E> {
    inline fun <reified E> reifyMyT(t: F): E {
        return "" as E
    }

    override fun decode(record: F): E {
        return when(record) {
            is JsonNode -> JsonNodeAnyCodec.decode(record) as E
            else -> throw ChatException("Cannot Decode other than JsonNode")
        }
    }
}
object JsonNodeStringCodec : Codec<JsonNode, String> {
    override fun decode(record: JsonNode): String {
        return when (record.nodeType) {
            JsonNodeType.NUMBER -> record.asLong().toString()
            else -> record.asText()
        }
    }
}

object JsonNodeAnyCodec : Codec<JsonNode, Any> {
    override fun decode(record: JsonNode): Any {
        return when (record.nodeType) {
            JsonNodeType.NUMBER -> record.asLong()
            JsonNodeType.STRING -> {
                try {
                    UUID.fromString(record.asText())
                } catch (e: Exception) {
                    record.asText()
                }
            }
            else -> record.asText()
        }
    }
}