package com.demo.chat.controller.webflux.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueStore
import org.springframework.web.bind.annotation.GetMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface KeyValueStoreRestMapping<T> :
    PersistenceRestMapping<T, KeyValuePair<T, Any>>,
    KeyValueStore<T, Any> {

    @GetMapping("/all")
    override fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T, E>>

    @GetMapping("/byIds")
    override fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T, E>>

    @GetMapping("/get")
    override fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T, E>>
}