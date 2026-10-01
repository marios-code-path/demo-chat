package com.demo.chat.security.service

import com.demo.chat.service.core.KeyVerifier

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
    private val summarizer: Summarizer<AuthMetadata<T>, Key<T>>,
    private val verifier: KeyVerifier<T>,
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

    /**
     * A grant write verifies its principal and its target first. Each resolves
     * in its stored domain, because a grant can name a user, a domain root, or
     * an object. A forged root or an unknown key fails before the write. A
     * revoke removes by the grant key alone, so it writes no party. See
     * `CHAT-avduuqwp`, T6.
     */
    override fun authorize(auth: AuthMetadata<T>, exist: Boolean): Mono<Void> =
        (if (exist) verifyParties(auth) else Mono.empty())
            .then(Mono.defer { write(auth, exist) })

    private fun verifyParties(auth: AuthMetadata<T>): Mono<Void> =
        verifier.verify(auth.principal, null)
            .then(verifier.verify(auth.target, null))
            .then()

    private fun write(auth: AuthMetadata<T>, exist: Boolean): Mono<Void> = when (auth.key.empty) {
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

    /**
     * The targets that a permission check reads: the given target, and the
     * domain root of that target.
     *
     * A root key is its own root, so a check that already names a domain root
     * reads one target. The list is distinct for that reason. See
     * `CHAT-rfzsnbco`.
     *
     * **The owner selection does not use this method.** A domain root read
     * there would give one target two owners.
     */
    private fun targets(uidB: Key<T>): List<Key<T>> {
        val root = Key.root(uidB.root)
        return if (root == uidB) listOf(uidB) else listOf(uidB, root)
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
            Flux.concat(targets(uidB).map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            actors(uidA),
            permission
        )

    override fun getAuthorizationsAgainstMany(uidA: Key<T>, uidB: List<Key<T>>, permission: String?): Flux<AuthMetadata<T>> = summarizer
        .computeAggregates(
            Flux.concat(uidB.flatMap { targets(it) }.map { authIndex.findBy(queryForTarget.apply(it)).flatMap(authPersist::get) }),
            actors(uidA),
            permission
        )
}