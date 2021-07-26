package com.demo.chat.secure.service

import com.demo.chat.domain.Key
import com.demo.chat.secure.Filterizer
import com.demo.chat.service.AuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier

class AbstractAuthorizationService<T, M, Q>(
    private val authPersist: PersistenceStore<T, M>,
    private val authIndex: IndexService<T, M, Q>,
    private val queryForPrinciple: Function<in Key<T>, Q>,
    private val queryForTarget: Function<in Key<T>, Q>,
    private val anonKey: Supplier<out Key<T>>,
    private val keyForAuth: Function<M, Key<T>>,
    private val filterizer: Filterizer<M, Key<T>>
) : AuthorizationService<T, M, M> {
    override fun authorize(authorization: M, exist: Boolean): Mono<Void> =
        when (exist) {
            true -> authPersist.add(authorization).then(authIndex.add(authorization))
            else -> authIndex.rem(keyForAuth.apply(authorization))
        }

    override fun getAuthorizationsForTarget(uid: Key<T>): Flux<M> = authIndex
        .findBy(queryForTarget.apply(uid))
        .flatMap(authPersist::get)

    override fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<M> = authIndex
        .findBy(queryForPrinciple.apply(uid))
        .flatMap(authPersist::get)

    override fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<M> = filterizer
        .filterize(
            getAuthorizationsForTarget(uidB),
            sequenceOf(anonKey.get(), uidA, uidB)
        )
}