package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.SpringSecurityAccessBrokerService
import com.demo.chat.service.security.AccessBroker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono

/**
 * What identity each kind of security context reaches at the access broker.
 *
 * `CHAT-ltvfmcvh` asks where the anonymous identity belongs. This class
 * changes nothing. **It states what the code does today**, so a later change
 * can show what it moved.
 *
 * `SpringSecurityAccessBrokerService.getSecurityContextPrincipal` reads
 * `it.authentication?.principal as ChatUserDetails<T>?`. So the answer
 * depends on the runtime class of the principal, not on whether the caller
 * authenticated.
 *
 * Read the measured table in
 * `docs/superpowers/specs/2026-09-23-anonymous-identity-diagnosis.md`.
 */
class AnonymousIdentityDiagnosisTests {

    @Test
    fun `an absent context reaches the anon root key`() {
        assertThat(identityOf(null)).isEqualTo(Reached.Anon)
    }

    @Test
    fun `a context with no authentication reaches the anon root key`() {
        assertThat(identityOf(SecurityContextImpl())).isEqualTo(Reached.Anon)
    }

    /**
     * The token that Spring Security supplies when no caller authenticated.
     * Its principal is the string `anonymousUser`, so the cast fails.
     */
    @Test
    fun `the spring anonymous token reaches no identity and denies`() {
        val token = AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )

        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(Reached.ClassCastDenied)
    }

    /**
     * The shape of `ChatAnonymousAuthenticationToken`, which
     * `DefaultingAnonymousPayloadInterceptor` installs on the RSocket seam.
     * Its principal is a `User`, and `User` is not a `ChatUserDetails`.
     *
     * The token lives in `chat-service-controller`, which `chat-security`
     * does not depend on, so this states its shape rather than importing it.
     */
    @Test
    fun `a user principal reaches no identity and denies`() {
        val token = UserPrincipalToken(User.create(USER_KEY, "anon", "ANONYMOUS", "http://anon"))

        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(Reached.ClassCastDenied)
    }

    @Test
    fun `a chat user details principal reaches its own key`() {
        val details = ChatUserDetails(User.create(USER_KEY, "u", "handle", "http://u"), listOf())
        val token = UsernamePasswordAuthenticationToken(details, "secret", listOf())

        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(Reached.UserKey)
    }

    /**
     * **The case this issue was written about.** A token that carries a
     * principal and reports `isAuthenticated=false` is the closest shape this
     * repository has to a rejected or expired credential.
     *
     * It reaches the same identity as a caller who authenticated. Nothing in
     * the read consults `isAuthenticated`.
     */
    @Test
    fun `an unauthenticated token with a chat user details principal reaches its own key`() {
        val details = ChatUserDetails(User.create(USER_KEY, "u", "handle", "http://u"), listOf())
        val token = UsernamePasswordAuthenticationToken.unauthenticated(details, "secret")

        assertThat(token.isAuthenticated).isFalse()
        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(Reached.UserKey)
    }

    /** What the access broker saw, reduced to one value per case. */
    private enum class Reached { Anon, UserKey, ClassCastDenied, Empty }

    private fun identityOf(context: SecurityContext?): Reached {
        val broker = RecordingAccessBroker()
        val service = SpringSecurityAccessBrokerService(broker, rootKeys())

        var call = service.hasAccessTo(TARGET_KEY, "read")
        if (context != null) {
            call = call.contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))
            )
        }

        val allowed = call.block()

        return when {
            allowed == false -> Reached.ClassCastDenied
            broker.seen.isEmpty() -> Reached.Empty
            broker.seen.single() == ANON_KEY -> Reached.Anon
            broker.seen.single() == USER_KEY -> Reached.UserKey
            else -> error("unexpected key ${broker.seen.single()}")
        }
    }

    private fun rootKeys(): RootKeys<Long> =
        RootKeys<Long>().apply { merge(mapOf("Anon" to ANON_KEY)) }

    private class UserPrincipalToken(
        private val user: User<Long>
    ) : AbstractAuthenticationToken(emptyList()) {
        override fun getCredentials(): Any = "ANONYMOUS"
        override fun getPrincipal(): Any = user
    }

    /** Resolves the principal publisher and records the key it carried. */
    private class RecordingAccessBroker : AccessBroker<Long> {
        val seen: MutableList<Key<Long>> = mutableListOf()

        override fun hasAccessByPrincipal(
            principal: Mono<Key<Long>>, key: Key<Long>, action: String
        ): Mono<Boolean> = principal.map { seen.add(it); true }

        override fun hasAccessManyByPrincipal(
            principal: Mono<Key<Long>>, targets: List<Key<Long>>, perm: String
        ): Mono<Boolean> = principal.map { seen.add(it); true }

        override fun hasAccessByKey(
            principal: Key<Long>, key: Key<Long>, action: String
        ): Mono<Boolean> = error("This diagnosis never calls hasAccessByKey")

        override fun hasAccessByManyKeys(
            principal: Key<Long>, keys: List<Key<Long>>, perm: String
        ): Mono<Boolean> = error("This diagnosis never calls hasAccessByManyKeys")
    }

    private companion object {
        val ANON_KEY: Key<Long> = Key.funKey(1L)
        val USER_KEY: Key<Long> = Key.funKey(2L)
        val TARGET_KEY: Key<Long> = Key.funKey(3L)
    }
}
