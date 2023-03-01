package com.demo.chat.secure.service.core

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthorizationService
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*
import java.util.stream.Collectors


class AccessPersistenceStore<T, E, Q>(
    private val authUtil: AuthorizationUtil<T, Q>,
    private val kind: Class<E>,
    private val principalKeyFromEntity: (Optional<E>) -> Key<T>,
    private val targetKeyFromEntity: (E) -> Key<T>,
    private val rootKeys: RootKeys<T>,
    private val that: PersistenceStore<T, E>
) : PersistenceStore<T, E> {
    override fun key(): Mono<out Key<T>> = that.key()

    override fun all(): Flux<out E> =
        Flux.from(authUtil.getAccess(
            principalKeyFromEntity(Optional.empty()), rootKeys.getRootKey(kind), "READ")
        ).thenMany(that.all())

    override fun get(key: Key<T>): Mono<out E> = that.get(key)

    override fun rem(key: Key<T>): Mono<Void> = Mono
        .from(authUtil.getAccess(principalKeyFromEntity(Optional.empty()), key, "DELETE"))
        .then(that.rem(key))

    override fun add(ent: E): Mono<Void> = Mono
        .from(authUtil.getAccess(principalKeyFromEntity(Optional.of(ent)), targetKeyFromEntity(ent), "CREATE"))
        .then(that.add(ent))
}

class AuthorizationUtil<T, Q>(
    private val authMan: AuthorizationService<T, AuthMetadata<T>, Q>,
) {

    private fun collectPermissionsAndProceed(meta: Flux<out AuthMetadata<T>>, perm: String): Publisher<Void> {
        return meta
            .switchIfEmpty(Mono.error(AccessDeniedException))
            .map { authMeta -> authMeta.permission }
            .collect(Collectors.toList())
            .map { permissions -> permissions.contains(perm) }
            //.cache(Duration.ofMillis(1000))
            .flatMap { canProceed ->
                when (canProceed) {
                    true -> Mono.empty()
                    else -> Mono.error(AccessDeniedException)
                }
            }
    }
    fun getAccess(principal: Key<T>, target: Key<T>, perm: String): Publisher<Void> = Mono.empty()

    fun getObjectPermissionAccess(principal: Key<T>, target: Key<T>, perm: String): Publisher<Void> {
        return collectPermissionsAndProceed(authMan.getAuthorizationsAgainst(principal, target), perm)
    }
}

