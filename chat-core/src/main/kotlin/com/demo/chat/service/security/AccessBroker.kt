package com.demo.chat.service.security

import com.demo.chat.service.core.VerifiedKey

import com.demo.chat.domain.Key
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AccessBroker<T> {

    /**
     * The target is a [VerifiedKey], so a raw caller key cannot reach this
     * method. The principal comes from `ContextIdentity`, which reads a user
     * that a typed store returned. See `CHAT-avduuqwp`, D5.
     */
    fun hasAccessByPrincipal(principal: Mono<Key<T>>, target: VerifiedKey<T>, action: String): Mono<Boolean>
    fun hasAccessByKey(principal: Key<T>, target: VerifiedKey<T>, action: String): Mono<Boolean>
    /**
     * This method checks access for two raw ids. An implementation resolves
     * each id through `KeyVerifier` first, because a raw id carries no root.
     */
    fun hasAccessByKeyId(principal: T, key: T, action: String): Mono<Boolean>

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
    fun permittedTargets(principal: Key<T>, targets: List<VerifiedKey<T>>, perm: String): Flux<Key<T>> =
        Flux.fromIterable(targets)
            .concatMap { target -> hasAccessByKey(principal, target, perm).filter { it }.map { target.key } }
}
