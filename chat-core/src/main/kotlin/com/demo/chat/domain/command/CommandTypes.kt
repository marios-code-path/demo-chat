package com.demo.chat.domain.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import java.time.Instant

/** The logical backends of a message command. The letters match the spec. */
enum class BackendId(val letter: String) {
    PERSISTENCE("P"), INDEX("I"), VECTOR("V"), PUBSUB("U");

    companion object {
        fun ofLetter(letter: String): BackendId =
            entries.firstOrNull { it.letter == letter.trim() }
                ?: throw IllegalArgumentException("Unknown backend letter '${letter.trim()}'. The known letters are P, I, V, and U.")
    }
}

/** The backends that a caller waits for. The empty set is `None`. */
data class CompletionRequirement(val backends: Set<BackendId>) {
    companion object {
        val NONE = CompletionRequirement(emptySet())

        /** This method parses `P,I` or `none`. */
        fun parse(text: String): CompletionRequirement =
            if (text.trim().equals("none", ignoreCase = true)) NONE
            else CompletionRequirement(text.split(',').filter { it.isNotBlank() }.map(BackendId::ofLetter).toSet())
    }
}

enum class CommandOperation { RECORD_MESSAGE, IMPORT_MESSAGE }

/** `UNCERTAIN` stays pending for the caller. It contributes no success and is not a terminal failure. */
enum class BackendState { PENDING, SUCCEEDED, FAILED, UNCERTAIN }

data class BackendStatus(
    val state: BackendState,
    val attempts: Int = 0,
    val reason: String? = null,
    val nextRecoveryAt: Instant? = null,
)

enum class CallerOutcome { ACCEPTED, PENDING, COMPLETED, INCOMPLETE }

data class Receipt<T>(val commandId: String, val messageKey: MessageKey<T>)

data class CommandStatus<T>(
    val commandId: String,
    val owner: T,
    val requestId: String,
    val receipt: Receipt<T>,
    val backends: Map<BackendId, BackendStatus>,
    val version: Long,
)

data class MessageSendResult<T>(
    val receipt: Receipt<T>,
    val outcome: CallerOutcome,
    val backends: Map<BackendId, BackendStatus>,
)

/** One admission identity. The owner comes from the trusted service boundary. */
data class RequestIdentity<T>(val owner: T, val requestId: String)

/** The input of admission. Normal sends use `RECORD_MESSAGE`. Imports use `IMPORT_MESSAGE`. */
data class CommandSubmission<T, V>(
    val owner: T,
    val requestId: String,
    val sender: T,
    val dest: T,
    val content: V,
    val operation: CommandOperation = CommandOperation.RECORD_MESSAGE,
    val timestamp: Instant? = null,
    val publish: Boolean? = null,
)

/** One accepted command. Every attempt and every recovery uses these values. */
data class AcceptedCommand<T, V>(
    val commandId: String,
    val owner: T,
    val requestId: String,
    val rootId: T,
    val operation: CommandOperation,
    val orderingKey: T,
    val schemaVersion: Int,
    val message: Message<T, V>,
    val obligations: Set<BackendId>,
    val executionPolicyVersion: Int,
    val expiresAt: Instant? = null,
)
