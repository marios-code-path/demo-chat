package com.demo.chat.security.access

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class AuthMetadataAccessBroker<T>(
    private val authMan: AuthorizationService<T, AuthMetadata<T>>,
) : AccessBroker<T> {

    private fun collectPermissionsAndProceed(meta: Flux<out AuthMetadata<T>>, perm: String): Mono<Boolean> {
        return meta
            .map { authMeta -> authMeta.permission }
            .collect(Collectors.toList())
            .map { permissions -> permissions.contains(perm) }
            .flatMap { canProceed ->
                when (canProceed) {
                    true -> Mono.just(true)
                    else -> Mono.just(false)
                }
            }
            .switchIfEmpty(Mono.just(false))
    }

    /**
     * **A key holds every right over itself.** The owner decided this on
     * 2026-09-24, under `CHAT-ixzpkqxg`. No row grants it and no row removes it,
     * so an expired wildcard or a close does not reach it.
     *
     * A row whose principal equals its target is not needed for this. Since
     * `CHAT-ixzpkqxg` such a row also reaches its owner alone, because
     * `CoreAuthorizationService` never puts a target key in the actor set.
     */
    private fun isSelf(principal: Key<T>, target: Key<T>): Boolean = principal == target

    override fun hasAccessByKey(principal: Key<T>, key: Key<T>, perm: String): Mono<Boolean> =
        if (isSelf(principal, key)) Mono.just(true)
        else collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(principal, key, perm), perm)

    override fun hasAccessByPrincipal(principal: Mono<Key<T>>, target: Key<T>, perm: String): Mono<Boolean> =
        principal.flatMap { pKey -> hasAccessByKey(pKey, target, perm) }

    /**
     * Self authority covers the principal alone in the list. The grants of every
     * other target are read as before, so adding the principal to a list never
     * widens the answer for the rest.
     */
    override fun hasAccessByManyKeys(principal: Key<T>, keys: List<Key<T>>, perm: String): Mono<Boolean> {
        val others = keys.filterNot { isSelf(principal, it) }

        return if (keys.isNotEmpty() && others.isEmpty()) Mono.just(true)
        else collectPermissionsAndProceed(authMan.getAuthorizationsAgainstMany(principal, others, perm), perm)
    }

    override fun hasAccessManyByPrincipal(principal: Mono<Key<T>>, targets: List<Key<T>>, perm: String): Mono<Boolean> =
        principal.flatMap { pKey -> hasAccessByManyKeys(pKey, targets, perm) }
}