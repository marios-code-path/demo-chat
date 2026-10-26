package com.demo.chat.shell.portal

import com.demo.chat.shell.cli.ShellExitStatus
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration

/**
 * Maps one shell register read to one portal reply.
 *
 * The index read keeps its typed `record` and `warning` lines.
 * The topic read receives `type` `record` first, as the wrapper does.
 * A read is complete unless a warning other than `duplicate-pointer` appears.
 * A shell failure keeps its status. A malformed line has no status.
 * A failed read carries no partial lines.
 */
class PortalReadService(
    private val readIndex: (String, Duration) -> String,
    private val readTopic: (String) -> String,
    private val mapper: ObjectMapper,
) {
    fun read(request: PortalRequest, deadline: Duration): PortalReply {
        val text = try {
            if (request.topic == null) readIndex(request.index, deadline) else readTopic(request.topic)
        } catch (error: Exception) {
            return failure(error)
        }
        return try {
            reply(text, isIndex = request.topic == null)
        } catch (error: PortalProtocolException) {
            PortalReply(status = null, lines = emptyList(), complete = false, error = PortalError(error.code))
        }
    }

    private fun reply(text: String, isIndex: Boolean): PortalReply {
        if (text.isEmpty()) return PortalReply(status = 0, lines = emptyList(), complete = true)
        val lines = text.lineSequence().map { line ->
            val node = parse(line)
            if (isIndex) requireIndexType(node) else addRecordType(node)
        }.toList()
        return PortalReply(status = 0, lines = lines, complete = lines.none(::isIncompleteWarning))
    }

    private fun parse(line: String): JsonNode {
        val node = try {
            mapper.readTree(line)
        } catch (_: JsonProcessingException) {
            invalidOutput()
        }
        if (node == null || !node.isObject) invalidOutput()
        return node
    }

    private fun requireIndexType(node: JsonNode): JsonNode {
        val type = node.get("type")
        if (type == null || !type.isTextual || type.textValue() !in INDEX_TYPES) invalidOutput()
        return node
    }

    private fun addRecordType(node: JsonNode): JsonNode {
        if (node.has("type")) return node
        val typed = mapper.createObjectNode()
        typed.put("type", "record")
        node.fields().forEachRemaining { (name, value) -> typed.set<JsonNode>(name, value) }
        return typed
    }

    private fun isIncompleteWarning(node: JsonNode): Boolean =
        node.path("type").asText() == WARNING && node.path("kind").asText() != DUPLICATE_POINTER

    private fun failure(error: Exception): PortalReply {
        val status = ShellExitStatus.of(error)
        val code = when (status) {
            ShellExitStatus.UNAVAILABLE -> PortalErrorCode.UNAVAILABLE
            ShellExitStatus.TIMEOUT -> PortalErrorCode.TIMEOUT
            else -> PortalErrorCode.READ_FAILED
        }
        return PortalReply(status = status.code, lines = emptyList(), complete = false, error = PortalError(code))
    }

    private fun invalidOutput(): Nothing = throw PortalProtocolException(PortalErrorCode.INVALID_OUTPUT)

    private companion object {
        const val WARNING = "warning"
        const val DUPLICATE_POINTER = "duplicate-pointer"
        val INDEX_TYPES = setOf("record", WARNING)
    }
}
