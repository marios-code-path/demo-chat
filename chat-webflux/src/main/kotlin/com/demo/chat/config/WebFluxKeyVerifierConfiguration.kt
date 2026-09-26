package com.demo.chat.config

import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.UnsupportedDomainException
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * The verifier that every REST route uses. A path id resolves through the key
 * registry before a store sees it. See `CHAT-avduuqwp`.
 */
@Configuration
class WebFluxKeyVerifierConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun <T> keyVerifier(keyBeans: KeyServiceBeans<T>, rootKeys: RootKeys<T>): KeyVerifier<T> =
        KeyVerifier(keyBeans.keyService(), rootKeys)
}

/**
 * The status of a refused key. An id that does not resolve in its domain is
 * not found. A mint that no key service supports is not implemented.
 */
@RestControllerAdvice
class KeyRefusalAdvice {
    @ExceptionHandler(KeyVerificationException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(error: KeyVerificationException): String = error.message ?: "The key does not resolve."

    @ExceptionHandler(UnsupportedDomainException::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun notImplemented(error: UnsupportedDomainException): String = error.message ?: "The mint is not supported."
}
