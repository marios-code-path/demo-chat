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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.coroutines.cancellation.CancellationException

/** The name of the portal read tool. */
const val READ_REGISTER_TOOL_NAME: String = "chat_read_register"

/** The name of the tool that opens one register entry. */
const val OPEN_REGISTER_ENTRY_TOOL_NAME: String = "chat_open_register_entry"

/** The name of the tool that posts one register entry. */
const val POST_REGISTER_ENTRY_TOOL_NAME: String = "chat_post_register_entry"

/** The one optional argument of the portal read. */
const val REGISTER_TOPIC_ARGUMENT: String = "topic"

/** The arguments of the write tools. */
private const val KEY_ARGUMENT: String = "key"
private const val SHORT_ARGUMENT: String = "short"
private const val TITLE_ARGUMENT: String = "title"
private const val TEXT_ARGUMENT: String = "text"
private const val REQUEST_ID_ARGUMENT: String = "requestId"

/** The largest key, in characters. It matches the portal rule. */
private const val KEY_LIMIT: Int = 64

/** The largest entry text, in UTF-8 bytes. It matches the portal rule. */
private const val TEXT_LIMIT: Int = 3072

/** The largest title, in characters. It matches the portal rule. */
private const val TITLE_LIMIT: Int = 256

/** The largest request id, in characters. It matches the portal rule. */
private const val REQUEST_ID_LIMIT: Int = 128

/** The fields of the tool answer. */
private const val STATUS_FIELD: String = "status"
private const val LINES_FIELD: String = "lines"
private const val COMPLETE_FIELD: String = "complete"
private const val ERROR_FIELD: String = "error"
private const val CODE_FIELD: String = "code"

/** The topic rule. It matches the portal request rule. */
private val REGISTER_NAME = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,63}")

/** The short name rule. It matches the portal rule. */
private val SHORT_NAME = Regex("^[a-z0-9][a-z0-9-]*$")

/** The request id rule. It matches the portal rule. */
private val REQUEST_ID = Regex("^[A-Za-z0-9._:-]+$")

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

/** The input schema of the open tool. Every argument is required. */
private val OPEN_INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(KEY_ARGUMENT, buildJsonObject { put("type", "string") })
                put(SHORT_ARGUMENT, buildJsonObject { put("type", "string") })
                put(TITLE_ARGUMENT, buildJsonObject { put("type", "string") })
            },
        required = listOf(KEY_ARGUMENT, SHORT_ARGUMENT, TITLE_ARGUMENT),
    )

/** The input schema of the post tool. The request id is optional. */
private val POST_INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(KEY_ARGUMENT, buildJsonObject { put("type", "string") })
                put(TEXT_ARGUMENT, buildJsonObject { put("type", "string") })
                put(REQUEST_ID_ARGUMENT, buildJsonObject { put("type", "string") })
            },
        required = listOf(KEY_ARGUMENT, TEXT_ARGUMENT),
    )

/** What the open tool declares about itself. */
private val OPEN_ANNOTATIONS =
    ToolAnnotations(
        title = "Open a register entry",
        readOnlyHint = false,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = true,
    )

/** What the post tool declares about itself. */
private val POST_ANNOTATIONS =
    ToolAnnotations(
        title = "Post a register entry",
        readOnlyHint = false,
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
    registerPortalTool(server, socket, selector, config.registerName)
}

/**
 * Register the portal tools for a complete portal setting.
 *
 * The read tool needs the socket and the selector. The write tools also need
 * [registerName], because a write mints a topic name and an id gives no prefix.
 */
fun registerPortalTool(server: Server, socket: Path, selector: String, registerName: String? = null) {
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

    if (registerName == null) return
    server.addTool(
        Tool(
            name = OPEN_REGISTER_ENTRY_TOOL_NAME,
            title = "Open a register entry",
            description =
                "Open the register entry of one key through the fixed portal. " +
                    "A repeated call answers the entry that exists.",
            inputSchema = OPEN_INPUT_SCHEMA,
            outputSchema = READ_OUTPUT_SCHEMA,
            annotations = OPEN_ANNOTATIONS,
        ),
    ) { request -> openEntry(socket, selector, request) }
    server.addTool(
        Tool(
            name = POST_REGISTER_ENTRY_TOOL_NAME,
            title = "Post a register entry",
            description =
                "Post one entry to the register entry of one key through the fixed portal. " +
                    "The tool derives a request id when none is given, so a retry is safe.",
            inputSchema = POST_INPUT_SCHEMA,
            outputSchema = READ_OUTPUT_SCHEMA,
            annotations = POST_ANNOTATIONS,
        ),
    ) { request -> postEntry(socket, selector, request) }
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
        refused(READ_REGISTER_TOOL_NAME, call, started, ToolError.refused(failure.message ?: "the tool refused the request"))
    } catch (failure: Exception) {
        // An unplanned failure must not reach the client as exception text.
        refused(READ_REGISTER_TOOL_NAME, call, started, ToolError.internalFailure())
    }
}

/**
 * Run one portal open and shape its answer.
 *
 * The four argument rules repeat the rules of the chat-shell module, because
 * this module cannot import them. A broken rule never reaches the portal.
 */
private suspend fun openEntry(
    socket: Path,
    selector: String,
    request: CallToolRequest,
): CallToolResult {
    val call = nextCallNumber()
    val started = System.nanoTime()
    return try {
        refuseUnknownArguments(request, setOf(KEY_ARGUMENT, SHORT_ARGUMENT, TITLE_ARGUMENT))
        val key = keyArgument(requireTextArgument(request, KEY_ARGUMENT))
        val short = shortArgument(requireTextArgument(request, SHORT_ARGUMENT))
        val title = titleArgument(requireTextArgument(request, TITLE_ARGUMENT))
        val result = withContext(Dispatchers.IO) { PortalClient(socket, selector).open(key, short, title) }
        val lines = if (result.errorCode == null) result.lines else emptyList()
        report(OPEN_REGISTER_ENTRY_TOOL_NAME, call, started, result.errorCode ?: "OK", result.status)
        portalAnswer(result.status, lines, result.complete, result.errorCode)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: ToolException) {
        refused(OPEN_REGISTER_ENTRY_TOOL_NAME, call, started, ToolError.refused(failure.message ?: "the tool refused the request"))
    } catch (failure: Exception) {
        refused(OPEN_REGISTER_ENTRY_TOOL_NAME, call, started, ToolError.internalFailure())
    }
}

/** Run one portal post and shape its answer. */
private suspend fun postEntry(
    socket: Path,
    selector: String,
    request: CallToolRequest,
): CallToolResult {
    val call = nextCallNumber()
    val started = System.nanoTime()
    return try {
        refuseUnknownArguments(request, setOf(KEY_ARGUMENT, TEXT_ARGUMENT, REQUEST_ID_ARGUMENT))
        val key = keyArgument(requireTextArgument(request, KEY_ARGUMENT))
        val text = textArgument(requireTextArgument(request, TEXT_ARGUMENT))
        val requestId =
            if (request.arguments.orEmpty().containsKey(REQUEST_ID_ARGUMENT)) {
                requestIdArgument(requireTextArgument(request, REQUEST_ID_ARGUMENT))
            } else {
                derivedRequestId(key, text)
            }
        val result = withContext(Dispatchers.IO) { PortalClient(socket, selector).post(key, text, requestId) }
        val lines = if (result.errorCode == null) result.lines else emptyList()
        report(POST_REGISTER_ENTRY_TOOL_NAME, call, started, result.errorCode ?: "OK", result.status)
        portalAnswer(result.status, lines, result.complete, result.errorCode)
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: ToolException) {
        refused(POST_REGISTER_ENTRY_TOOL_NAME, call, started, ToolError.refused(failure.message ?: "the tool refused the request"))
    } catch (failure: Exception) {
        refused(POST_REGISTER_ENTRY_TOOL_NAME, call, started, ToolError.internalFailure())
    }
}

/** Derive one stable request id from the key and entry text. */
private fun derivedRequestId(key: String, text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(key.toByteArray(StandardCharsets.UTF_8))
    digest.update(0.toByte())
    val hash = digest.digest(text.toByteArray(StandardCharsets.UTF_8))
    return "auto-" + HexFormat.of().formatHex(hash).take(32)
}

/** Read one required argument as text. */
private fun requireTextArgument(request: CallToolRequest, name: String): String {
    val element = request.arguments?.get(name)
        ?: throw ToolException("the '$name' argument is missing")
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolException("the '$name' argument is not a JSON string")
    }
    return primitive.content
}

/** Apply the key rule of `node-9/bin/register`. */
private fun keyArgument(key: String): String {
    if (key.isEmpty()) throw ToolException("the '$KEY_ARGUMENT' argument is empty")
    if (key.length > KEY_LIMIT) throw ToolException("the '$KEY_ARGUMENT' argument passes $KEY_LIMIT characters")
    if (key.contains('|')) throw ToolException("the '$KEY_ARGUMENT' argument holds the | delimiter")
    if (key.contains('\\')) throw ToolException("the '$KEY_ARGUMENT' argument holds a backslash")
    if (key.any { it.isISOControl() }) throw ToolException("the '$KEY_ARGUMENT' argument holds a control character")
    if (key.first().isWhitespace() || key.last().isWhitespace()) {
        throw ToolException("the '$KEY_ARGUMENT' argument holds a space at its start or its end")
    }
    if (key == "-") throw ToolException("the '$KEY_ARGUMENT' argument - is reserved for archive pointers")
    return key
}

/** Apply the short name rule. */
private fun shortArgument(short: String): String {
    if (!SHORT_NAME.matches(short)) {
        throw ToolException("the '$SHORT_ARGUMENT' argument is not lowercase letters, digits, or dashes")
    }
    return short
}

/** Apply the title rule. */
private fun titleArgument(title: String): String {
    if (title.isEmpty()) throw ToolException("the '$TITLE_ARGUMENT' argument is empty")
    if (title.length > TITLE_LIMIT) throw ToolException("the '$TITLE_ARGUMENT' argument passes $TITLE_LIMIT characters")
    if (title.contains('|')) throw ToolException("the '$TITLE_ARGUMENT' argument holds the | delimiter")
    if (title.any { it.isISOControl() }) throw ToolException("the '$TITLE_ARGUMENT' argument holds a control character")
    return title
}

/** Apply the entry text rule. */
private fun textArgument(text: String): String {
    if (text.isBlank()) throw ToolException("the '$TEXT_ARGUMENT' argument is empty or whitespace only")
    val bytes = text.toByteArray(Charsets.UTF_8).size
    if (bytes > TEXT_LIMIT) throw ToolException("the '$TEXT_ARGUMENT' argument passes $TEXT_LIMIT bytes")
    return text
}

/** Apply the request id rule. */
private fun requestIdArgument(requestId: String): String {
    if (requestId.length > REQUEST_ID_LIMIT) {
        throw ToolException("the '$REQUEST_ID_ARGUMENT' argument passes $REQUEST_ID_LIMIT characters")
    }
    if (!REQUEST_ID.matches(requestId)) {
        throw ToolException("the '$REQUEST_ID_ARGUMENT' argument is not letters, digits, dot, dash, colon, or underscore")
    }
    if (requestId == "_") throw ToolException("the '$REQUEST_ID_ARGUMENT' argument _ asks for a new entry")
    return requestId
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
    tool: String,
    call: Long,
    started: Long,
    error: ToolError,
): CallToolResult {
    report(tool, call, started, error.code.name, null, "refused: ${error.message}")
    return CallToolResult(
        content = listOf(TextContent(error.message)),
        isError = true,
        meta = error.toMeta(),
    )
}
