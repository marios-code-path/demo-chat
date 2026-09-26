package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.UnsupportedDomainException

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

interface SecretsRestMapping<T> : SecretsStore<T> {

    fun keyService(): IKeyService<T>

    /** A credential belongs to a user, so each id resolves in USER. See `CHAT-avduuqwp`, C72 to C74. */
    fun verifier(): KeyVerifier<T>

    // A path variable of the generic type T erases to Object, so Spring binds
    // the String of the wire. typeUtil() converts it to the key type before
    // the registry reads it.
    fun typeUtil(): TypeUtil<T>

    @GetMapping("/{id}")
    fun restGetStoredCredentials(@PathVariable id: String): Mono<String> =
        verifier().resolve(typeUtil().fromString(id), ChatDomain.USER).flatMap { getStoredCredentials(it.key) }

    @PutMapping("/add/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredentialWithId(@PathVariable id: String, @RequestBody cred: String): Mono<Void> =
        verifier().resolve(typeUtil().fromString(id), ChatDomain.USER).flatMap { addCredential(KeyCredential(it.key, cred)) }

    @PutMapping("/add", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredential(@RequestBody keyCredential: String): Mono<Key<T>> =
        // A credential has no domain, so no key service mints one. The route
        // answers 501. See CHAT-avduuqwp, E19.
        Mono.error(UnsupportedDomainException("KeyCredential"))

    @PostMapping("/compare/{id}")
    fun restCompareSecret(@PathVariable id: String, @RequestBody cred: String): Mono<Boolean> =
        verifier().resolve(typeUtil().fromString(id), ChatDomain.USER).flatMap { compareSecret(KeyCredential(it.key, cred)) }
}