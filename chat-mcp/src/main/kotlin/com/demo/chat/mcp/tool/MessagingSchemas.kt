package com.demo.chat.mcp.tool

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.*

private fun stringShape(): JsonObject = buildJsonObject { put("type", "string") }

private fun objectShape(properties: JsonObject): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", properties)
    put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
    put("additionalProperties", false)
}

private fun keyShape(): JsonObject = objectShape(buildJsonObject {
    put("id", stringShape())
    put("root", stringShape())
})

private fun timeShape(): JsonObject = buildJsonObject {
    put("type", "string")
    put("format", "date-time")
}

private fun counterShape(): JsonObject = buildJsonObject {
    put("type", "integer")
    put("minimum", 0)
}

private fun enumShape(values: List<String>): JsonObject = buildJsonObject {
    put("type", "string")
    put("enum", JsonArray(values.map(::JsonPrimitive)))
}

private fun receiptShape(): JsonObject = objectShape(buildJsonObject {
    put("commandId", stringShape())
    put("messageKey", keyShape())
})

private fun backendsShape(): JsonObject = buildJsonObject {
    val backend = objectShape(buildJsonObject {
        put("state", enumShape(listOf("PENDING", "SUCCEEDED", "FAILED", "UNCERTAIN")))
        put("attempts", counterShape())
        put("nextRecoveryAt", buildJsonObject {
            put("anyOf", JsonArray(listOf(timeShape(), buildJsonObject { put("type", "null") })))
        })
    })
    put("type", "object")
    put("properties", buildJsonObject {
        for (name in listOf("PERSISTENCE", "INDEX", "VECTOR", "PUBSUB")) put(name, backend)
    })
    put("additionalProperties", false)
}

private fun messageShape(): JsonObject = objectShape(buildJsonObject {
    put("messageKey", keyShape())
    put("senderId", stringShape())
    put("topicId", stringShape())
    put("text", stringShape())
    put("timestamp", timeShape())
})

internal fun messagingInput(arguments: List<String>): ToolSchema = ToolSchema(
    properties = buildJsonObject { arguments.forEach { put(it, stringShape()) } },
    required = arguments,
)

internal fun messagingOutput(name: String): ToolSchema {
    val properties = buildJsonObject {
        when (name) {
            "chat_list_messages" -> put("messages", buildJsonObject {
                put("type", "array")
                put("items", messageShape())
            })
            "chat_get_message" -> put("message", messageShape())
            "chat_send_message" -> {
                put("receipt", receiptShape())
                put("outcome", enumShape(listOf("ACCEPTED", "PENDING", "COMPLETED", "INCOMPLETE")))
                put("backends", backendsShape())
            }
            "chat_get_command_status" -> {
                put("commandId", stringShape())
                put("requestId", stringShape())
                put("receipt", receiptShape())
                put("backends", backendsShape())
                put("version", counterShape())
            }
            else -> error("the messaging tool name is not supported")
        }
    }
    return ToolSchema(properties = properties, required = properties.keys.toList())
}
