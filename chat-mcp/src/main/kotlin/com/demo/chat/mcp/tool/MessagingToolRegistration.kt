package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.SubmissionUnknownException
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.error.ToolError
import com.demo.chat.mcp.error.ToolErrorCode
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException

fun registerMessagingTools(server: Server, service: MessagingToolService, enableSend: Boolean) {
    fun register(name: String, description: String, arguments: List<String>, send: Boolean = false, body: (Map<String, String>) -> JsonObject) {
        server.addTool(Tool(
            name = name,
            description = description,
            inputSchema = messagingInput(arguments),
            outputSchema = messagingOutput(name),
            annotations = ToolAnnotations(
                readOnlyHint = !send, destructiveHint = false, idempotentHint = true, openWorldHint = true,
            ),
        )) { request -> messagingAnswer(name, request, arguments.toSet(), send, body) }
    }
    register("chat_list_messages", "Read the stored messages of one configured room.", listOf("topicId")) {
        service.listMessages(it.getValue("topicId"))
    }
    register("chat_get_message", "Read one message in a configured room.", listOf("messageId")) {
        service.getMessage(it.getValue("messageId"))
    }
    register("chat_get_command_status", "Read backend progress for a command owned by this agent.", listOf("commandId")) {
        service.commandStatus(it.getValue("commandId"))
    }
    if (enableSend) register("chat_send_message", "Submit a message with a caller-selected request ID.", listOf("topicId", "text", "requestId"), true) {
        service.sendMessage(it.getValue("topicId"), it.getValue("text"), it.getValue("requestId"))
    }
}

internal suspend fun messagingAnswer(
    toolName: String,
    request: CallToolRequest,
    required: Set<String>,
    send: Boolean,
    body: (Map<String, String>) -> JsonObject,
): CallToolResult {
    val call = nextCallNumber()
    val started = System.nanoTime()
    var requestId: String? = null

    fun failure(error: ToolError, status: Int? = null, extra: JsonObject? = null, structured: JsonObject? = null): CallToolResult {
        report(toolName, call, started, error.code.name, status, "refused: ${error.message}")
        return CallToolResult(
            content = listOf(TextContent(error.message)),
            isError = true,
            meta = JsonObject(error.toMeta() + extra.orEmpty()),
            structuredContent = structured,
        )
    }

    return try {
        val supplied = request.arguments ?: JsonObject(emptyMap())
        if (supplied.keys != required) throw MessagingInputException()
        val arguments = supplied.mapValues { (_, value) ->
            val primitive = value as? JsonPrimitive ?: throw MessagingInputException()
            if (!primitive.isString || value == JsonNull) throw MessagingInputException()
            primitive.content
        }
        if (send) {
            val id = arguments.getValue("requestId")
            if (!validVisibleId(id)) throw MessagingInputException()
            requestId = id
        }
        val structured = withContext(Dispatchers.IO) { body(arguments) }
        val outcome = if (send) structured.getValue("outcome").jsonPrimitive.content else null
        val status = when (outcome) {
            "COMPLETED" -> 201
            "PENDING", "ACCEPTED" -> 202
            "INCOMPLETE" -> 424
            else -> 200
        }
        if (outcome == "INCOMPLETE") failure(ToolError.fixed(ToolErrorCode.COMMAND_INCOMPLETE), status, structured = structured)
        else {
            report(toolName, call, started, "OK", status)
            CallToolResult(content = listOf(TextContent(structured.toString())), structuredContent = structured, isError = false)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: SubmissionUnknownException) {
        val id = requestId
        if (id == null) failure(ToolError.internalFailure())
        else failure(ToolError.fixed(ToolErrorCode.OUTCOME_UNKNOWN), error.status, buildJsonObject { put("requestId", id) })
    } catch (error: MessagingInputException) {
        failure(ToolError.fixed(ToolErrorCode.INVALID_INPUT))
    } catch (error: MessagingUnavailableException) {
        failure(ToolError.fixed(ToolErrorCode.NOT_AVAILABLE))
    } catch (error: ClientException) {
        val classified = when {
            send && error.status == 400 -> ToolError.fixed(ToolErrorCode.INVALID_INPUT)
            send && error.status == 409 -> ToolError.fixed(ToolErrorCode.REQUEST_CONFLICT)
            else -> ToolError.of(error).let { if (send) it.copy(retryable = false) else it }
        }
        failure(classified, error.status)
    } catch (error: ConfigException) {
        failure(ToolError.fixed(ToolErrorCode.AUTHENTICATION_REQUIRED))
    } catch (error: Exception) {
        failure(ToolError.internalFailure())
    }
}
