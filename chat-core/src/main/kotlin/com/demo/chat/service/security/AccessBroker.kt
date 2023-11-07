package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Mono

interface AccessBroker<T> {

    fun hasAccessByPrincipal(principal: Mono<Key<T>>, key: Key<T>, action: String): Mono<Boolean>
    fun hasAccessManyByPrincipal(principal: Mono<Key<T>>, targets: List<Key<T>>, perm: String): Mono<Boolean>
    fun hasAccessByKey(principal: Key<T>, key: Key<T>, action: String): Mono<Boolean>
    fun hasAccessByManyKeys(principal: Key<T>, keys: List<Key<T>>, perm: String): Mono<Boolean>
    fun hasAccessByKeyId(principal: T, key: T, action: String): Mono<Boolean> =
        hasAccessByKey(Key.funKey(principal), Key.funKey(key), action)
}