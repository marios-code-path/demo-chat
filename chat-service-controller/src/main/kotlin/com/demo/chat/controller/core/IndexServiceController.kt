package com.demo.chat.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.controller.core.mapping.IndexServiceMapping
import com.demo.chat.service.IndexService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexServiceController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexServiceMapping<T, E, Q> {
    override fun add(entity: E): Mono<Void> = that.add(entity)
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)
    override fun findBy(query: Q): Flux<out Key<T>> = that.findBy(query)
    override fun findUnique(query: Q): Mono<out Key<T>> = that.findUnique(query)
}