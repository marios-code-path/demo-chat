package com.demo.chat.security.rsocket

/**
 * The RSocket error code of a core miss. See `CHAT-scoizkpm`.
 *
 * **A miss and a failure arrived as one type before this code.** The server
 * reported both as `ApplicationErrorException` with code `0x201`. Only the
 * message text told `Object not Found` from a store failure, and a client must
 * not read the text. So a client that read every `0x201` as a miss turned a
 * real failure into an unknown room.
 *
 * The code sits in the application range, beside the security codes in
 * [RSocketSecurityErrorCodes]. The frame message keeps the human text.
 */
object RSocketNotFound {
    /** The core holds no such object, or the registry holds no such key. REST answers 404. */
    const val CODE: Int = 0x00000404
}

/** A core miss, as a client reads it. The message is the message of the core. */
class CoreNotFound(cause: Throwable) : RuntimeException(cause.message ?: "Object not Found", cause)
