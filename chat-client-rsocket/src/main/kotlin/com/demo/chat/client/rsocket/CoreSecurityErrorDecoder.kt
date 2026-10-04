package com.demo.chat.client.rsocket

import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.security.rsocket.RSocketNotFound
import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.RSocketErrorException

/**
 * Turns a core security refusal into a typed client error. See `CHAT-mpjtnpqv`.
 *
 * The decoder reads the RSocket error code. It never reads the message text.
 * Any other error passes unchanged.
 *
 * **A core miss becomes [CoreNotFound].** The server gives it
 * [RSocketNotFound.CODE]. So a client can tell a miss from a failure, which
 * still arrives as `ApplicationErrorException`. See `CHAT-scoizkpm`.
 */
object CoreSecurityErrorDecoder {

    fun decode(error: Throwable): Throwable {
        val coded = generateSequence(error) { it.cause }
            .filterIsInstance<RSocketErrorException>()
            .firstOrNull()
            ?: return error
        return when (coded.errorCode()) {
            RSocketSecurityErrorCodes.AUTHENTICATION -> CoreAuthenticationRefusal(coded)
            RSocketSecurityErrorCodes.AUTHORIZATION -> CoreAuthorizationRefusal(coded)
            RSocketNotFound.CODE -> CoreNotFound(coded)
            else -> error
        }
    }
}
