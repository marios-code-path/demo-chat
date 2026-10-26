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

data class PortalRequest(val operation: String, val index: String, val topic: String?)

enum class PortalErrorCode {
    INVALID_INPUT,
    BUSY,
    UNAVAILABLE,
    TIMEOUT,
    OUTPUT_TOO_LARGE,
    INVALID_OUTPUT,
    READ_FAILED,
}

data class PortalError(val code: PortalErrorCode)

data class PortalReply(
    val status: Int?,
    val lines: List<JsonNode>,
    val complete: Boolean,
    val error: PortalError? = null,
)

class PortalProtocolException(val code: PortalErrorCode) : RuntimeException(code.name)

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

        val operation = root.get("operation")?.takeIf { it.isTextual }?.textValue()
        val index = root.get("index")?.takeIf { it.isTextual }?.textValue()
        val topicNode = root.get("topic")
        val topic = topicNode?.takeIf { it.isTextual }?.textValue()
        if (operation != OPERATION || index == null || !NAME.matches(index)) invalidInput()
        if (topicNode != null && (topic == null || !NAME.matches(topic))) invalidInput()

        return PortalRequest(operation, index, topic)
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
        const val OPERATION = "read-register"
        val NEWLINE = '\n'.code.toByte()
        val NAME = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,63}")
        val REQUEST_FIELDS = setOf("operation", "index", "topic")
    }
}
