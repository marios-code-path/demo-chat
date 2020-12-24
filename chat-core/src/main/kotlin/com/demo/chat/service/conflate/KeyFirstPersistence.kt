package com.demo.chat.service.conflate

import com.demo.chat.domain.Key
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class KeyFirstPersistence<T, E>(
        val store: PersistenceStore<T, E>,
        val assembler: (ent: E, key: Key<T>) -> E,
) : PersistenceStore<T, E> by store {
    override fun assemble(ent: E): Mono<E> = store
            .key()
            .flatMap { key ->
                val assembled = assembler(ent, key)

                store
                        .add(assembled)
                        .map { assembled }
            }
}