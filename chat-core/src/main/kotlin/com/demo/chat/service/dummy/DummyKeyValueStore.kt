package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class DummyKeyValueStore<T> : DummyPersistenceStore<T, KeyValuePair<T, Any>>(), KeyValueStore<T, Any> {
    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>> = Flux.empty()

    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>> = Flux.empty()

    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>> = Mono.empty()
}