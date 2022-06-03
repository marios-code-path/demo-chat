package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

open class InMemoryPersistence<T, E>(
    val keyService: IKeyService<T>,
    val entityClass: Class<*>,
    val keyFromEntity: Function<E, Key<T>>
) : PersistenceStore<T, E> {
    val map = ConcurrentHashMap<T, E>()

    override fun key(): Mono<out Key<T>> = keyService.key(entityClass)

    override fun add(ent: E): Mono<Void> = Mono.create {
        map[keyFromEntity.apply(ent).id] = ent
        it.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        map.remove(key.id!!)
        it.success()
    }

    override fun get(key: Key<T>): Mono<out E> = Mono.create {
        when (map.containsKey(key.id)) {
            true -> it.success(map[key.id])
            else -> it.success()
        }
    }

    override fun all(): Flux<out E> = Flux.fromIterable(map.values.asIterable())
}