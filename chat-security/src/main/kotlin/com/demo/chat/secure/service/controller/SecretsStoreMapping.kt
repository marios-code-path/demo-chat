package com.demo.chat.secure.service.controller

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Mono

interface SecretsStoreMapping<T> : SecretsStore<T> {
    @MessageMapping("get")
    override fun getStoredCredentials(key: Key<T>): Mono<String>
    @MessageMapping("add")
    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void>
    @MessageMapping("compare")
    override fun compareSecret(keyCredential: KeyCredential<T>): Mono<Boolean>
}