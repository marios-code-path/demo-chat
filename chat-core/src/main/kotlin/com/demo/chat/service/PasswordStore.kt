package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono
import java.util.*

data class ChatCredential<T>(val id: T, val password: String)

/**
 * This lets me store a credential (password) for any UUID associated with it
 *  TODO We can update this later
 */
interface PasswordStore<T> {
    fun getStoredCredentials(key: Key<T>): Mono<ChatCredential<T>>
    fun addCredential(credential: ChatCredential<T>): Mono<Void>
}