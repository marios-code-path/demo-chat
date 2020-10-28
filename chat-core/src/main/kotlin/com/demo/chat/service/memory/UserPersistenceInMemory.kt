package com.demo.chat.service.memory

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.UserPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class UserPersistenceInMemory<T>(val keyService: IKeyService<T>) : UserPersistence<T> {
    val map = ConcurrentHashMap<T, User<T>>()

    override fun key(): Mono<out Key<T>> = keyService
            .key(User::class.java)

    override fun add(ent: User<T>): Mono<Void> = Mono.create {
        map[ent.key.id] = ent
        it.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        map.remove(key.id)
        it.success()
    }

    override fun get(key: Key<T>): Mono<out User<T>> = Mono.create {
        when(map.contains(key.id)) {
            true -> it.success(map[key.id])
            else -> it.success()
        }
    }

    override fun all(): Flux<out User<T>> = Flux.fromIterable(map.values.asIterable())
}