package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.MessagingClient
import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.config.parseIdText
import kotlinx.serialization.json.*

class MessagingInputException : RuntimeException("the tool input is not valid")
class MessagingUnavailableException : RuntimeException("the message is outside the configured rooms")

class MessagingToolService(private val config: AdapterConfig, private val client: MessagingClient) {
    fun listMessages(topicId: String): JsonObject {
        val messages = client.readHistory(allowedTopic(topicId))
        messages.forEach(::requireRoom)
        return buildJsonObject { put("messages", JsonArray(messages)) }
    }

    fun getMessage(messageId: String): JsonObject {
        val message = client.readMessage(objectId(messageId))
        requireRoom(message)
        return buildJsonObject { put("message", message) }
    }

    fun sendMessage(topicId: String, text: String, requestId: String): JsonObject {
        if (!validVisibleId(requestId) || !validMessageText(text)) throw MessagingInputException()
        return client.submit(allowedTopic(topicId), text, requestId)
    }

    fun commandStatus(commandId: String): JsonObject {
        if (!validVisibleId(commandId)) throw MessagingInputException()
        return client.status(commandId)
    }

    private fun objectId(text: String): String = try {
        parseIdText(text, config.keyType).text
    } catch (failure: ConfigException) {
        throw MessagingInputException()
    }

    private fun allowedTopic(text: String): String = objectId(text).also { topic ->
        if (config.topicIds.none { it.text == topic }) throw MessagingUnavailableException()
    }

    private fun requireRoom(message: JsonObject) {
        val room = message.getValue("topicId").jsonPrimitive.content
        if (config.topicIds.none { it.text == room }) throw MessagingUnavailableException()
    }
}

internal fun validVisibleId(value: String): Boolean =
    value.length in 1..128 && value.all { it in '!'..'~' }

internal fun validMessageText(value: String): Boolean =
    value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size in 1..16_384
