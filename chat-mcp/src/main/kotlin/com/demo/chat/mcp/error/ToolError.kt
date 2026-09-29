package com.demo.chat.mcp.error

import com.demo.chat.mcp.client.ClientException
import com.demo.chat.mcp.client.FailureReason
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The stable code of one application failure.
 *
 * A client reads this value and not a sentence. The set is closed, so a new
 * failure class needs a new code and a decision.
 *
 * `FEATURE_UNAVAILABLE` and `OUTCOME_UNKNOWN` have no producer in this phase.
 * Recall and send are later phases, and each code waits for its tool. A code
 * with no producer is declared here so the vocabulary is complete, and no path
 * invents one.
 */
enum class ToolErrorCode {
    /** The backend refused the credential, or the credential file is unusable. */
    AUTHENTICATION_REQUIRED,

    /** The object is absent, denied, or outside the configured scope. */
    NOT_AVAILABLE,

    /** A required backend feature is unavailable. No tool emits this yet. */
    FEATURE_UNAVAILABLE,

    /** A backend transport or service failure occurred. */
    BACKEND_UNAVAILABLE,

    /** A response or the configured work limit was exceeded. */
    LIMIT_EXCEEDED,

    /** A send may have executed, and no reliable result arrived. No tool emits this yet. */
    OUTCOME_UNKNOWN,
}

/**
 * One application failure, as the client reads it.
 *
 * The message is a sentence this adapter built. It carries no backend exception
 * text, no topic name, no argument value and no payload.
 *
 * A failed send also carries `outcome`. No send tool exists in this phase, so
 * this type carries no such field. The field arrives with that tool.
 */
data class ToolError(
    val code: ToolErrorCode,
    val message: String,
    val retryable: Boolean,
) {
    /**
     * Render this failure as the application data of a tool error result.
     *
     * The three fields sit flat in `_meta`. The design names exactly these three
     * as the application data, so a nested object would state a different shape.
     */
    fun toMeta(): JsonObject =
        buildJsonObject {
            put(CODE_FIELD, code.name)
            put(MESSAGE_FIELD, message)
            put(RETRYABLE_FIELD, retryable)
        }

    companion object {
        /** The field that carries the stable code. */
        const val CODE_FIELD: String = "code"

        /** The field that carries the short message. */
        const val MESSAGE_FIELD: String = "message"

        /** The field that says whether a repeat of the same call may succeed. */
        const val RETRYABLE_FIELD: String = "retryable"

        /**
         * The sentence a client reads when the backend serves no such object.
         *
         * It names no status, because a 403 and a 404 answer it alike. A
         * sentence that named the status would tell a hidden object from an
         * absent one, and Task 7 rule 4 forbids that.
         */
        const val NOT_AVAILABLE_MESSAGE: String =
            "the backend does not serve this object, or it refuses this caller"

        /**
         * Classify one backend failure.
         *
         * `retryable` is true for a transport failure alone. A transport failure
         * is a connection that did not open, or a deadline that passed. A repeat
         * of a read may then succeed. Every other class is either decided by the
         * request or decided by the stored data, so a repeat gives the same
         * answer.
         *
         * **A failed send must always answer false.** This adapter holds no
         * durable deduplication contract, so a repeat may send twice. No send
         * tool exists yet. That rule binds the tool that adds one.
         *
         * The sentence of a not-available failure is fixed here, so a 403 and a
         * 404 answer the same message. **The status still reaches the diagnostic
         * line**, and that line carries operator data alone.
         */
        fun of(failure: ClientException): ToolError {
            val code = codeOf(failure.reason)
            return ToolError(
                code = code,
                message = messageFor(code, failure),
                retryable = failure.reason == FailureReason.TRANSPORT,
            )
        }

        /** The sentence a client reads for one failure class. */
        private fun messageFor(code: ToolErrorCode, failure: ClientException): String =
            if (code == ToolErrorCode.NOT_AVAILABLE) {
                NOT_AVAILABLE_MESSAGE
            } else {
                failure.message ?: "the backend call failed"
            }

        /** The code of a failure that the adapter raised without a backend answer. */
        fun refused(message: String): ToolError =
            ToolError(code = ToolErrorCode.NOT_AVAILABLE, message = message, retryable = false)

        /** The code of a failure inside the adapter that no other branch claims. */
        fun internalFailure(): ToolError =
            ToolError(
                code = ToolErrorCode.BACKEND_UNAVAILABLE,
                message = "the adapter could not complete the call",
                retryable = false,
            )

        /** Map one backend failure class to its stable code. */
        fun codeOf(reason: FailureReason): ToolErrorCode =
            when (reason) {
                FailureReason.AUTHENTICATION -> ToolErrorCode.AUTHENTICATION_REQUIRED
                FailureReason.NOT_AVAILABLE -> ToolErrorCode.NOT_AVAILABLE
                FailureReason.BACKEND -> ToolErrorCode.BACKEND_UNAVAILABLE
                FailureReason.TRANSPORT -> ToolErrorCode.BACKEND_UNAVAILABLE
                FailureReason.LIMIT -> ToolErrorCode.LIMIT_EXCEEDED
                FailureReason.PROTOCOL -> ToolErrorCode.BACKEND_UNAVAILABLE
            }
    }
}
