package com.demo.chat.service

import reactor.core.publisher.Mono
import java.util.*

data class ChatCredential(val id: UUID, val password: String)

/**
 * This lets me store a credential (password) for any UUID associated with it
 *  TODO We can update this later
 */
interface   ChatPasswordStore {
    fun getStoredCredentials(key: UUID): Mono<ChatCredential>
    fun addCredential(credential: ChatCredential): Mono<Void>
}