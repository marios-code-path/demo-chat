package com.demo.chat.secure.access

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class AuthMetadataAccessBroker<T>(
    private val authMan: AuthorizationService<T, AuthMetadata<T>>,
) : AccessBroker<T> {

    private fun collectPermissionsAndProceed(meta: Flux<out AuthMetadata<T>>, perm: String): Mono<Void> {
        return meta
            .switchIfEmpty(Mono.error(AccessDeniedException))
            .map { authMeta -> authMeta.permission }
            .collect(Collectors.toList())
            .map { permissions -> permissions.contains(perm) }
            //.cache(Duration.ofMillis(1000))
            .flatMap { canProceed ->
                when (canProceed) {
                    true -> Mono.empty()
                    else -> Mono.defer {
                        Mono.error(AccessDeniedException)
                    }
                }
            }
    }

    override fun getAccess(principal: Key<T>, key: Key<T>, action: String): Mono<Void> =
        collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(principal, key), action)

    override fun getAccessFromPublisher(principal: Mono<Key<T>>, target: Key<T>, perm: String): Mono<Void> = principal
        .flatMap { pKey ->
            collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(pKey, target), perm)
        }
}