package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface SecretsStore<T> {
    fun getStoredCredentials(key: Key<T>): Mono<String>
    fun addCredential(key: Key<T>, credential: String): Mono<Void>
}