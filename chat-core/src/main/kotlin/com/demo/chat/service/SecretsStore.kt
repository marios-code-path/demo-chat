package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono
import java.util.*

data class ChatCredential(val secure: String)

/**
 * This lets me store a credential (password) for any UUID associated with it
 *  TODO We can update this later
 */
interface SecretsStore<T, V> {
    fun getStoredCredentials(key: Key<T>): Mono<V>
    fun addCredential(key: Key<T>, credential: V): Mono<Void>
}