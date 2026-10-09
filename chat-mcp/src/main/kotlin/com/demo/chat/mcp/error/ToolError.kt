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
 * `FEATURE_UNAVAILABLE` has no producer. Search remains outside this phase.
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

    /** A send may have executed, and no reliable result arrived. */
    OUTCOME_UNKNOWN,

    /** The new tool received invalid arguments. */
    INVALID_INPUT,

    /** An earlier submission used this request ID with different content. */
    REQUEST_CONFLICT,

    /** A required backend refused the command. */
    COMMAND_INCOMPLETE,
}

/**
 * One application failure, as the client reads it.
 *
 * The message is a sentence this adapter built. It carries no backend exception
 * text, no topic name, no argument value and no payload.
 *
 * An unknown submission adds its validated `requestId` through the messaging answer function.
 * Other errors retain exactly these three metadata fields.
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
         * durable deduplication contract. The messaging answer function forces false for each send error.
         *
         * **This function reads the reason of the failure and nothing else.**
         * The message of a `ClientException` can hold a URL, a header, a stored
         * value or a credential fragment, because a transport builds it from the
         * material it handled. The sentence comes from [messageOf] alone, so no
         * such text can reach the client.
         */
        fun of(failure: ClientException): ToolError {
            val code = codeOf(failure.reason)
            return ToolError(
                code = code,
                message = messageOf(code),
                retryable = failure.reason == FailureReason.TRANSPORT,
            )
        }

        /**
         * The one fixed sentence a client reads for one failure code.
         *
         * The `when` has no `else`, so a new code fails the compile until a
         * sentence is written for it. A map would answer a missing key with an
         * exception at run time, or with no sentence at all.
         *
         * No sentence names a backend status. A 403 and a 404 answer the same
         * not-available sentence, so a reader cannot tell a hidden object from
         * an absent one. No sentence carries a class name, a count or a value
         * from the request.
         */
        internal fun messageOf(code: ToolErrorCode): String =
            when (code) {
                ToolErrorCode.AUTHENTICATION_REQUIRED -> "the backend refused the credential of this adapter"
                ToolErrorCode.NOT_AVAILABLE -> NOT_AVAILABLE_MESSAGE
                ToolErrorCode.FEATURE_UNAVAILABLE -> "the backend does not offer a feature this call requires"
                ToolErrorCode.BACKEND_UNAVAILABLE -> "the backend did not answer the call"
                ToolErrorCode.LIMIT_EXCEEDED -> "the call passed a limit of this adapter"
                ToolErrorCode.OUTCOME_UNKNOWN -> "the submission outcome is unknown. " +
                    "Repeat only with the same request ID while the server process remains unchanged."
                ToolErrorCode.INVALID_INPUT -> "the tool input is not valid"
                ToolErrorCode.REQUEST_CONFLICT -> "the request ID conflicts with an earlier submission"
                ToolErrorCode.COMMAND_INCOMPLETE -> "a required backend refused the command"
            }

        /** Construct an error from its fixed sentence. */
        fun fixed(code: ToolErrorCode): ToolError = ToolError(code, messageOf(code), false)

        /**
         * The code of a failure that the adapter raised without a backend answer.
         *
         * The caller supplies the sentence, because the adapter wrote it. A
         * `ToolException` and a `ConfigException` name the argument to correct,
         * and that name is the whole value of the sentence.
         *
         * **A caller must never pass a message from a backend exception.** The
         * sentence is adapter prose, and [of] is the path for a backend failure.
         */
        fun refused(message: String): ToolError =
            ToolError(code = ToolErrorCode.NOT_AVAILABLE, message = message, retryable = false)

        /**
         * The code of a failure inside the adapter that no other branch claims.
         *
         * This failure never held a backend answer, so no backend class name and
         * no backend message exists to leak. The sentence is the adapter's own,
         * and it is not the fixed sentence of `BACKEND_UNAVAILABLE`. That
         * sentence says the backend did not answer, which would be false here.
         */
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
