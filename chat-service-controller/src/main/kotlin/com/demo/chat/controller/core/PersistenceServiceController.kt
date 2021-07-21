package com.demo.chat.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.controller.core.mapping.PersistenceStoreMapping
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class PersistenceServiceController<T, E>(private val that: PersistenceStore<T, E>) : PersistenceStoreMapping<T, E> {
    override fun assemble(ent: E): Mono<out E> = that.assemble(ent)

    override fun key(): Mono<out Key<T>> = that.key()

    override fun add(ent: E): Mono<Void> = that.add(ent)

    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    override fun get(key: Key<T>): Mono<out E> = that.get(key)

    override fun all(): Flux<out E> = that.all()
}