package com.demo.chat.service


import com.demo.chat.domain.EventKey
import com.demo.chat.domain.User
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class UserPersistenceRSocket(val that: ChatUserPersistence) : ChatUserPersistence {
    @MessageMapping("key")
    override fun key(): Mono<out EventKey> = that.key()

    @MessageMapping("add")
    override fun add(ent: User): Mono<Void> = that.add(ent)

    @MessageMapping("rem")
    override fun rem(key: EventKey): Mono<Void> = that.rem(key)

    @MessageMapping("get")
    override fun get(key: EventKey): Mono<out User> = that.get(key)

    @MessageMapping("all")
    override fun all(): Flux<out User> = that.all()
}