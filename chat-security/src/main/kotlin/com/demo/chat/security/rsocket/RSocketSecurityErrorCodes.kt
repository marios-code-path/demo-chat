package com.demo.chat.security.rsocket

/**
 * The RSocket error codes that carry a core security refusal. See `CHAT-mpjtnpqv`.
 *
 * Both codes sit in the application range of the RSocket protocol,
 * `0x00000301` to `0xFFFFFFFE`. The frame message keeps the human text, such
 * as `Access Denied`. A client reads the code. It never reads the text.
 */
object RSocketSecurityErrorCodes {
    /** The core refused the credential. REST answers 401. */
    const val AUTHENTICATION: Int = 0x00000401

    /** The core knows the caller, and the caller does not hold the right. REST answers 403. */
    const val AUTHORIZATION: Int = 0x00000403
}

/** A core security refusal, as a client reads it. The message is the message of the core. */
sealed class CoreSecurityRefusal(message: String, cause: Throwable) : RuntimeException(message, cause)

class CoreAuthenticationRefusal(cause: Throwable) : CoreSecurityRefusal(
    cause.message ?: "The core refused authentication.",
    cause,
)

class CoreAuthorizationRefusal(cause: Throwable) : CoreSecurityRefusal(
    cause.message ?: "The core refused authorization.",
    cause,
)
