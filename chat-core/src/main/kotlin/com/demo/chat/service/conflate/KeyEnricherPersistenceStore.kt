package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Mono

open class KeyEnricherPersistenceStore<T, V, E>(
    val store: PersistenceStore<T, E>,
    val enricher: (data: V, key: Key<T>) -> E,
) : EnricherPersistenceStore<T, V, E>, PersistenceStore<T, E> by store {
    override fun addEnriched(ent: V): Mono<E> = store
        .key()
        .map { enricher(ent, it) }
        .flatMap { entity ->
            store
                .add(entity)
                .thenReturn(entity)
        }
}