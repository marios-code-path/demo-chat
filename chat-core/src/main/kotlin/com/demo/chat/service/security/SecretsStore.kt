package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface SecretsStore<T> {
    fun getStoredCredentials(key: Key<T>): Mono<String>
    fun addCredential(keyCredential: KeyCredential<T>): Mono<Void>
    fun compareSecret(keyCredential: KeyCredential<T>): Mono<Boolean> = Mono.just(false) // should throw error when !==
}