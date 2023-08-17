package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.Message
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.MessagePersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class InMemoryKeyValueStore<T>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<KeyValuePair<T, Any>, Key<T>>,
) : InMemoryPersistence<T, KeyValuePair<T, Any>>(keyService, KeyValuePair::class.java, keyFromEntity),
    KeyValueStore<T, Any> {
    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> =
        Mono.create {
            when (map.containsKey(key.id)) {
                true -> it.success(KeyValuePair.create(map[key.id]!!.key, map[key.id]!!.data as E))
                else -> it.success()
            }
        }

    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        Flux.create { sink ->
            map.values.forEach { value ->
                sink.next(KeyValuePair.create(value.key, value.data as E))
            }

            sink.complete()
        }

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> =
        Flux.create { sink ->
            ids.forEach { key ->
                if (map.containsKey(key.id)) {
                    sink.next(KeyValuePair.create(map[key.id]!!.key, map[key.id]!!.data as E))
                }
            }

            sink.complete()
        }
    }