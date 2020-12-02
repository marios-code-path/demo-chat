package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexedPersistence<T, E, Q>(
        val persistence: PersistenceStore<T, E>,
        val index: IndexService<T, E, Q>,
) : PersistenceStore<T, E> {
    override fun key(): Mono<out Key<T>> = persistence.key()

    override fun add(ent: E): Mono<Void> = Flux.concat(
            persistence.add(ent),
            index.add(ent)
    ).last()

    override fun rem(key: Key<T>): Mono<Void> = Flux.concat(
            persistence.rem(key),
            index.rem(key)
    ).last()

    override fun get(key: Key<T>): Mono<out E> = persistence.get(key)

    override fun all(): Flux<out E> = persistence.all()
}