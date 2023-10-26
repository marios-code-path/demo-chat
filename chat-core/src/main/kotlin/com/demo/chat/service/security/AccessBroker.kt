package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface AccessBroker<T> {
    fun getAccessFromPublisher(principal: Mono<Key<T>>, key: Key<T>, action: String): Mono<Boolean>
    fun getAccess(principal: Key<T>, key: Key<T>, action: String): Mono<Boolean>
    fun getAccess(principal: T, key: T, action: String): Mono<Boolean> =
        getAccess(Key.funKey(principal), Key.funKey(key), action)
}