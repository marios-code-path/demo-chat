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
    /**
     * The domain root that covers the principal of a call.
     *
     * **Every caller is a user.** `ContextIdentity` answers the key of a user
     * or the `Anon` root key, and `Anon` is an object of the `User` domain. So
     * the domain root of a principal is always the `User` root, and this
     * parameter needs no domain on the key.
     *
     * A row that names the `User` root as its principal reaches every caller
     * through this key. The four `user: User` rows of `userinit.yml` reach a
     * caller for the first time, and a close row does too.
     *
     * **This is the principal side alone.** The target side still reads one
     * target, so a grant on a domain root does not cover an object of that
     * domain. `CHAT-rfzsnbco` carries that, and it does need a domain on the
     * key.
     */
    private val principalRootKey: Supplier<out Key<T>>,
    private val summarizer: Summarizer<AuthMetadata<T>, Key<T>>
) : AuthorizationService<T, AuthMetadata<T>> {

    /**
     * The principals whose rows reach a caller: the anonymous key, the principal
     * domain root, and the caller.
     *
     * **A target key is never an actor.** It was one until `CHAT-ixzpkqxg`, so a
     * row whose principal equals its target passed this filter for every caller
     * that asked about that target. The authority of a key over itself is a rule
     * in `AuthMetadataAccessBroker`, not a row.
     */
    private fun actors(vararg keys: Key<T>): Sequence<Key<T>> =
        sequenceOf(anonKey.get(), principalRootKey.get()) + keys.asSequence()

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
            actors()
        )

    fun getAuthorizationsForMultiplePrincipal(uids: List<Key<T>>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uids.map { authIndex.findBy(queryForPrinciple.apply(it)).flatMap(authPersist::get) }),
            actors() + uids
        )

    override fun getAuthorizationsForTarget(uid: Key<T>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex.findBy(queryForTarget.apply(uid)).flatMap(authPersist::get),
            actors()
        )

    override fun getAuthorizationsForPrincipal(uid: Key<T>): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex
                .findBy(queryForPrinciple.apply(uid)).flatMap(authPersist::get),
            actors(uid)
        )

    override fun getAuthorizationsAgainst(uidA: Key<T>, uidB: Key<T>, permission: String?): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            authIndex.findBy(queryForTarget.apply(uidB)).flatMap(authPersist::get),
            actors(uidA),
            permission
        )

    override fun getAuthorizationsAgainstMany(uidA: Key<T>, uidB: List<Key<T>>, permission: String?): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uidB.map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            actors(uidA),
            permission
        )
}