package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IndexService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyIndexService<T, E, Q> : IndexService<T, E, Q> {
    override fun add(entity: E): Mono<Void> = empty()

    override fun rem(key: Key<T>): Mono<Void> = empty()

    override fun findBy(query: Q): Flux<out Key<T>> = Flux.empty()

    override fun findUnique(query: Q): Mono<out Key<T>> = empty()

}

