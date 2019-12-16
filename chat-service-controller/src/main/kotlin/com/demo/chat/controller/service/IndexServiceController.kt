package com.demo.chat.controller.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.service.IndexService
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

open class IndexServiceController<K, in T, Q, WQ>(val that: IndexService<K, T, Q, WQ>) : IndexService<K, T, Q, WQ> {
    @MessageMapping("add")
    override fun add(entity: T, criteria: WQ): Mono<Void> = that.add(entity, criteria)

    @MessageMapping("rem")
    override fun rem(entity: T): Mono<Void> = that.rem(entity)

    @MessageMapping("query")
    override fun findBy(query: Q): Flux<out Key<K>> = that.findBy(query)
}