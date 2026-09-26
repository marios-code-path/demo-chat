package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

class SpringSecurityAccessBrokerService<T>(
    val access: AccessBroker<T>,
    val rootKeys: RootKeys<T>
) {

    /**
     * The check of an access expression that names a domain as text. The text
     * is parsed into a [ChatDomain] first. An unknown name denies. It never
     * reaches a map lookup as text. See `CHAT-avduuqwp`.
     */
    fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
        ChatDomain.parse(domain)
            ?.let { access.hasAccessByPrincipal(getSecurityContextPrincipal(), rootKeys.of(it), perm) }
            ?.onErrorReturn(false)
            ?.switchIfEmpty(Mono.just(false))
            ?: Mono.just(false)

    fun hasAccessTo(who: T, target: T, perm: String): Mono<Boolean> =
        access.hasAccessByKeyId(who, target, perm)
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    /**
     * The per element check of a `@PostFilter`. [EntityTargets] names the
     * target of [entity]. An entity with no target denies.
     */
    fun hasAccessToEntity(entity: Any?, perm: String): Mono<Boolean> =
        EntityTargets.keyOf<T>(entity)
            ?.let { target -> hasAccessTo(target, perm) }
            ?: Mono.just(false)

    fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
        access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            target, perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    /**
     * The check of `IKeyServiceAccess.key`, which still names a class until T3d
     * of `CHAT-avduuqwp`. The simple name of [kind] is parsed into a
     * [ChatDomain]. An unknown class denies. It no longer throws.
     */
    fun <S> hasAccessToDomainByKind(kind: Class<S>, perm: String): Mono<Boolean> =
        hasAccessToDomain(kind.simpleName, perm)

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