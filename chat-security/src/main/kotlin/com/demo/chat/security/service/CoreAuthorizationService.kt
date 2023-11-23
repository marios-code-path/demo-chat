package com.demo.chat.security.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.security.Summarizer
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.security.AuthorizationService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.function.Supplier

/**
 * Base functionality for an authorization service where
 *
 * T = Key Type
 * M = AuthorizationMetaData Type
 * Q = Authorization Query Type
 */
class CoreAuthorizationService<T, Q>(
    private val authPersist: PersistenceStore<T, AuthMetadata<T>>,
    private val authIndex: IndexService<T, AuthMetadata<T>, Q>,
    private val queryForPrinciple: Function<in Key<T>, Q>,
    private val queryForTarget: Function<in Key<T>, Q>,
    private val anonKey: Supplier<out Key<T>>,
    private val summarizer: Summarizer<AuthMetadata<T>, Key<T>>
) : AuthorizationService<T, AuthMetadata<T>> {

    override fun authorize(auth: AuthMetadata<T>, exist: Boolean): Mono<Void> = when (auth.key.empty) {
        true -> authPersist
            .key()
            .map { key -> AuthMetadata.create(key, auth.principal, auth.target, auth.permission, auth.expires) }

        else -> Mono.just(auth)
    }
        .flatMap { authorization ->
            when (exist) {
                true -> authPersist
                    .add(authorization)
                    .then(authIndex.add(authorization))

                else -> authPersist.rem(authorization.key)
                    .then(authIndex.rem(authorization.key))
            }
        }

    private fun getAuthorizationsForMultipleTarget(uids: List<Key<T>>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uids.map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            sequenceOf(anonKey.get()) + uids
        )

    fun getAuthorizationsForMultiplePrincipal(uids: List<Key<T>>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uids.map { authIndex.findBy(queryForPrinciple.apply(it)).flatMap(authPersist::get) }),
            sequenceOf(anonKey.get()) + uids
        )

    override fun getAuthorizationsForTarget(uid: Key<T>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex.findBy(queryForTarget.apply(uid)).flatMap(authPersist::get),
            sequenceOf(anonKey.get(), uid)
        )

    override fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex
                .findBy(queryForPrinciple.apply(uid)).flatMap(authPersist::get),
            sequenceOf(anonKey.get(), uid)
        )

    override fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex.findBy(queryForTarget.apply(uidB)).flatMap(authPersist::get),
            sequenceOf(anonKey.get(), uidA, uidB)
        )

    override fun getAuthorizationsAgainstMany(uidA: Key<T>, uidB: List<Key<T>>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uidB.map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            sequenceOf(anonKey.get(), uidA) + uidB
        )
}