package com.demo.chat.service.memory

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class MembershipPersistenceInMemory<T>(val keyService: IKeyService<T>)
    : MembershipPersistence<T> {
    private val map = ConcurrentHashMap<T, TopicMembership<T>>()

    override fun key(): Mono<out Key<T>> = keyService.key(TopicMembership::class.java)

    override fun add(ent: TopicMembership<T>): Mono<Void> = Mono.create {
        map[ent.key] = ent
        it.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        map.remove(key.id)
        it.success()
    }

    override fun get(key: Key<T>): Mono<out TopicMembership<T>> = Mono.create {
        it.success(map[key.id])
    }

    override fun all(): Flux<out TopicMembership<T>> = Flux.fromIterable(map.values.asIterable())
}