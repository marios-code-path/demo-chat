package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.service.security.AccessBroker
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

class SpringSecurityAccessBrokerService<T>(
    val access: AccessBroker<T>,
    val rootKeys: RootKeys<T>,
    private val verifier: KeyVerifier<T>,
) {

    /**
     * The check of an access expression that names a domain as text. The text
     * is parsed into a [ChatDomain] first. An unknown name denies. It never
     * reaches a map lookup as text. See `CHAT-avduuqwp`.
     */
    fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
        ChatDomain.parse(domain)
            ?.let { access.hasAccessByPrincipal(getSecurityContextPrincipal(), verifier.domainRoot(it), perm) }
            ?.onErrorReturn(false)
            ?.switchIfEmpty(Mono.just(false))
            ?: Mono.just(false)

    fun hasAccessTo(who: T, target: T, perm: String): Mono<Boolean> =
        access.hasAccessByKeyId(who, target, perm)
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    /**
     * The per element check of a `@PostFilter`. [EntityTargets] names the
     * target of [entity] and its domain. An entity with no target denies.
     *
     * **This method trusts the store.** A `@PostFilter` reads entities that a
     * typed store returned, so the key converts through `trustTypedStore` in
     * the domain of the entity type. It reads no registry. This method is the
     * one permitted caller of that conversion. Do not call it for input that
     * a caller sent. Use [hasAccessToSubmittedEntity] for that.
     */
    fun hasAccessToEntity(entity: Any?, perm: String): Mono<Boolean> {
        val key = EntityTargets.keyOf(entity, rootKeys) ?: return Mono.just(false)
        val domain = EntityTargets.domainOf(entity) ?: return Mono.just(false)
        return Mono.fromCallable { verifier.trustTypedStore(key, domain) }
            .flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
    }

    /**
     * The check of an entity that a caller sent, such as an index write. The
     * key verifies against the registry in the domain of the entity type. An
     * unknown key or a forged root denies, and the broker is not called.
     */
    fun hasAccessToSubmittedEntity(entity: Any?, perm: String): Mono<Boolean> {
        val key = EntityTargets.keyOf(entity, rootKeys) ?: return Mono.just(false)
        val domain = EntityTargets.domainOf(entity) ?: return Mono.just(false)
        return verifier.verify(key, domain)
            .flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
    }

    /**
     * The target verifies against the registry before the broker sees it. An
     * unknown key or a forged root denies, and the broker is not called. See
     * `CHAT-avduuqwp`, D4.
     */
    fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
        verifier.verify(target, null)
            .flatMap { access.hasAccessByPrincipal(getSecurityContextPrincipal(), it, perm) }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    /**
     * The principal of the current security context.
     *
     * **`ContextIdentity` holds the rule.** This method used to carry its own
     * copy, and it supplied the `Anon` root key for a context with no
     * authentication. That made an unauthenticated caller and an anonymous
     * caller the same identity. See `docs/IDENTITY-POLICY.md`.
     *
     * An empty answer means denied. Every caller above ends with
     * `switchIfEmpty(Mono.just(false))`, so an empty principal refuses the
     * access rather than granting it.
     */
    private fun getSecurityContextPrincipal(): Mono<Key<T>> = contextIdentity.identity()

    private val contextIdentity = ContextIdentity(rootKeys)
}