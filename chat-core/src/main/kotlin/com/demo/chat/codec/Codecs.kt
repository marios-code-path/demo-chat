package com.demo.chat.codec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import java.util.*

interface Codec<F, E> {
    fun decode(record: F): E
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