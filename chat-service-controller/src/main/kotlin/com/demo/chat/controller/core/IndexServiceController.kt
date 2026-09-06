package com.demo.chat.controller.core

import com.demo.chat.controller.core.mapping.IndexServiceMapping
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.core.IndexService
import com.demo.chat.domain.Key
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexServiceController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexServiceMapping<T, E, Q>, IndexService<T, E, Q> by that

open class IndexSearchRequestIndexServiceController<T, E>(
    private val that: IndexService<T, E, IndexSearchRequest>
) : IndexService<T, E, IndexSearchRequest> by that {
    @MessageMapping("add")
    override fun add(entity: E): Mono<Void> = that.add(entity)

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    @MessageMapping("query")
    override fun findBy(query: IndexSearchRequest): Flux<out Key<T>> = that.findBy(query)

    @MessageMapping("unique")
    override fun findUnique(query: IndexSearchRequest): Mono<out Key<T>> = that.findUnique(query)
}

open class MapIndexServiceController<T, E>(
    private val that: IndexService<T, E, Map<String, String>>
) : IndexService<T, E, Map<String, String>> by that {
    @MessageMapping("add")
    override fun add(entity: E): Mono<Void> = that.add(entity)

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    @MessageMapping("query")
    override fun findBy(query: Map<String, String>): Flux<out Key<T>> = that.findBy(query)

    @MessageMapping("unique")
    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> = that.findUnique(query)
}
