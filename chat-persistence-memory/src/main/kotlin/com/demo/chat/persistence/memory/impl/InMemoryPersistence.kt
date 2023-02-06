package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

open class InMemoryPersistence<T, E>(
    private val keyService: IKeyService<T>,
    private val entityClass: Class<*>,
    private val keyFromEntity: Function<E, Key<T>>
) : PersistenceStore<T, E> {
    val map = ConcurrentHashMap<T, E>()

    override fun key(): Mono<out Key<T>> = keyService.key(entityClass)

    @Synchronized
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

