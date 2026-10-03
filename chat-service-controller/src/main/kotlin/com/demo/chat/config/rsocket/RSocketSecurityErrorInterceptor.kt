package com.demo.chat.config.rsocket

import com.demo.chat.security.rsocket.RSocketSecurityErrorCodes
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.exceptions.CustomRSocketException
import io.rsocket.plugins.RSocketInterceptor
import io.rsocket.util.RSocketProxy
import org.reactivestreams.Publisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Gives every core security refusal its RSocket error code. See `CHAT-mpjtnpqv`.
 *
 * **This is a responder interceptor, not a payload interceptor.** It wraps the
 * outermost responder, so it sees two sources of refusal. The first is the
 * Spring Security payload chain. The second is `@PreAuthorize` in a handler.
 * A reactive `@PreAuthorize` emits its denial inside the returned publisher,
 * after the payload chain completes. A payload interceptor never sees it, and
 * `@MessageExceptionHandler` does not see it either.
 *
 * The message stays the message of the exception. The shell and other raw
 * clients still read `Access Denied`.
 */
class RSocketSecurityErrorInterceptor : RSocketInterceptor {

    override fun apply(rsocket: RSocket): RSocket = object : RSocketProxy(rsocket) {
        override fun fireAndForget(payload: Payload): Mono<Void> =
            super.fireAndForget(payload).onErrorMap(::toSecurityError)

        override fun requestResponse(payload: Payload): Mono<Payload> =
            super.requestResponse(payload).onErrorMap(::toSecurityError)

        override fun requestStream(payload: Payload): Flux<Payload> =
            super.requestStream(payload).onErrorMap(::toSecurityError)

        override fun requestChannel(payloads: Publisher<Payload>): Flux<Payload> =
            super.requestChannel(payloads).onErrorMap(::toSecurityError)

        override fun metadataPush(payload: Payload): Mono<Void> =
            super.metadataPush(payload).onErrorMap(::toSecurityError)
    }

    companion object {
        fun toSecurityError(error: Throwable): Throwable = when (error) {
            is AuthenticationException ->
                CustomRSocketException(RSocketSecurityErrorCodes.AUTHENTICATION, error.message ?: "Authentication failed")
            is AccessDeniedException ->
                CustomRSocketException(RSocketSecurityErrorCodes.AUTHORIZATION, error.message ?: "Access Denied")
            else -> error
        }
    }
}
