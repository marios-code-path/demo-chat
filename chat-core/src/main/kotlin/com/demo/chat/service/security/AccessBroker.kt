package com.demo.chat.service.security

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AccessBroker<T> {

    fun hasAccessByPrincipal(principal: Mono<Key<T>>, key: Key<T>, action: String): Mono<Boolean>
    fun hasAccessByKey(principal: Key<T>, key: Key<T>, action: String): Mono<Boolean>
    fun hasAccessByKeyId(principal: T, key: T, action: String): Mono<Boolean> =
        hasAccessByKey(Key.funKey(principal), Key.funKey(key), action)

    /**
     * The targets that [principal] may reach with [perm], in the order given.
     *
     * **Each target is evaluated on its own**, through [hasAccessByKey]. A
     * denied target is left out. An empty list, or a list with no permitted
     * target, answers an empty result. See `CHAT-wkwiipgy`.
     *
     * One Boolean cannot carry this answer. The Boolean many target checks
     * that this replaced allowed a whole list when any one target had a grant.
     */
    fun permittedTargets(principal: Key<T>, targets: List<Key<T>>, perm: String): Flux<Key<T>> =
        Flux.fromIterable(targets)
            .concatMap { target -> hasAccessByKey(principal, target, perm).filter { it }.map { target } }
}
