package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MessagePersistence
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class MessagePersistenceInMemory<T, V>(val keyService: IKeyService<T>) : PersistenceStore<T, Message<T, V>> {
    private val map = ConcurrentHashMap<T, Message<T, V>>()

    override fun key(): Mono<out Key<T>> = keyService.key(Message::class.java)

    override fun add(ent: Message<T,  V>): Mono<Void> = Mono.create {
        map[ent.key.id] = ent
        it.success()
    }

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        map.remove(key.id)
        it.success()
    }

    override fun get(key: Key<T>): Mono<out Message<T, V>> = Mono.create {
        it.success(map[key.id])
    }

    override fun all(): Flux<out Message<T, V>> = Flux.fromIterable(map.values.asIterable())
}