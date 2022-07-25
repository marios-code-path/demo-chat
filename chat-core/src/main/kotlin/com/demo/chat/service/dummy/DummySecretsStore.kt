package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.SecretsStore
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class  DummySecretsStore<T> : SecretsStore<T> {
    override fun getStoredCredentials(key: Key<T>): Mono<String> = empty()

    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void> = empty()

    override fun compareSecret(keyCredential: KeyCredential<T>): Mono<Boolean> = Mono.just(false)
}