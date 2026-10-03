package com.demo.chat.client.rsocket

import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.RSocketSecurityErrorKind
import io.rsocket.exceptions.ApplicationErrorException
import tools.jackson.databind.json.JsonMapper

object CoreSecurityErrorDecoder {

    private val mapper = JsonMapper.builder().build()

    fun decode(error: Throwable): Throwable {
        val applicationError = generateSequence(error) { it.cause }
            .filterIsInstance<ApplicationErrorException>()
            .firstOrNull()
            ?: return error

        val payload = runCatching { mapper.readTree(applicationError.message ?: return error) }
            .getOrNull()
            ?: return error

        if (payload.get("version")?.asInt() != VERSION) {
            return error
        }

        return when (payload.get("kind")?.asString()) {
            RSocketSecurityErrorKind.AUTHENTICATION.name -> CoreAuthenticationRefusal(error)
            RSocketSecurityErrorKind.AUTHORIZATION.name -> CoreAuthorizationRefusal(error)
            else -> error
        }
    }

    private const val VERSION = 1
}
