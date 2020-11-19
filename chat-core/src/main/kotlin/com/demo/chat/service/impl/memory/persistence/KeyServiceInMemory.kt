package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.codec.Codec
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class KeyServiceInMemory<T>(private val keyGen: Codec<Unit, T>) : IKeyService<T> {
    val map = ConcurrentHashMap<T, Key<T>>()

    override fun <S> key(kind: Class<S>): Mono<out Key<T>> = Mono.just(
            Key.funKey(keyGen.decode(Unit)).apply {
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