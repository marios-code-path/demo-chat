package com.demo.chat.client.rsocket.clients

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.UserPersistence
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.service.RSocketExchange
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface UserPersistenceClientProxy: UserPersistence<UUID> {
    @RSocketExchange("persistence.key")
    override fun key(): Mono<out Key<UUID>>

    @RSocketExchange("persistence.all")
    override fun all(): Flux<out User<UUID>>

    @RSocketExchange("persistence.get")
    override fun get(@Payload key: Key<UUID>): Mono<out User<UUID>>

    @RSocketExchange("persistence.rem")
    override fun rem(@Payload key: Key<UUID>): Mono<Void>

    @RSocketExchange("persistence.add")
    override fun add(@Payload ent: User<UUID>): Mono<Void>
}

interface PersistenceStoreClientProxy<T, E> : PersistenceStore<T, E>{
    @RSocketExchange("persistence.key")
    override fun key(): Mono<out Key<T>>

    @RSocketExchange("persistence.all")
    override fun all(): Flux<out E>

    @RSocketExchange("persistence.get")
    override fun get(@Payload key: Key<T>): Mono<out E>

    @RSocketExchange("persistence.rem")
    override fun rem(@Payload key: Key<T>): Mono<Void>

    @RSocketExchange("persistence.add")
    override fun add(@Payload ent: E): Mono<Void>
}
