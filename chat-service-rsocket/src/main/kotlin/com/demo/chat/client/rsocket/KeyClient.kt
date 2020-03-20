package com.demo.chat.client.rsocket

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.retrieveMono
import reactor.core.publisher.Mono

open class KeyClient<T>(
                        private val requestor: RSocketRequester) : IKeyService<T> {
    private val prefix = "key."
    override fun <S> key(kind: Class<S>): Mono<out Key<T>>  {
        println("OUT FOR A KEY")
        return requestor
                .route("${prefix}key")
                .data(kind)
                .retrieveMono()
    }
    override fun rem(key: Key<T>): Mono<Void> = requestor
            .route("${prefix}rem")
            .data(key)
            .retrieveMono()

    override fun exists(key: Key<T>): Mono<Boolean> = requestor
            .route("${prefix}exists")
            .data(key)
            .retrieveMono()
}