package com.demo.chat.service.core

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface  IKeyService <T> {
    fun <S> key(kind: Class<S>): Mono<out Key<T>>
    fun rem(key: Key<T>): Mono<Void>
    fun exists(key: Key<T>): Mono<Boolean>
    fun kind(key: Key<T>): Mono<String> = Mono.just("none")
}

interface IKeyGenerator <T> {
    fun nextId(): T
    fun nextKey(): Key<T> = Key.funKey(nextId())
}