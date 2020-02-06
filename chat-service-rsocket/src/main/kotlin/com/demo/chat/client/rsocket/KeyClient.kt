package com.demo.chat.client.rsocket

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class KeyClient<T>(private val requestor: RSocketRequester) : IKeyService<T> {
    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = requestor
            .route("key")
            .data(kind)
            .retrieveMono()

    override fun rem(key: Key<T>): Mono<Void> = requestor
            .route("rem")
            .data(key)
            .retrieveMono()

    override fun exists(key: Key<T>): Mono<Boolean> = requestor
            .route("exists")
            .data(key)
            .retrieveMono()
}