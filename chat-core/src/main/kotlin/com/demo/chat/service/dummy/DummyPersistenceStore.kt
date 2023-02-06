package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.service.core.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyPersistenceStore<T, E> : PersistenceStore<T, E> {
    override fun key(): Mono<out Key<T>> = empty()

    override fun add(ent: E): Mono<Void> = empty()

    override fun rem(key: Key<T>): Mono<Void> = empty()

    override fun get(key: Key<T>): Mono<out E> = empty()

    override fun all(): Flux<out E> = Flux.empty()
}