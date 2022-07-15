package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyKeyService<T> : IKeyService<T> {
    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = empty()

    override fun rem(key: Key<T>): Mono<Void> = empty()

    override fun exists(key: Key<T>): Mono<Boolean> = Mono.just(false)
}