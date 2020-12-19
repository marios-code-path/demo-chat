package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.service.IndexService
import com.demo.chat.service.LoadableService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class LoadablePersistedIndex<T, E, Q>(
        val persistence: PersistenceStore<T, E>,
        val index: IndexService<T, E, Q>,
) : IndexService<T, E, Q>, LoadableService {
    override fun load() = persistence
                    .all()
                    .flatMap(::add)
                    .then()

    override fun add(entity: E): Mono<Void> = index.add(entity)

    override fun rem(key: Key<T>): Mono<Void> = index.rem(key)

    override fun findBy(query: Q): Flux<out Key<T>> = index.findBy(query)
}