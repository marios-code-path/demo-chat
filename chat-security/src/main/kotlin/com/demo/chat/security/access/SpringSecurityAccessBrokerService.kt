package com.demo.chat.security.access

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.AccessBroker
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

class SpringSecurityAccessBrokerService<T>(
    val access: AccessBroker<T>,
    val rootKeys: RootKeys<T>
) {

    fun hasAccessToDomain(domain: String, perm: String): Mono<Boolean> =
        access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            rootKeys.getRootKey(domain), perm
        )
            .doOnError { println("ERROR") }
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun hasAccessTo(who: T, target: T, perm: String): Mono<Boolean> =
        access.hasAccessByKeyId(who, target, perm)
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun hasAccessTo(target: Key<T>, perm: String): Mono<Boolean> =
        access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            target, perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))

    fun <S> hasAccessToDomainByKind(kind: Class<S>, perm: String): Mono<Boolean> {
        if(!rootKeys.hasKey(kind))
            throw ChatException("Unknown key for domain ${kind.simpleName}")

       return access.hasAccessByPrincipal(
            getSecurityContextPrincipal(),
            rootKeys.getRootKey(kind), perm
        )
            .onErrorReturn(false)
            .switchIfEmpty(Mono.just(false))
    }

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