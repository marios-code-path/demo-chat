package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

interface SecretsRestMapping<T> : SecretsStore<T> {

    fun keyService(): IKeyService<T>

    @GetMapping("/{id}")
    fun restGetStoredCredentials(@PathVariable id: T): Mono<String> = getStoredCredentials(Key.funKey(id))

    @PutMapping("/add/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredentialWithId(@PathVariable id: T, @RequestBody cred: String): Mono<Void> =
        addCredential(KeyCredential(Key.funKey(id), cred))

    @PutMapping("/add", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun restAddCredential(@RequestBody keyCredential: String): Mono<Key<T>> =
        keyService()
            .key(KeyCredential::class.java)
            .map { key ->
                KeyCredential(key, keyCredential)
            }
            .flatMap { kc ->
                addCredential(kc).thenReturn(kc.key)
            }

    @PostMapping("/compare/{id}")
    fun restCompareSecret(@PathVariable id: T, @RequestBody cred: String): Mono<Boolean> = compareSecret(
        KeyCredential(Key.funKey(id), cred)
    )
}