package com.demo.chat.codec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

interface Codec<F, E> {
    fun decode(record: F): E
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