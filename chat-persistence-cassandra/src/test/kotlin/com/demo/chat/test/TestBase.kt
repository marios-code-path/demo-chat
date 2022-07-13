package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import reactor.core.publisher.Mono
import java.util.*

object TestKeyService : IKeyService<UUID> {
    override fun exists(key: Key<UUID>): Mono<Boolean> = Mono.just(true)

    override fun <T> key(kind: Class<T>): Mono<out Key<UUID>> = Mono.just(Key.funKey(UUID.randomUUID()))

    override fun rem(key: Key<UUID>): Mono<Void> = Mono.never()
}