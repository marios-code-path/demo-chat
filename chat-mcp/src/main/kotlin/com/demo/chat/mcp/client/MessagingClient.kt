package com.demo.chat.mcp.client

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.config.readCredentialFile
import kotlinx.serialization.json.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MessagingClient(private val config: AdapterConfig, private val http: BackendHttp) {
    fun readHistory(topicId: String): List<JsonObject> =
        decodeHistory(http.history(route("list", topicId), credential()), config.keyType)

    fun readMessage(messageId: String): JsonObject =
        decodeMessage(http.get(route("id", messageId), credential()), config.keyType)

    fun submit(topicId: String, text: String, requestId: String): JsonObject {
        val response = http.submit(route("submit", topicId), credential(), requestId, text)
        return try {
            decodeSend(response, config.keyType)
        } catch (failure: ClientException) {
            if (failure.status in setOf(400, 409, 401, 403, 404)) throw failure
            throw SubmissionUnknownException(requestId, response.status)
        }
    }

    fun status(commandId: String): JsonObject =
        decodeStatus(http.get(route("command", commandId), credential()), config.keyType, commandId)

    private fun credential(): String = usableCredential(readCredentialFile(config.credentialFile))

    private fun route(operation: String, id: String): URI =
        URI(config.backendBaseUrl.toASCIIString() + "/message/$operation/" + URLEncoder.encode(id, StandardCharsets.UTF_8))
}
