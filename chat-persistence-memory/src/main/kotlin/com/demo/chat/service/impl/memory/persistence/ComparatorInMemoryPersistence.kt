package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import reactor.core.publisher.Mono
import java.util.function.Function

open class ComparatorInMemoryPersistence<T, E>(
    val keyService: IKeyService<T>,
    val entityClass: Class<*>,
    val keyFromEntity: Function<E, Key<T>>,
    private val comparator: Comparator<E>
) : InMemoryPersistence<T, E>(keyService, entityClass, keyFromEntity) {
    override fun add(ent: E): Mono<Void> = Mono.create { sink ->
        map.values.forEach {
            if (comparator.compare(it, ent) == 0) {
                sink.error(DuplicateException)
                return@create
            }
        }

        map[keyFromEntity.apply(ent).id] = ent
        sink.success()
    }

}