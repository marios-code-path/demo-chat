package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

open class KeyServiceInMemory<T>(private val keyGen: Supplier<T>) : IKeyService<T> {
    private val map = ConcurrentHashMap<T, Key<T>>()

    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = Mono.just(
            Key.funKey(keyGen.get()).apply {
                map[this.id] = this
            }
    )

    override fun rem(key: Key<T>): Mono<Void> = Mono.create {
        try {
            map.remove(key.id)
            it.success()
        } catch (ex: Exception) {
            it.error(ex)
        }
    }

    override fun exists(key: Key<T>): Mono<Boolean> = Mono.create {
        it.success(map.containsKey(key.id))
    }
}