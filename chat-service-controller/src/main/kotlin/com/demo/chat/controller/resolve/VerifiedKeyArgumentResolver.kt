package com.demo.chat.controller.resolve

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.MethodParameter
import org.springframework.core.ReactiveAdapterRegistry
import org.springframework.core.codec.Decoder
import org.springframework.messaging.Message
import org.springframework.messaging.handler.annotation.reactive.PayloadMethodArgumentResolver
import org.springframework.messaging.handler.invocation.reactive.HandlerMethodArgumentResolver
import reactor.core.publisher.Mono

/**
 * Builds the `VerifiedKey` of a [Verified] payload. See `CHAT-avduuqwp`, D1.
 *
 * The payload decodes as a `Key` through the decoders of the message handler.
 * The key then verifies through `KeyVerifier`. An unknown key, a forged root,
 * and a key outside the domain each fail with `KeyVerificationException`. The
 * handler does not run.
 *
 * The verifier is read at the first request, because the message handler is
 * customized before the key registry beans exist.
 */
class VerifiedKeyArgumentResolver(
    decoders: List<Decoder<*>>,
    private val verifiers: ObjectProvider<KeyVerifier<*>>,
) : HandlerMethodArgumentResolver {

    // The payload resolver decodes the key. Its resolveArgument is final, so this class delegates.
    private val payload = PayloadMethodArgumentResolver(decoders, null, ReactiveAdapterRegistry.getSharedInstance(), true)

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(Verified::class.java) &&
            VerifiedKey::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(parameter: MethodParameter, message: Message<*>): Mono<Any> = Mono.defer {
        val domain = domainOf(parameter)

        @Suppress("UNCHECKED_CAST")
        val verifier = verifiers.getObject() as KeyVerifier<Any?>

        payload.resolveArgument(KEY_PARAMETER, message)
            .flatMap { key ->
                @Suppress("UNCHECKED_CAST")
                verifier.verify(key as Key<Any?>, domain)
            }
            .map { it as Any }
    }

    private fun domainOf(parameter: MethodParameter): ChatDomain? {
        val annotation = parameter.getParameterAnnotation(Verified::class.java)!!
        return when {
            annotation.anyDomain -> null
            annotation.domain.size == 1 -> annotation.domain.single()
            annotation.domain.isEmpty() -> keyDomainOf(parameter.containingClass)
            else -> throw IllegalStateException("A @Verified key names one domain at most.")
        }
    }

    companion object {
        /** The signature that the payload decodes against. */
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun keyPayload(key: Key<Any>) = Unit

        private val KEY_PARAMETER =
            MethodParameter(VerifiedKeyArgumentResolver::class.java.getMethod("keyPayload", Key::class.java), 0)
    }
}
