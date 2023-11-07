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

    override fun hasAccessByKey(principal: Key<T>, key: Key<T>, perm: String): Mono<Boolean> =
        collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(principal, key), perm)

    override fun hasAccessByPrincipal(principal: Mono<Key<T>>, target: Key<T>, perm: String): Mono<Boolean> {
        return principal
            .flatMap { pKey ->
                collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(pKey, target), perm)
            }
    }

    override fun hasAccessByManyKeys(principal: Key<T>, keys: List<Key<T>>, perm: String): Mono<Boolean> =
        collectPermissionsAndProceed(authMan.getAuthorizationsAgainstMany(principal, keys), perm)

    override fun hasAccessManyByPrincipal(principal: Mono<Key<T>>, targets: List<Key<T>>, perm: String): Mono<Boolean> {
        return principal
            .flatMap { pKey ->
                collectPermissionsAndProceed(authMan.getAuthorizationsAgainstMany(pKey, targets), perm)
            }
    }

}