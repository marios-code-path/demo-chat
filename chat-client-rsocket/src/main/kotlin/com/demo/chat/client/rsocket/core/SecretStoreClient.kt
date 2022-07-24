package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.Key
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class SecretStoreClient<T>(
    private val prefix: String,
    private val requester: RSocketRequester,
) : SecretsStore<T> {
    override fun getStoredCredentials(key: Key<T>): Mono<String> =
        requester
            .route("${prefix}get")
            .retrieveMono()

    override fun addCredential(key: Key<T>, credential: String): Mono<Void> =
        requester
            .route("${prefix}add")
            .data(credential)
            .send()
}