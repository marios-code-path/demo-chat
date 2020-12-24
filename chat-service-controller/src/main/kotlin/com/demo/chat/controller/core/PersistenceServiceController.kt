package com.demo.chat.controller.core

import com.demo.chat.domain.Key
import com.demo.chat.service.PersistenceStore
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class PersistenceServiceController<T, E>(private val that: PersistenceStore<T, E>) : PersistenceStore<T, E> by that{
    @MessageMapping("assemble")
    override fun assemble(ent: E): Mono<E> = that.assemble(ent)

    @MessageMapping("key")
    override fun key(): Mono<out Key<T>> = that.key()

    @MessageMapping("add")
    override fun add(ent: E): Mono<Void> = that.add(ent)

    @MessageMapping("rem")
    override fun rem(key: Key<T>): Mono<Void> = that.rem(key)

    @MessageMapping("get")
    override fun get(key: Key<T>): Mono<out E> = that.get(key)

    @MessageMapping("all")
    override fun all(): Flux<out E> = that.all()
}