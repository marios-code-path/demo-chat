package com.demo.chat.controllers.service

import com.demo.chat.domain.EventKey
import com.demo.chat.service.IndexService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class IndexServiceController<out K : EventKey, in T, Q, WQ>(val that: IndexService<K, T, Q, WQ>) : IndexService<K, T, Q, WQ> {
    @MessageMapping("add")
    override fun add(entity: T, criteria: WQ): Mono<Void> = Mono.empty()

    @MessageMapping("rem")
    override fun rem(entity: T): Mono<Void> = Mono.empty()

    @MessageMapping("query")
    override fun findBy(query: Q): Flux<out K> = Flux.empty()
}