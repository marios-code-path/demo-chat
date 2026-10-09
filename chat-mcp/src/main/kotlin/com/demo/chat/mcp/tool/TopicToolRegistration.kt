package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.config.ConfigException
import com.demo.chat.mcp.diagnostic
import com.demo.chat.mcp.error.ToolError
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/** The name of the tool that lists topics. */
const val LIST_TOPICS_TOOL_NAME: String = "chat_list_topics"

/** The name of the tool that reads one topic. */
const val GET_TOPIC_TOOL_NAME: String = "chat_get_topic"

/** The one argument of the single topic read. */
const val TOPIC_ID_ARGUMENT: String = "topicId"

/** The one field of the list output. */
const val TOPICS_FIELD: String = "topics"

/** The one field of the single read output. */
const val TOPIC_FIELD: String = "topic"

/**
 * What both tools declare about themselves.
 *
 * `readOnlyHint` is true, because neither tool changes a backend object.
 * `destructiveHint` is false, because no tool destroys anything.
 * `idempotentHint` is true, because a repeated call answers the same value.
 * `openWorldHint` is true, because the backend is outside this process.
 */
private val TOPIC_ANNOTATIONS =
    ToolAnnotations(
        title = "Chat topic read",
        readOnlyHint = true,
        destructiveHint = false,
        idempotentHint = true,
        openWorldHint = true,
    )

/**
 * The input schema of the list tool.
 *
 * The tool takes no argument. The adapter refuses any argument that arrives,
 * because `ToolSchema` cannot carry `additionalProperties` and the SDK offers
 * no way to add it. See decision 11 in the plan.
 */
private val LIST_INPUT_SCHEMA = ToolSchema(properties = JsonObject(emptyMap()), required = emptyList())

/**
 * The output schema of the list tool.
 *
 * The answer requires its one field. An output schema that declared a property
 * and did not require it would state a weaker contract than the tool keeps.
 */
private val LIST_OUTPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(
                    TOPICS_FIELD,
                    buildJsonObject {
                        put("type", "array")
                        put("items", topicSchema())
                    },
                )
            },
        required = listOf(TOPICS_FIELD),
    )

/** The input schema of the single read. */
private val GET_INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(TOPIC_ID_ARGUMENT, buildJsonObject { put("type", "string") })
            },
        required = listOf(TOPIC_ID_ARGUMENT),
    )

/** The output schema of the single read. Its answer requires its one field. */
private val GET_OUTPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                put(TOPIC_FIELD, topicSchema())
            },
        required = listOf(TOPIC_FIELD),
    )

/**
 * The schema of one topic.
 *
 * Every id is a string. A Long id above 2^53 keeps every digit on the wire.
 */
private fun topicSchema(): JsonObject =
    buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("id", buildJsonObject { put("type", "string") })
                put("root", buildJsonObject { put("type", "string") })
                put("name", buildJsonObject { put("type", "string") })
            },
        )
        put(
            "required",
            buildJsonArray {
                add(JsonPrimitive("id"))
                add(JsonPrimitive("root"))
                add(JsonPrimitive("name"))
            },
        )
        put("additionalProperties", false)
    }

/**
 * Register both topic tools on one server.
 *
 * The backend call blocks, so it runs on the IO dispatcher. A blocking call on
 * the session dispatcher would stall every other frame.
 */
fun registerTopicTools(server: Server, service: TopicToolService) {
    server.addTool(
        Tool(
            name = LIST_TOPICS_TOOL_NAME,
            title = "List chat topics",
            description =
                "List the chat topics that this adapter is configured to read. " +
                    "A topic that the backend denies is left out of the answer.",
            inputSchema = LIST_INPUT_SCHEMA,
            outputSchema = LIST_OUTPUT_SCHEMA,
            annotations = TOPIC_ANNOTATIONS,
        ),
    ) { request ->
        answer(LIST_TOPICS_TOOL_NAME, request, emptySet()) {
            val topics = service.listTopics()
            buildJsonObject {
                put(
                    TOPICS_FIELD,
                    buildJsonArray { topics.forEach { add(it.toJson()) } },
                )
            }
        }
    }

    server.addTool(
        Tool(
            name = GET_TOPIC_TOOL_NAME,
            title = "Read one chat topic",
            description =
                "Read one chat topic by its id. " +
                    "The id must be one of the topic ids that this adapter is configured to read.",
            inputSchema = GET_INPUT_SCHEMA,
            outputSchema = GET_OUTPUT_SCHEMA,
            annotations = TOPIC_ANNOTATIONS,
        ),
    ) { request ->
        answer(GET_TOPIC_TOOL_NAME, request, setOf(TOPIC_ID_ARGUMENT)) {
            buildJsonObject { put(TOPIC_FIELD, service.getTopic(readIdArgument(request)).toJson()) }
        }
    }
}

/**
 * Run one tool body and shape its answer.
 *
 * A refusal of our own becomes an error result with our sentence and its stable
 * code. No backend exception text and no payload reaches the client.
 *
 * One diagnostic line reaches stderr for each call. It names the tool, the
 * correlation id, the duration, the result code and the backend status. It
 * carries no argument value, no topic name, no token and no payload. Every
 * refusal message is the sentence this adapter built, so none of them can carry
 * backend text.
 *
 * **A cancellation is rethrown and not answered.** The design requires
 * cancellation to reach an outstanding read. A `CancellationException` is a
 * kind of `Exception`, so the last branch would otherwise answer a cancelled
 * call as a backend failure.
 */
internal suspend fun answer(
    toolName: String,
    request: CallToolRequest,
    allowed: Set<String>,
    body: () -> JsonObject,
): CallToolResult {
    val call = nextCallNumber()
    val started = System.nanoTime()
    return try {
        refuseUnknownArguments(request, allowed)
        val structured = withContext(Dispatchers.IO) { body() }
        report(toolName, call, started, "OK", HTTP_OK)
        CallToolResult(
            content = listOf(TextContent(structured.toString())),
            structuredContent = structured,
            isError = false,
        )
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: ToolException) {
        refused(toolName, call, started, ToolError.refused(failure.message ?: "the tool refused the request"))
    } catch (failure: ClientException) {
        refused(toolName, call, started, ToolError.of(failure), failure.status)
    } catch (failure: ConfigException) {
        refused(
            toolName,
            call,
            started,
            ToolError.refused("the adapter refused the request: ${failure.message}"),
        )
    } catch (failure: Exception) {
        // An unplanned failure must not reach the client as exception text. The
        // sentence is fixed here, so no class name and no message escapes.
        refused(toolName, call, started, ToolError.internalFailure())
    }
}

/**
 * Report one refusal on stderr and answer it to the client.
 *
 * The message reaches `_meta` and the diagnostic line. Both carry the sentence
 * this adapter built alone.
 */
private fun refused(
    toolName: String,
    call: Long,
    started: Long,
    error: ToolError,
    status: Int? = null,
): CallToolResult {
    report(toolName, call, started, error.code.name, status, "refused: ${error.message}")
    return CallToolResult(
        content = listOf(TextContent(error.message)),
        isError = true,
        meta = error.toMeta(),
    )
}

/**
 * Write the one diagnostic line of one call.
 *
 * The line carries the five fields the design requires. It carries no argument
 * value, no topic name, no token and no payload.
 */
internal fun report(
    toolName: String,
    call: Long,
    started: Long,
    code: String,
    status: Int?,
    outcome: String? = null,
) {
    val millis = (System.nanoTime() - started) / 1_000_000
    val verdict = outcome ?: "answered"
    diagnostic(
        "$toolName $verdict call=$call duration=${millis}ms " +
            "code=$code status=${status ?: STATUS_ABSENT}",
    )
}

/** The status field of a call that reached no backend answer. */
private const val STATUS_ABSENT: String = "-"

/**
 * The status of a call that answered.
 *
 * `BackendHttp.get` returns the body of a 200 response alone. Every other
 * status becomes a `ClientException`, and that exception carries its own
 * status. So this value is read from the transport contract and not guessed.
 */
private const val HTTP_OK: Int = 200

/** The counter that names one call within this process. */
private val CALL_NUMBERS: AtomicLong = AtomicLong(0)

/** Take the next correlation id. It counts from one, and it is unique in this process. */
internal fun nextCallNumber(): Long = CALL_NUMBERS.incrementAndGet()

/**
 * Refuse every argument that the tool does not declare.
 *
 * The tool schema cannot state `additionalProperties`. This check is the
 * contract, and it runs before any backend call.
 */
private fun refuseUnknownArguments(request: CallToolRequest, allowed: Set<String>) {
    val unknown = (request.arguments?.keys ?: emptySet()) - allowed
    val first = unknown.sorted().firstOrNull()
    if (first != null) {
        throw ToolException("the tool accepts no argument named '$first'")
    }
}

/** Read the topic id argument as exact text. */
private fun readIdArgument(request: CallToolRequest): String {
    val element =
        request.arguments?.get(TOPIC_ID_ARGUMENT)
            ?: throw ToolException("the tool requires an argument named '$TOPIC_ID_ARGUMENT'")
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolException("the '$TOPIC_ID_ARGUMENT' argument is not a JSON string")
    }
    return primitive.content
}
