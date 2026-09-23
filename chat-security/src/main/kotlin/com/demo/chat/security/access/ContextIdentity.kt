package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

/**
 * The one place that turns a security context into a chat identity.
 *
 * **Every access decision reads its principal through this class.** The rule
 * lived in four places before 2026-09-23, and two of them disagreed. See
 * `docs/IDENTITY-POLICY.md` for the policy and the reasoning.
 *
 * The rules, in the order this class applies them:
 *
 * 1. No security context answers no identity.
 * 2. A context with no authentication answers no identity.
 * 3. An authentication that reports `isAuthenticated=false` answers no
 *    identity. A rejected credential and an expired credential both arrive
 *    in this shape.
 * 4. An `AnonymousAuthenticationToken` answers the `Anon` root key.
 * 5. A `ChatUserDetails` principal answers the key of its user.
 * 6. A `User` principal answers its own key.
 * 7. **Every other principal answers no identity.**
 *
 * **No identity means denied, and it is never an error.** The caller turns an
 * empty answer into a refusal. An earlier version cast the principal, so an
 * unexpected type raised `ClassCastException` and the refusal depended on an
 * `onErrorReturn` far away from the decision.
 *
 * Rules 4 to 6 are the only ways to obtain an identity. Rule 7 keeps that
 * list closed, so a new principal type cannot gain access in silence.
 */
class ContextIdentity<T>(private val rootKeys: RootKeys<T>) {

    /** The identity of the current security context, or empty when denied. */
    fun identity(): Mono<Key<T>> =
        ReactiveSecurityContextHolder.getContext()
            .flatMap { context -> Mono.justOrEmpty(identityOf(context.authentication)) }

    /**
     * The identity of one authentication, or null when denied.
     *
     * This is a total function. It throws for no input.
     */
    @Suppress("UNCHECKED_CAST")
    fun identityOf(authentication: Authentication?): Key<T>? {
        if (authentication == null) return null
        if (!authentication.isAuthenticated) return null
        if (authentication is AnonymousAuthenticationToken) return rootKeys.getRootKey(Anon::class.java)

        return when (val principal = authentication.principal) {
            is ChatUserDetails<*> -> (principal as ChatUserDetails<T>).user.key
            is User<*> -> (principal as User<T>).key
            else -> null
        }
    }
}
