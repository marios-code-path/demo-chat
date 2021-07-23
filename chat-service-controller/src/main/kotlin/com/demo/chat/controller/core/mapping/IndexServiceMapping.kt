package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.*
import com.demo.chat.service.IndexService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface IndexServiceMapping<T, E, Q> : IndexService<T, E, Q> {
    @MessageMapping("add")
    override fun add(entity: E): Mono<Void>
    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void>
    @MessageMapping("query")
    override fun findBy(query: Q): Flux<out Key<T>>
    @MessageMapping("unique")
    override fun findUnique(query: Q): Mono<out Key<T>>
}