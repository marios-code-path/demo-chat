package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

data class ChatCredential(val secure: String)

interface SecretsStore<T, V> {
    fun getStoredCredentials(key: Key<T>): Mono<V>
    fun addCredential(key: Key<T>, credential: V): Mono<Void>
}