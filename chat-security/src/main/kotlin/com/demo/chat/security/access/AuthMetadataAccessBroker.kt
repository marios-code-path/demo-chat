package com.demo.chat.security.access

import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.core.VerifiedKey

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.service.security.AccessBroker
import com.demo.chat.service.security.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.stream.Collectors

class AuthMetadataAccessBroker<T>(
    private val authMan: AuthorizationService<T, AuthMetadata<T>>,
    private val verifier: KeyVerifier<T>,
) : AccessBroker<T> {

    /**
     * A raw id carries no root, so this method resolves both ids first. The
     * principal resolves in USER, and the target in its stored domain. A
     * failed resolution denies. See `CHAT-avduuqwp`, C4.
     */
    override fun hasAccessByKeyId(principal: T, key: T, action: String): Mono<Boolean> =
        Mono.zip(verifier.resolve(principal, ChatDomain.USER), verifier.resolve(key, null))
            .flatMap { hasAccessByKey(it.t1.key, it.t2, action) }
            .onErrorReturn(false)
            .defaultIfEmpty(false)

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

    /**
     * A key reaches [isSelf] only as a [VerifiedKey]. That key came from
     * `KeyVerifier.verify`, `KeyVerifier.resolve`, `KeyVerifier.domainRoot`, or
     * the one trusted conversion `trustTypedStore`. See `CHAT-avduuqwp`, D5.
     */
    override fun hasAccessByKey(principal: Key<T>, target: VerifiedKey<T>, perm: String): Mono<Boolean> =
        if (isSelf(principal, target.key)) Mono.just(true)
        else collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(principal, target.key, perm), perm)

    override fun hasAccessByPrincipal(principal: Mono<Key<T>>, target: VerifiedKey<T>, perm: String): Mono<Boolean> =
        principal.flatMap { pKey -> hasAccessByKey(pKey, target, perm) }
}