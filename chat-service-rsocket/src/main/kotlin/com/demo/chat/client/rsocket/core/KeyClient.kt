package com.demo.chat.client.rsocket.core

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class KeyClient<T>(private val prefix: String, private val requester: RSocketRequester) : IKeyService<T> {
    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = requester
            .route("${prefix}key")
            .data(kind)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requester
            .route("${prefix}rem")
            .data(key)
            .send()

    override fun exists(key: Key<T>): Mono<Boolean> = requester
            .route("${prefix}exists")
            .data(key)
            .retrieveMono()
}