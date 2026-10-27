package com.demo.chat.mcp.portal

import com.demo.chat.mcp.config.AdapterConfig
import com.demo.chat.mcp.error.ToolError
import com.demo.chat.mcp.tool.ToolException
import com.demo.chat.mcp.tool.nextCallNumber
import com.demo.chat.mcp.tool.refuseUnknownArguments
import com.demo.chat.mcp.tool.report
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/** The name of the portal read tool. */
const val READ_REGISTER_TOOL_NAME: String = "chat_read_register"

/** The one optional argument of the portal read. */
const val REGISTER_TOPIC_ARGUMENT: String = "topic"

/** The fields of the tool answer. */
private const val STATUS_FIELD: String = "status"
private const val LINES_FIELD: String = "lines"
private const val COMPLETE_FIELD: String = "complete"
private const val ERROR_FIELD: String = "error"
private const val CODE_FIELD: String = "code"

/** The topic rule. It matches the portal request rule. */
private val REGISTER_NAME = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,63}")

/**
 * The input schema of the portal read.
 *
 * The one argument is optional. The selector and the identity are absent on
 * purpose. A caller cannot choose another register, and a caller cannot choose
 * another caller.
 */
private val READ_INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(REGISTER_TOPIC_ARGUMENT, buildJsonObject { put("type", "string") })
            },
        required = emptyList(),
    )

/** The output schema of the portal read. Every answer carries the three fields. */
private val READ_OUTPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(
                    STATUS_FIELD,
                    buildJsonObject {
                        put("type", buildJsonArray { add(JsonPrimitive("integer")); add(JsonPrimitive("null")) })
                    },
                )
                put(
                    LINES_FIELD,
                    buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "object") })
                    },
                )
                put(COMPLETE_FIELD, buildJsonObject { put("type", "boolean") })
                put(
                    ERROR_FIELD,
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject { put(CODE_FIELD, buildJsonObject { put("type", "string") }) },
                        )
                        put("required", buildJsonArray { add(JsonPrimitive(CODE_FIELD)) })
                    },
                )
            },
        required = listOf(STATUS_FIELD, LINES_FIELD, COMPLETE_FIELD),
    )

/** What the portal tool declares about itself. */
private val READ_ANNOTATIONS =
    ToolAnnotations(
        title = "Read the node register",
        readOnlyHint = true,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = true,
    )

/**
 * Register the portal read tool, when the portal settings are complete.
 *
 * The tool serves one fixed register through one fixed socket. It reads the
 * register and never writes it. See CHAT-tcgpzewg.
 */
fun registerPortalTool(server: Server, config: AdapterConfig) {
    val socket = config.portalSocket ?: return
    val selector = config.registerSelector ?: return
    registerPortalTool(server, socket, selector)
}

/** Register one read tool for a complete portal setting. */
fun registerPortalTool(server: Server, socket: Path, selector: String) {
    server.addTool(
        Tool(
            name = READ_REGISTER_TOOL_NAME,
            title = "Read the node register",
            description =
                "Read the node-9 register through the fixed portal. " +
                    "The optional topic narrows the read to one topic.",
            inputSchema = READ_INPUT_SCHEMA,
            outputSchema = READ_OUTPUT_SCHEMA,
            annotations = READ_ANNOTATIONS,
        ),
    ) { request -> readRegister(socket, selector, request) }
}

/**
 * Run one portal read and shape its answer.
 *
 * The socket call blocks, so it runs on the IO dispatcher. A caller reads the
 * stable error code in the answer. No path, no credential and no exception
 * text reaches the client or stderr.
 */
private suspend fun readRegister(
    socket: Path,
    selector: String,
    request: CallToolRequest,
): CallToolResult {
    val call = nextCallNumber()
    val started = System.nanoTime()
    return try {
        refuseUnknownArguments(request, setOf(REGISTER_TOPIC_ARGUMENT))
        val topic = readTopicArgument(request)
        val result = withContext(Dispatchers.IO) { PortalClient(socket, selector).read(topic) }
        // A failed read keeps no partial line, so a caller cannot read a
        // truncated register as a full one.
        val lines = if (result.errorCode == null) result.lines else emptyList()
        report(READ_REGISTER_TOOL_NAME, call, started, result.errorCode ?: "OK", result.status)
        portalAnswer(result.status, lines, result.complete, result.errorCode)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: ToolException) {
        refused(call, started, ToolError.refused(failure.message ?: "the tool refused the request"))
    } catch (failure: Exception) {
        // An unplanned failure must not reach the client as exception text.
        refused(call, started, ToolError.internalFailure())
    }
}

/**
 * Read the optional topic argument.
 *
 * An absent argument is no filter. A present argument must be a nonempty
 * string that matches the portal name rule.
 */
private fun readTopicArgument(request: CallToolRequest): String? {
    val element = request.arguments?.get(REGISTER_TOPIC_ARGUMENT) ?: return null
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolException("the '$REGISTER_TOPIC_ARGUMENT' argument is not a JSON string")
    }
    val text = primitive.content
    if (text.isEmpty()) throw ToolException("the '$REGISTER_TOPIC_ARGUMENT' argument is empty")
    if (!REGISTER_NAME.matches(text)) {
        throw ToolException("the '$REGISTER_TOPIC_ARGUMENT' argument must match ${REGISTER_NAME.pattern}")
    }
    return text
}

/** Shape one portal answer. An incomplete or failed read sets `isError`. */
private fun portalAnswer(
    status: Int?,
    lines: List<JsonElement>,
    complete: Boolean,
    errorCode: String?,
): CallToolResult {
    val structured =
        buildJsonObject {
            if (status == null) put(STATUS_FIELD, JsonNull) else put(STATUS_FIELD, status)
            put(LINES_FIELD, buildJsonArray { lines.forEach { add(it) } })
            put(COMPLETE_FIELD, complete)
            if (errorCode != null) {
                put(ERROR_FIELD, buildJsonObject { put(CODE_FIELD, errorCode) })
            }
        }
    return CallToolResult(
        content = listOf(TextContent(structured.toString())),
        structuredContent = structured,
        isError = errorCode != null || !complete,
    )
}

/** Answer a refusal with the adapter sentence and its stable code. */
private fun refused(
    call: Long,
    started: Long,
    error: ToolError,
): CallToolResult {
    report(READ_REGISTER_TOOL_NAME, call, started, error.code.name, null, "refused: ${error.message}")
    return CallToolResult(
        content = listOf(TextContent(error.message)),
        isError = true,
        meta = error.toMeta(),
    )
}
