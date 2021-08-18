package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexedPersistence<T, V, E, Q>(
    val persistence: EnricherPersistenceStore<T, V, E>,
    val index: IndexService<T, E, Q>
) : EnricherPersistenceStore<T, V, E> by persistence {
    override fun add(ent: E): Mono<Void> = Flux.concat(
        persistence.add(ent),
        index.add(ent)
    ).last()

    override fun rem(key: Key<T>): Mono<Void> = Flux.concat(
        persistence.rem(key),
        index.rem(key)
    ).last()
}