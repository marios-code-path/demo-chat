package com.demo.chat.service

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface  IKeyService <K> {
    fun <T> key(kind: Class<T>): Mono<out Key<K>>
    fun rem(key: Key<K>): Mono<Void>
    fun exists(key: Key<K>): Mono<Boolean>
}