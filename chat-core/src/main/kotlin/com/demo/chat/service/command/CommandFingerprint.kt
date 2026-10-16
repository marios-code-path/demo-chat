package com.demo.chat.service.command

import com.demo.chat.domain.command.CommandOperation
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

/**
 * The version 1 fingerprint detects changed normal-send intent under one
 * request identity. Import fingerprints use version 2 and include time and publication.
 * Pointer fingerprints use version 3 and include the target. An import that
 * supplies its message id uses version 4. It includes time, publication, and
 * the supplied id. See `CHAT-zxrgxdlx`.
 *
 * The encoding writes the version as a 4-byte big-endian integer. Each field
 * then writes a 4-byte big-endian length and its UTF-8 bytes. SHA-256 hashes
 * the result. The request ID and owner stay out. Normal-send timestamps stay out.
 */
object CommandFingerprint {
    const val VERSION = 1

    fun of(
        operation: CommandOperation,
        senderId: String,
        destinationId: String,
        content: Any?,
        timestamp: Instant? = null,
        publish: Boolean? = null,
        target: String? = null,
        messageId: String? = null,
    ): String {
        val text = content as? String
            ?: throw IllegalArgumentException(
                "Stage 1 fingerprints a String message only. The value type is ${content?.javaClass?.name}."
            )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(
                when {
                    target != null -> VERSION + 2
                    messageId != null -> VERSION + 3
                    timestamp == null && publish == null -> VERSION
                    else -> VERSION + 1
                }
            )
            val fields = when {
                target != null -> listOf(operation.name, senderId, destinationId, text, target)
                messageId != null ->
                    listOf(operation.name, senderId, destinationId, text, timestamp.toString(), publish.toString(), messageId)
                timestamp == null && publish == null -> listOf(operation.name, senderId, destinationId, text)
                else -> listOf(operation.name, senderId, destinationId, text, timestamp.toString(), publish.toString())
            }
            fields.forEach { field ->
                val encoded = field.toByteArray(Charsets.UTF_8)
                out.writeInt(encoded.size)
                out.write(encoded)
            }
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))
    }
}
