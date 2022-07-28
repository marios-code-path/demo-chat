package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.Key
import com.demo.chat.service.security.KeyCredential
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class SecretStoreClient<T>(
    private val prefix: String,
    private val requester: RSocketRequester,
) : UserCredentialSecretsStore<T> {
    override fun getStoredCredentials(key: Key<T>): Mono<String> =
        requester
            .route("${prefix}get")
            .data(Mono.just(key), Key::class.java)
            .retrieveMono(String::class.java)

    override fun addCredential(keyCredential: KeyCredential<T>): Mono<Void> =
        requester
            .route("${prefix}add")
            .data(Mono.just(keyCredential), KeyCredential::class.java)
            .send()

    override fun compareSecret(keyCredential: KeyCredential<T>): Mono<Boolean> =
        requester
            .route("${prefix}compare")
            .data(Mono.just(keyCredential), KeyCredential::class.java)
            .retrieveMono()
}