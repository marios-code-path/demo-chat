package com.demo.chat.service.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import reactor.core.publisher.Mono

/**
 * The check of a key against the registry. See `CHAT-avduuqwp`.
 *
 * - [resolve] reads the stored root of an id.
 * - [verify] reads the stored root, and it refuses a key whose root differs.
 * - Both refuse a key outside the expected domain, when a domain is given.
 *
 * Equality never verifies a root. Only this class does.
 */
class KeyVerifier<T>(private val keys: IKeyService<T>, private val rootKeys: RootKeys<T>) {

    fun resolve(id: T, expected: ChatDomain?): Mono<VerifiedKey<T>> = keys.rootOf(id)
        .switchIfEmpty(Mono.error(KeyVerificationException("Key $id is not in the registry.")))
        .flatMap { root -> check(Key.of(id, root), expected) }

    fun verify(key: Key<T>, expected: ChatDomain?): Mono<VerifiedKey<T>> = resolve(key.id, expected)
        .flatMap { stored ->
            if (stored.key.root == key.root) Mono.just(VerifiedKey(key))
            else Mono.error(KeyVerificationException("Key ${key.id} carries root ${key.root}. The stored root is ${stored.key.root}."))
        }

    /**
     * **This method trusts its caller.** It does not read the registry. So it
     * accepts an unknown id that carries the root of [domain]. Call it only for
     * a key that a typed store of [domain] returned. The one permitted caller
     * is `SpringSecurityAccessBrokerService.hasAccessToEntity`.
     * `KeyVerifierConstructionTests` enforces that limit.
     */
    fun trustTypedStore(key: Key<T>, domain: ChatDomain): VerifiedKey<T> =
        if (key.root == rootKeys.of(domain).id) VerifiedKey(key)
        else throw KeyVerificationException("Key ${key.id} is not in ${domain.wireName}.")

    private fun check(key: Key<T>, expected: ChatDomain?): Mono<VerifiedKey<T>> =
        if (expected == null || rootKeys.of(expected).id == key.root) Mono.just(VerifiedKey(key))
        else Mono.error(KeyVerificationException("Key ${key.id} is not in ${expected.wireName}."))
}
