package com.demo.chat.controller.core.mapping

import com.demo.chat.domain.Key
import com.demo.chat.service.PersistenceStore
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