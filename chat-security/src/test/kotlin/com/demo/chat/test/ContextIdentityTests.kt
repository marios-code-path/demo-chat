package com.demo.chat.test

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.Anon
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
 * The identity policy, one test per state.
 *
 * `docs/IDENTITY-POLICY.md` states the policy. These tests hold it.
 *
 * **The list of states is closed.** A reader that wants a new state must add
 * a rule here first. The last test refuses an unknown principal, which is
 * what keeps the list closed.
 */
class ContextIdentityTests {

    @Test
    fun `no context answers no identity`() {
        assertThat(identityOf(null)).isNull()
    }

    @Test
    fun `no authentication answers no identity`() {
        assertThat(identityOf(SecurityContextImpl())).isNull()
    }

    /**
     * A rejected credential and an expired credential both arrive in this
     * shape. **Before 2026-09-23 this answered the key of the user.**
     */
    @Test
    fun `an unauthenticated token answers no identity`() {
        val token = UsernamePasswordAuthenticationToken.unauthenticated(chatUserDetails(), "secret")

        assertThat(token.isAuthenticated).isFalse()
        assertThat(identityOf(SecurityContextImpl(token))).isNull()
    }

    /**
     * The token Spring Security supplies for a caller who sent no credential.
     * **Before 2026-09-23 this raised `ClassCastException`.**
     */
    @Test
    fun `the spring anonymous token answers the anon root key`() {
        val token = AnonymousAuthenticationToken(
            "key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )

        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(ANON_KEY)
    }

    @Test
    fun `a chat user details principal answers its own key`() {
        val token = UsernamePasswordAuthenticationToken(chatUserDetails(), "secret", listOf())

        assertThat(identityOf(SecurityContextImpl(token))).isEqualTo(USER_KEY)
    }

    /**
     * A token may carry the domain user directly. **Before 2026-09-23 this
     * raised `ClassCastException`.**
     */
    @Test
    fun `a user principal answers its own key`() {
        assertThat(identityOf(SecurityContextImpl(authenticatedUserToken()))).isEqualTo(USER_KEY)
    }

    /**
     * **This is what keeps the list of states closed.** An unknown principal
     * answers no identity, and it does not raise.
     */
    @Test
    fun `an unknown principal answers no identity and raises nothing`() {
        val token = UsernamePasswordAuthenticationToken("a-plain-string", "secret", listOf())

        assertThatCode { identityOf(SecurityContextImpl(token)) }.doesNotThrowAnyException()
        assertThat(identityOf(SecurityContextImpl(token))).isNull()
    }

    private fun identityOf(context: SecurityContext?): Key<Long>? {
        var call = ContextIdentity(rootKeys()).identity()
        if (context != null) {
            call = call.contextWrite(
                ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context))
            )
        }
        return call.block()
    }

    private fun chatUserDetails() =
        ChatUserDetails(User.create(USER_KEY, "u", "handle", "http://u"), listOf())

    private fun authenticatedUserToken() =
        object : AbstractAuthenticationToken(emptyList()) {
            init {
                isAuthenticated = true
            }

            override fun getCredentials(): Any = "none"
            override fun getPrincipal(): Any = User.create(USER_KEY, "u", "handle", "http://u")
        }

    private fun rootKeys(): RootKeys<Long> =
        RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = ANON_KEY)

    private companion object {
        val ANON_KEY: Key<Long> = TestKeys.key(1L)
        val USER_KEY: Key<Long> = TestKeys.key(2L)
    }
}
