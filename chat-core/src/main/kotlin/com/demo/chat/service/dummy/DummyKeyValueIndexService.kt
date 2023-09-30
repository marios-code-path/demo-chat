package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.service.core.KeyValueIndexService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class DummyKeyValueIndexService<T, Q> : KeyValueIndexService<T, Q> {
    override fun add(entity: KeyValuePair<T, Any>): Mono<Void> = Mono.empty()

    override fun rem(key: Key<T>): Mono<Void> = Mono.empty()

    override fun findBy(query: Q): Flux<out Key<T>> = Flux.empty()

    override fun findUnique(query: Q): Mono<out Key<T>> = Mono.empty()
}