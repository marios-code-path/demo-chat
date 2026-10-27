package com.demo.chat.mcp.portal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration

/** The largest reply frame, including its newline. */
const val PORTAL_REPLY_LIMIT: Int = 4_194_304

/** The operation of the one portal request. */
private const val READ_REGISTER: String = "read-register"

/** The newline byte that ends a frame. */
private const val NEWLINE: Byte = '\n'.code.toByte()

/** One reply from the portal. The lines keep their order. */
data class PortalReadResult(
    /** The shell exit status. It is null when the portal gave no status. */
    val status: Int?,
    /** True when every line is complete. */
    val complete: Boolean,
    /** The register lines. Each line is one JSON object. */
    val lines: List<JsonElement>,
    /** The error code of a failed read. */
    val errorCode: String?,
)

/**
 * The portal client. One call opens one Unix socket connection.
 *
 * The request is one newline-terminated JSON frame. The client half-closes its
 * write side, because the server reads to end of input. The reply is one
 * newline-terminated JSON frame of at most [replyLimit] bytes.
 *
 * **Every transport failure becomes `UNAVAILABLE`, with a null status.** A
 * caller cannot mistake a dropped connection for an empty register. The client
 * does not retry a failure.
 */
class PortalClient(
    private val socket: Path,
    private val selector: String,
    private val deadline: Duration = Duration.ofSeconds(35),
    private val replyLimit: Int = PORTAL_REPLY_LIMIT,
) {
    /** Exchange one request frame and one reply frame. */
    fun read(): PortalReadResult {
        val end = System.nanoTime() + deadline.toNanos()
        return try {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.configureBlocking(false)
                Selector.open().use { watcher ->
                    val key = channel.register(watcher, 0)
                    // A Unix socket usually connects at once. Then no connect
                    // event ever arrives, so the wait is skipped.
                    if (!channel.connect(UnixDomainSocketAddress.of(socket))) {
                        key.interestOps(SelectionKey.OP_CONNECT)
                        if (!await(watcher, SelectionKey.OP_CONNECT, end)) return unavailable()
                        if (!channel.finishConnect()) return unavailable()
                    }
                    if (!send(channel, key, watcher, end)) return unavailable()
                    channel.shutdownOutput()
                    val reply = receive(channel, key, watcher, end) ?: return unavailable()
                    decode(reply)
                }
            }
        } catch (_: IOException) {
            unavailable()
        } catch (_: IllegalArgumentException) {
            unavailable()
        } catch (_: CancelledKeyException) {
            unavailable()
        }
    }

    /** Write the whole request frame, and false on a deadline. */
    private fun send(
        channel: SocketChannel,
        key: SelectionKey,
        watcher: Selector,
        end: Long,
    ): Boolean {
        val buffer = ByteBuffer.wrap(requestFrame())
        key.interestOps(SelectionKey.OP_WRITE)
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) > 0) continue
            if (!await(watcher, SelectionKey.OP_WRITE, end)) return false
        }
        return true
    }

    /** Read one frame, and null on a short, oversized or late reply. */
    private fun receive(
        channel: SocketChannel,
        key: SelectionKey,
        watcher: Selector,
        end: Long,
    ): String? {
        val received = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(8192)
        key.interestOps(SelectionKey.OP_READ)
        while (true) {
            val count = channel.read(buffer)
            if (count < 0) return null
            if (count == 0) {
                if (!await(watcher, SelectionKey.OP_READ, end)) return null
                continue
            }
            buffer.flip()
            val chunk = ByteArray(buffer.remaining())
            buffer.get(chunk)
            buffer.clear()
            received.write(chunk)
            val frame = received.toByteArray()
            val newline = frame.indexOf(NEWLINE)
            if (newline >= 0) {
                if (newline + 1 > replyLimit) return null
                return decodeUtf8(frame, newline)
            }
            if (frame.size > replyLimit) return null
        }
    }

    /** Wait for one operation, and false on the deadline. */
    private fun await(
        watcher: Selector,
        operation: Int,
        end: Long,
    ): Boolean {
        while (true) {
            val remaining = end - System.nanoTime()
            if (remaining <= 0L) return false
            watcher.select(maxOf(1L, remaining / 1_000_000L))
            val key = watcher.selectedKeys().firstOrNull()
            watcher.selectedKeys().clear()
            if (key == null) continue
            if (!key.isValid || (key.readyOps() and operation) == 0) return false
            return true
        }
    }

    /** Decode the JSON text strictly as UTF-8. */
    private fun decodeUtf8(
        frame: ByteArray,
        length: Int,
    ): String? =
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(frame, 0, length))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    /** Decode one reply frame. A broken frame becomes `UNAVAILABLE`. */
    private fun decode(text: String?): PortalReadResult {
        val root =
            text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return unavailable()

        val statusElement = root["status"]
        val status =
            when {
                statusElement == null || statusElement is JsonNull -> null
                statusElement !is JsonPrimitive || statusElement.isString -> return unavailable()
                else -> statusElement.content.toIntOrNull() ?: return unavailable()
            }

        val completeElement = root["complete"] as? JsonPrimitive
        if (completeElement == null || completeElement is JsonNull || completeElement.isString) {
            return unavailable()
        }
        val complete =
            when (completeElement.content) {
                "true" -> true
                "false" -> false
                else -> return unavailable()
            }

        val lines = (root["lines"] as? JsonArray)?.toList() ?: return unavailable()

        val errorElement = root["error"]
        val errorCode =
            when (errorElement) {
                null, is JsonNull -> null
                is JsonObject -> {
                    val code = errorElement["code"] as? JsonPrimitive
                    if (code == null || !code.isString) return unavailable()
                    code.content
                }
                else -> return unavailable()
            }

        return PortalReadResult(status, complete, lines, errorCode)
    }

    /** The one request frame. It holds no credential. */
    private fun requestFrame(): ByteArray {
        val request =
            buildJsonObject {
                put("operation", READ_REGISTER)
                put("index", selector)
            }
        return (request.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun unavailable(): PortalReadResult =
        PortalReadResult(status = null, complete = false, lines = emptyList(), errorCode = "UNAVAILABLE")
}
