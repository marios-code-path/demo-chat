package com.demo.chat.shell.portal

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** The operation that reads the register index or one topic. */
const val READ_REGISTER: String = "read-register"

/** The operation that opens one register entry. */
const val OPEN_REGISTER_ENTRY: String = "open-register-entry"

/** The operation that posts one register entry. */
const val POST_REGISTER_ENTRY: String = "post-register-entry"

/**
 * One portal request.
 *
 * A read holds [topic]. An open holds [key], [short], and [title]. A post
 * holds [key], [text], and [requestId]. Every write field is null for an
 * operation that does not use it.
 */
data class PortalRequest(
    val operation: String,
    val index: String,
    val topic: String? = null,
    val key: String? = null,
    val short: String? = null,
    val title: String? = null,
    val text: String? = null,
    val requestId: String? = null,
)

enum class PortalErrorCode {
    INVALID_INPUT,
    BUSY,
    UNAVAILABLE,
    TIMEOUT,
    OUTPUT_TOO_LARGE,
    INVALID_OUTPUT,
    READ_FAILED,
    WRITE_FAILED,
    NOT_OPEN,
    UNCONFIRMED,
}

data class PortalError(val code: PortalErrorCode)

data class PortalReply(
    val status: Int?,
    val lines: List<JsonNode>,
    val complete: Boolean,
    val error: PortalError? = null,
    /** The cause of a failure. It is for the diagnostic only, never for the wire. */
    val detail: String? = null,
)

class PortalProtocolException(val code: PortalErrorCode) : RuntimeException(code.name)

/**
 * Reads one newline-terminated request frame and writes one reply frame.
 *
 * The client closes its socket write side after the request.
 * Read to end of input before parsing, so a second frame cannot reach a backend read.
 * A request has at most 4096 bytes, including the newline.
 * A reply has at most 4194304 bytes, including the newline.
 * Invalid requests raise `INVALID_INPUT`.
 * Oversized replies raise `OUTPUT_TOO_LARGE` before any byte is written.
 */
class PortalProtocol(private val mapper: ObjectMapper) {
    fun readRequest(input: InputStream): PortalRequest {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > REQUEST_LIMIT) invalidInput()
            bytes.write(buffer, 0, count)
        }

        val frame = bytes.toByteArray()
        if (frame.isEmpty() || frame.last() != NEWLINE || frame.dropLast(1).contains(NEWLINE)) invalidInput()

        val json = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(frame, 0, frame.size - 1))
                .toString()
        } catch (_: CharacterCodingException) {
            invalidInput()
        }

        val root = try {
            mapper.factory.createParser(json).use { parser ->
                parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                val value = mapper.readTree<JsonNode>(parser)
                if (parser.nextToken() != null) invalidInput()
                value
            }
        } catch (_: JsonProcessingException) {
            invalidInput()
        }

        if (root == null || !root.isObject) invalidInput()
        if (root.fieldNames().asSequence().any { it !in REQUEST_FIELDS }) invalidInput()

        val operation = root.get("operation")?.takeIf { it.isTextual }?.textValue() ?: invalidInput()
        val allowed = allowedFields(operation) ?: invalidInput()
        val present = root.fieldNames().asSequence().toSet()
        if (!present.all { it in allowed }) invalidInput()
        if (!present.containsAll(REQUIRED.getValue(operation))) invalidInput()

        val index = text(root, "index") ?: invalidInput()
        if (!NAME.matches(index)) invalidInput()
        val topic = text(root, "topic")
        if (topic != null && !NAME.matches(topic)) invalidInput()

        return PortalRequest(
            operation = operation,
            index = index,
            topic = topic,
            key = text(root, "key"),
            short = text(root, "short"),
            title = text(root, "title"),
            text = text(root, "text"),
            requestId = text(root, "requestId"),
        )
    }

    /** The allowed fields of [operation]. Answer null for an unknown operation. */
    private fun allowedFields(operation: String?): Set<String>? = when (operation) {
        READ_REGISTER -> READ_FIELDS
        OPEN_REGISTER_ENTRY -> OPEN_FIELDS
        POST_REGISTER_ENTRY -> POST_FIELDS
        else -> null
    }

    /** Read one optional textual field. A present field that is not text is refused. */
    private fun text(root: JsonNode, field: String): String? {
        val node = root.get(field) ?: return null
        if (!node.isTextual) invalidInput()
        return node.textValue()
    }

    fun writeReply(output: OutputStream, reply: PortalReply) {
        val root = mapper.createObjectNode()
        if (reply.status == null) root.putNull("status") else root.put("status", reply.status)
        val lines = root.putArray("lines")
        reply.lines.forEach(lines::add)
        root.put("complete", reply.complete)
        reply.error?.let { root.putObject("error").put("code", it.code.name) }

        val json = mapper.writeValueAsBytes(root)
        if (json.size >= REPLY_LIMIT) throw PortalProtocolException(PortalErrorCode.OUTPUT_TOO_LARGE)
        output.write(json)
        output.write(NEWLINE.toInt())
        output.flush()
    }

    private fun invalidInput(): Nothing = throw PortalProtocolException(PortalErrorCode.INVALID_INPUT)

    private companion object {
        const val REQUEST_LIMIT = 4096
        const val REPLY_LIMIT = 4_194_304
        val NEWLINE = '\n'.code.toByte()
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,63}")

        /** Every field that any operation may hold. */
        val REQUEST_FIELDS = setOf("operation", "index", "topic", "key", "short", "title", "text", "requestId")

        /** The fields of a read, an open, and a post. */
        val READ_FIELDS = setOf("operation", "index", "topic")
        val OPEN_FIELDS = setOf("operation", "index", "key", "short", "title")
        val POST_FIELDS = setOf("operation", "index", "key", "text", "requestId")

        /** The fields that each operation must hold. */
        val REQUIRED = mapOf(
            READ_REGISTER to setOf("operation", "index"),
            OPEN_REGISTER_ENTRY to setOf("operation", "index", "key", "short", "title"),
            POST_REGISTER_ENTRY to setOf("operation", "index", "key", "text", "requestId"),
        )
    }
}
