package com.demo.chat.controller.service

import com.demo.chat.domain.EventKey
import com.demo.chat.service.ChatPersistence
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

open class PersistenceServiceController<T>(val that: ChatPersistence<T>) : ChatPersistence<T> {
    @MessageMapping("key")
    override fun key(): Mono<out EventKey> = that.key()

    @MessageMapping("add")
    override fun add(ent: T): Mono<Void> = that.add(ent)

    @MessageMapping("rem")
    override fun rem(key: EventKey): Mono<Void> = that.rem(key)

    @MessageMapping("get")
    override fun get(key: EventKey): Mono<out T> = that.get(key)

    @MessageMapping("all")
    override fun all(): Flux<out T> = that.all()
}