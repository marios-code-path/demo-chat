package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.DuplicateException
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import java.util.function.Function

open class ComparatorInMemoryPersistence<T, E>(
    val keyService: IKeyService<T>,
    entityClass: Class<*>,
    private val keyFromEntity: Function<E, Key<T>>,
    private val comparator: Comparator<E>
) : InMemoryPersistence<T, E>(keyService, entityClass, keyFromEntity) {
    override fun add(ent: E): Mono<Void> = Mono.create { sink ->
        map.values.forEach { mapEntity ->
            if (comparator.compare(mapEntity, ent) == 0) {
                sink.error(DuplicateException)
                return@create
            }
        }

        map[keyFromEntity.apply(ent).id] = ent
        sink.success()
    }

}