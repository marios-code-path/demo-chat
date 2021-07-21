package com.demo.chat.secure.service

import com.demo.chat.domain.Key
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.AuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Supplier

class AbstractAuthorizationImpl<T, M, G, Q>(
    private val authPersist: PersistenceStore<T, M>,
    private val authIndex: IndexService<T, M, Q>,
    private val authQueryForID: Function<in Key<T>, Q>,
    private val idForPrincipal: Function<M, out Key<T>>,
    private val idForTarget: Function<M, out Key<T>>,
    private val anonKey: Supplier<out Key<T>>,
    private val keyForAuth: Function<M, Key<T>>,
    private val grouper: Function<M, G>,
    private val reducer: BiFunction<M, M, M>
) : AuthorizationService<T, M, M> {
    override fun authorize(authorization: M, exist: Boolean): Mono<Void> =
        when (exist) {
            true -> authIndex.add(authorization)
            else -> authIndex.rem(keyForAuth.apply(authorization))
        }

    override fun getAuthorizationsFor(uid: Key<T>): Flux<M> = authIndex
        .findBy(authQueryForID.apply(uid))
        .flatMap(authPersist::get)

    override fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<M> =
        Flux.merge(
            getAuthorizationsFor(anonKey.get()),
            getAuthorizationsFor(uidA),
            getAuthorizationsFor(uidB)
        )
            .filter {
                val pid = idForPrincipal.apply(it)
                val tid = idForTarget.apply(it)
                (uidA == pid && uidB == tid) ||
                        (pid == anonKey.get() && tid == anonKey.get()) ||
                        (pid == anonKey.get() && tid == uidB) ||
                        (pid == uidA && tid == uidA)
            }
            .groupBy(grouper)
            .flatMap { g -> g.reduce(reducer) }
}
