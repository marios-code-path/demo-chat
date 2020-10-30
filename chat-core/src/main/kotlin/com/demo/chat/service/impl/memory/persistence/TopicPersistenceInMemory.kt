package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.IKeyService
import com.demo.chat.service.TopicPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class TopicPersistenceInMemory<T>(val keyService: IKeyService<T>) : TopicPersistence<T> {
    private val map = ConcurrentHashMap<T, MessageTopic<T>>()

    override fun key(): Mono<out Key<T>> = keyService.key(MessageTopic::class.java)

    override fun add(ent: MessageTopic<T>): Mono<Void> = Mono.create {
        map[ent.key.id] = ent
        it.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        map.remove(key.id)
        it.success()
    }

    override fun get(key: Key<T>): Mono<out MessageTopic<T>> = Mono.create {
        it.success(map[key.id])
    }

    override fun all(): Flux<out MessageTopic<T>> = Flux.fromIterable(map.values.asIterable())
}
