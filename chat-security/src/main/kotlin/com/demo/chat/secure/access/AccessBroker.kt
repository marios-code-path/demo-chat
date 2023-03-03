package com.demo.chat.secure.access

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface AccessBroker<T> {
    fun getAccess(principal: Mono<Key<T>>, key: Key<T>, action: String): Mono<Void>
}