package com.demo.chat.config

import com.demo.chat.controller.webflux.resolve.ResolvedKeyArgumentResolver
import com.demo.chat.domain.KeyInputException
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.TypeUtil
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer
import com.demo.chat.domain.UnsupportedDomainException
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.rsocket.CoreAuthenticationRefusal
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.service.core.KeyVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * The verifier that every REST route uses. A path id resolves through the key
 * registry before a store sees it. See `CHAT-avduuqwp`.
 */
@Configuration
class WebFluxKeyVerifierConfiguration(
    private val verifiers: ObjectProvider<KeyVerifier<*>>,
    private val typeUtils: ObjectProvider<TypeUtil<*>>,
) : WebFluxConfigurer {

    /** Each `@Resolved` path id resolves through the verifier before a handler runs. See `CHAT-avduuqwp`, D2. */
    override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
        configurer.addCustomResolver(ResolvedKeyArgumentResolver(verifiers, typeUtils))
    }

    @Bean
    @ConditionalOnMissingBean
    fun <T> keyVerifier(keyBeans: KeyServiceBeans<T>, rootKeys: RootKeys<T>): KeyVerifier<T> =
        KeyVerifier(keyBeans.keyService(), rootKeys)
}

/**
 * The status of a refused key. An id that does not resolve in its domain is
 * not found. An id that does not convert exactly to the key type is a bad
 * request. A mint that no key service supports is not implemented.
 *
 * **A denied caller is refused here as well.** Method security throws
 * `AuthorizationDeniedException` from the handler. That type is a subclass of
 * `AccessDeniedException`, and no other type in this repository renders it, so
 * a refusal answered 500. A 500 is not a refusal to any caller. See
 * `CHAT-znprrzhn`.
 */
@RestControllerAdvice
class KeyRefusalAdvice {
    @ExceptionHandler(CoreAuthenticationRefusal::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun coreAuthenticationRefused(error: CoreAuthenticationRefusal): String =
        error.message ?: "The core refused authentication."

    @ExceptionHandler(CoreAuthorizationRefusal::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun coreAuthorizationRefused(error: CoreAuthorizationRefusal): String =
        error.message ?: "The core refused authorization."

    /**
     * A core miss is not found, and the core message stays. The client decoder
     * makes [CoreNotFound] from code `0x404`. Before this handler, a core miss
     * answered 500. Measured on 2026-10-03. See `CHAT-undefoqd`.
     */
    @ExceptionHandler(CoreNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun coreNotFound(error: CoreNotFound): String = error.message ?: "Object not Found"

    /**
     * An in-process miss is not found too. A single process REST launch runs
     * the composite in the same JVM, so a miss arrives as [NotFoundException]
     * and not as [CoreNotFound]. Before this handler, it answered 500.
     * Measured on 2026-10-04. See `CHAT-sdvmkidi`.
     */
    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun inProcessNotFound(error: NotFoundException): String = error.message ?: "Object not Found"

    @ExceptionHandler(KeyInputException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(error: KeyInputException): String = error.message ?: "The key id is not valid."

    @ExceptionHandler(KeyVerificationException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(error: KeyVerificationException): String = error.message ?: "The key does not resolve."

    @ExceptionHandler(UnsupportedDomainException::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun notImplemented(error: UnsupportedDomainException): String = error.message ?: "The mint is not supported."

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden(error: AccessDeniedException): String = error.message ?: "Access Denied"
}
