package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.core.PersistenceStore
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceStoreMapping<T, E> : PersistenceStore<T, E> {
    @MessageMapping("key")
    override fun key(): Mono<out Key<T>>

    @MessageMapping("add")
    override fun add(ent: E): Mono<Void>

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void>

    @MessageMapping("get")
    override fun get(key: Key<T>): Mono<out E>

    @MessageMapping("all")
    override fun all(): Flux<out E>
}

interface KeyValueStoreMapping<T> :
    PersistenceStoreMapping<T, KeyValuePair<T, Any>>,
    KeyValueStore<T, Any> {

    @MessageMapping("typedAll")
    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>>

    @MessageMapping("typedByIds")
    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>>

    @MessageMapping("typedGet")
    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>>
}