package com.demo.chat.secure.service

import com.demo.chat.domain.Key
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

@MessageMapping("secrets")
interface SecretsStoreMapping<T> : SecretsStore<T> {
    @MessageMapping("get")
    override fun getStoredCredentials(key: Key<T>): Mono<String>
    @MessageMapping("add")
    override fun addCredential(key: Key<T>, credential: String): Mono<Void>
    @MessageMapping("compare")
    override fun compareSecret(key: Key<T>, credential: String): Mono<Void>
}