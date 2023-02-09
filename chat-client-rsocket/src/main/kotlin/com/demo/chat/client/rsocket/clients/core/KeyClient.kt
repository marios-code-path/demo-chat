package com.demo.chat.client.rsocket.clients.core

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import org.springframework.messaging.rsocket.service.RSocketExchange
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


interface KeyClientProxy<T>: IKeyService<T> {
    @RSocketExchange("key.key")
    override fun <S> key(@Payload kind: Class<S>): Mono<out Key<T>>

    @RSocketExchange("key.exists")
    override fun exists(@Payload  key: Key<T>): Mono<Boolean>

    @RSocketExchange("key.rem")
    override fun rem(@Payload key: Key<T>): Mono<Void>
}