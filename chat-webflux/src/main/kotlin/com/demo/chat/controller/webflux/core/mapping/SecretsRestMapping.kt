package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.UnsupportedDomainException

import com.demo.chat.domain.knownkey.ChatDomain


import com.demo.chat.domain.Key
import com.demo.chat.controller.webflux.resolve.Resolved
import com.demo.chat.service.core.VerifiedKey
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

interface SecretsRestMapping<T> : SecretsStore<T> {

    fun keyService(): IKeyService<T>

    // A credential belongs to a user, so each path id resolves in USER. See CHAT-avduuqwp, C72 to C74.


    @GetMapping("/{id}")
    fun restGetStoredCredentials(@Resolved(ChatDomain.USER) id: VerifiedKey<T>): Mono<String> =
        getStoredCredentials(id.key)

    @PutMapping("/add/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredentialWithId(@Resolved(ChatDomain.USER) id: VerifiedKey<T>, @RequestBody cred: String): Mono<Void> =
        addCredential(KeyCredential(id.key, cred))

    @PutMapping("/add", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredential(@RequestBody keyCredential: String): Mono<Key<T>> =
        // A credential has no domain, so no key service mints one. The route
        // answers 501. See CHAT-avduuqwp, E19.
        Mono.error(UnsupportedDomainException("KeyCredential"))

    @PostMapping("/compare/{id}")
    fun restCompareSecret(@Resolved(ChatDomain.USER) id: VerifiedKey<T>, @RequestBody cred: String): Mono<Boolean> =
        compareSecret(KeyCredential(id.key, cred))
}