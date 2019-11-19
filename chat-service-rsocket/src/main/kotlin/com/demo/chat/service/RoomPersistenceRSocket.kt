package com.demo.chat.service

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.Room
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class RoomPersistenceRSocket(val that: ChatRoomPersistence) : ChatRoomPersistence {

    @MessageMapping("key")
    override fun key(): Mono<out EventKey> = that.key()

    @MessageMapping("add")
    override fun add(ent: Room): Mono<Void> = that.add(ent)

    @MessageMapping("rem")
    override fun rem(key: EventKey): Mono<Void> = that.rem(key)

    @MessageMapping("get")
    override fun get(key: EventKey): Mono<out Room> = that.get(key)

    @MessageMapping("all")
    override fun all(): Flux<out Room> = that.all()

}

open class ChatPersistenceRSocket<T>(val that: ChatPersistence<T>) : ChatPersistence<T> {
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