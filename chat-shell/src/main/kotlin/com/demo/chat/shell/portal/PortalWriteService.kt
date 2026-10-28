package com.demo.chat.shell.portal

import com.demo.chat.shell.cli.ShellExitStatus
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Duration
import java.util.UUID

/**
 * Maps one register write to one portal reply.
 *
 * The rules come from `node-9/bin/register`. The portal owns the pointer
 * logic, so a caller keeps none. A refused or uncertain write answers an
 * error and no success line. See CHAT-secgyzgg.
 */
class PortalWriteService(
    private val register: String,
    private val gateway: RegisterGateway,
    private val mapper: ObjectMapper,
    private val confirmWindow: Duration = Duration.ofSeconds(10),
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Open one register entry.
     *
     * An existing pointer answers `exists`. A new entry answers `created` after
     * the index readback shows the pointer. A concurrent open of the same key
     * answers `exists` with the winner's pointer.
     */
    fun open(request: PortalRequest, deadline: Duration): PortalReply = attempt {
        val key = PortalWriteRules.requireKey(request.key)
        val short = PortalWriteRules.requireShort(request.short)
        val title = PortalWriteRules.requireTitle(request.title)
        val pointers = readPointers(deadline)

        earliestFor(pointers, key)?.let { pointer ->
            gateway.join(pointer.topic)
            return@attempt success(openLine("exists", key, pointer))
        }

        val newTopic = "$register-fr-$short"
        if (newTopic.length > TOPIC_LIMIT) {
            throw PortalProtocolException(PortalErrorCode.INVALID_INPUT, "the topic $newTopic passes $TOPIC_LIMIT characters")
        }
        val owner = earliestForTopic(pointers, newTopic)
        if (owner != null && owner.key != key) {
            throw WriteRefusal("the topic $newTopic belongs to ${owner.key}, not $key")
        }

        try {
            gateway.addRoom(newTopic)
        } catch (_: Exception) {
            // A concurrent open may have added the pointer first.
            val after = readPointers(deadline)
            val winner = earliestFor(after, key)
                ?: throw WriteRefusal("the topic create of $newTopic failed")
            return@attempt success(openLine("exists", key, winner))
        }

        gateway.join(newTopic)
        val receipt = gateway.post(register, "$key | $newTopic | | $title", newRequestId())
        val postedId = receiptText(receipt, "messageId")
            ?: throw WriteRefusal("the pointer post returned no message id", ShellExitStatus.NOT_CONFIRMED.code)

        val after = readPointers(deadline)
        val earliest = earliestFor(after, key)
            ?: throw WriteRefusal("the index readback does not show pointer $postedId", ShellExitStatus.NOT_CONFIRMED.code)
        val state = if (earliest.id == postedId) "created" else "exists"
        success(openLine(state, key, earliest))
    }

    /**
     * Post one entry to the topic of the earliest pointer.
     *
     * A repeated request id adds no second entry. A post that the confirmation
     * read never shows answers `UNCONFIRMED` with status 75.
     */
    fun post(request: PortalRequest, deadline: Duration): PortalReply = attempt {
        val key = PortalWriteRules.requireKey(request.key)
        val text = PortalWriteRules.requireText(request.text)
        val requestId = PortalWriteRules.requireRequestId(request.requestId) ?: newRequestId()
        val pointers = readPointers(deadline)
        val pointer = earliestFor(pointers, key)
            ?: throw WriteRefusal(
                "no pointer for $key. Run register open first",
                ShellExitStatus.FAILED.code,
                PortalErrorCode.NOT_OPEN,
            )

        gateway.join(pointer.topic)
        val receipt = gateway.post(pointer.topic, text, requestId)
        val messageId = receiptText(receipt, "messageId")
            ?: throw WriteRefusal("the post returned no message id", ShellExitStatus.NOT_CONFIRMED.code)
        val commandId = receiptText(receipt, "commandId").orEmpty()
        val receiptRequest = receiptText(receipt, "requestId") ?: requestId

        if (!confirmed(pointer.topic, messageId)) {
            throw WriteRefusal(
                "no read showed message $messageId within ${confirmWindow.toMillis()} ms",
                ShellExitStatus.NOT_CONFIRMED.code,
                PortalErrorCode.UNCONFIRMED,
            )
        }
        success(postLine(key, pointer.topic, messageId, commandId, receiptRequest))
    }

    /** Poll the topic until it shows [messageId], for at most [confirmWindow]. */
    private fun confirmed(topic: String, messageId: String): Boolean {
        val start = now()
        while (true) {
            val shown = runCatching { gateway.readMessages(topic) }.getOrDefault(emptyList())
            if (shown.any { record -> recordText(record, "id") == messageId && recordText(record, "topic") == topic }) {
                return true
            }
            val left = confirmWindow.toMillis() - (now() - start)
            if (left <= 0) return false
            pause(minOf(POLL_MILLIS, left))
        }
    }

    /** Read the index and apply the denial rule of `node-9/bin/register`. */
    private fun readPointers(deadline: Duration): List<Pointer> {
        val lines = gateway.readIndex(deadline)
        if (lines.none(::isDenial)) return parse(lines)
        gateway.join(register)
        val again = gateway.readIndex(deadline)
        if (again.any(::isDenial)) throw WriteRefusal("the register index was denied")
        return parse(again)
    }

    /** Split the index records into pointers of this register. */
    private fun parse(lines: List<JsonNode>): List<Pointer> =
        lines.filter { isRecord(it) && recordText(it, "topic") == register }.map { line ->
            val id = recordText(line, "id") ?: throw malformed()
            val timestamp = recordText(line, "timestamp") ?: throw malformed()
            val fields = (recordText(line, "text") ?: "").split('|').map { it.trim() }
            if (fields.size != POINTER_FIELDS || fields[0].isEmpty() || fields[1].isEmpty()) throw malformed()
            Pointer(timestamp, id, fields[0], fields[1])
        }

    private fun isRecord(node: JsonNode): Boolean = recordText(node, "type") in setOf(null, "record")

    private fun isDenial(node: JsonNode): Boolean =
        recordText(node, "type") == "warning" && recordText(node, "kind") == "denied-index"

    private fun earliestFor(pointers: List<Pointer>, key: String): Pointer? = earliest(pointers.filter { it.key == key })

    private fun earliestForTopic(pointers: List<Pointer>, topic: String): Pointer? =
        earliest(pointers.filter { it.topic == topic })

    private fun earliest(pointers: List<Pointer>): Pointer? =
        pointers.sortedWith(compareBy({ it.timestamp }, { it.id })).firstOrNull()

    private fun recordText(node: JsonNode, field: String): String? =
        node.get(field)?.takeIf { it.isTextual }?.textValue()

    private fun receiptText(receipt: JsonNode, field: String): String? =
        recordText(receipt, field)?.takeIf { it.isNotEmpty() }

    private fun openLine(state: String, key: String, pointer: Pointer): JsonNode =
        mapper.createObjectNode().apply {
            put("type", "open")
            put("state", state)
            put("key", key)
            put("topic", pointer.topic)
            put("pointer_id", pointer.id)
        }

    private fun postLine(key: String, topic: String, messageId: String, commandId: String, requestId: String): JsonNode =
        mapper.createObjectNode().apply {
            put("type", "post")
            put("key", key)
            put("topic", topic)
            put("message_id", messageId)
            put("command_id", commandId)
            put("request_id", requestId)
        }

    private fun success(line: JsonNode): PortalReply = PortalReply(status = 0, lines = listOf(line), complete = true)

    private fun failure(code: PortalErrorCode, status: Int): PortalReply =
        PortalReply(status = status, lines = emptyList(), complete = false, error = PortalError(code))

    private fun attempt(block: () -> PortalReply): PortalReply =
        try {
            block()
        } catch (refused: PortalProtocolException) {
            failure(refused.code, if (refused.code == PortalErrorCode.INVALID_INPUT) ShellExitStatus.USAGE.code else ShellExitStatus.FAILED.code)
        } catch (refused: WriteRefusal) {
            failure(refused.code, refused.status)
        } catch (_: Exception) {
            failure(PortalErrorCode.WRITE_FAILED, ShellExitStatus.FAILED.code)
        }

    private fun malformed(): WriteRefusal =
        WriteRefusal("a pointer is not in the form <key> | <topic> | <metadata> | <title>")

    /** One index pointer: the stored time, the message id, the key, and the topic. */
    private data class Pointer(val timestamp: String, val id: String, val key: String, val topic: String)

    /** A refused write, with the error code and the shell status that it answers. */
    private class WriteRefusal(
        message: String,
        val status: Int = ShellExitStatus.FAILED.code,
        val code: PortalErrorCode = PortalErrorCode.WRITE_FAILED,
    ) : RuntimeException(message)

    private companion object {
        const val TOPIC_LIMIT = 64
        const val POINTER_FIELDS = 4
        const val POLL_MILLIS = 500L
    }
}
