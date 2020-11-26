package com.demo.chat.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.service.IndexService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexServiceController<T, E, Q>(private val that: IndexService<T, E, Q>) : IndexService<T, E, Q> {
    @MessageMapping("add")
    override fun add(entity: E): Mono<Void> = that.add(entity)

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    @MessageMapping("query")
    override fun findBy(query: Q): Flux<out Key<T>> = that.findBy(query)
}