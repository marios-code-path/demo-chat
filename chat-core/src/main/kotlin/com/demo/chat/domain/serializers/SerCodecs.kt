package com.demo.chat.domain.serializers

import com.demo.chat.codec.Codec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

object JsonNodeCodec : Codec<JsonNode, Any> {
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