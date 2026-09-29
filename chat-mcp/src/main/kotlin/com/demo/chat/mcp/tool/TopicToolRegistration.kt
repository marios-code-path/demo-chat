package com.demo.chat.mcp.tool

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.config.ConfigException
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
        answer(request, emptySet()) {
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
        answer(request, setOf(TOPIC_ID_ARGUMENT)) {
            buildJsonObject { put(TOPIC_FIELD, service.getTopic(readIdArgument(request)).toJson()) }
        }
    }
}

/**
 * Run one tool body and shape its answer.
 *
 * A refusal of our own becomes an error result with our sentence. No backend
 * exception text and no payload reaches the client.
 */
private suspend fun answer(
    request: CallToolRequest,
    allowed: Set<String>,
    body: () -> JsonObject,
): CallToolResult =
    try {
        refuseUnknownArguments(request, allowed)
        val structured = withContext(Dispatchers.IO) { body() }
        CallToolResult(
            content = listOf(TextContent(structured.toString())),
            structuredContent = structured,
            isError = false,
        )
    } catch (failure: ToolException) {
        refusal(failure.message ?: "the tool refused the request")
    } catch (failure: ClientException) {
        refusal(failure.message ?: "the backend call failed")
    } catch (failure: ConfigException) {
        refusal("the adapter refused the request: ${failure.message}")
    }

/** One error result. It carries one sentence and no payload. */
private fun refusal(message: String): CallToolResult =
    CallToolResult(content = listOf(TextContent(message)), isError = true)

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
