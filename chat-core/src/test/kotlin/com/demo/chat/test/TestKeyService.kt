package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import reactor.core.publisher.Mono

class TestKeyService : IKeyService<String> {
    override fun <S> key(kind: Class<S>): Mono<out Key<String>> =
            Mono.just(Key.funKey(randomAlphaNumeric(10)))

    override fun rem(key: Key<String>): Mono<Void> = Mono.empty()
    override fun exists(key: Key<String>): Mono<Boolean> = Mono.just(true)
}