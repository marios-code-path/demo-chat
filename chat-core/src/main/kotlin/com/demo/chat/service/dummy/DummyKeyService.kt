package com.demo.chat.service.dummy

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.empty

open class DummyKeyService<T> : IKeyService<T> {
    override fun key(domain: ChatDomain): Mono<out Key<T>> = empty()

    override fun rem(key: Key<T>): Mono<Void> = empty()

    override fun exists(key: Key<T>): Mono<Boolean> = Mono.just(false)

    override fun rootOf(id: T): Mono<T & Any> = empty()
}
