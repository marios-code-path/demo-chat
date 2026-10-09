package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.security.access.ContextSubmitterIdentity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder

class ContextSubmitterIdentityTests {
    private val anon: Key<Long> = TestKeys.key(1L)
    private val user: Key<Long> = TestKeys.key(2L)
    private val roots = RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = anon)
    private val submitter = ContextSubmitterIdentity(ContextIdentity(roots), roots)

    private fun currentWith(authentication: Authentication?): Key<Long>? {
        val call = submitter.current()
        return (if (authentication == null) call
        else call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))).block()
    }

    @Test
    fun `an authenticated user is the submitter`() {
        val details = ChatUserDetails(User.create(user, "u", "handle", "http://u"), listOf())
        assertThat(currentWith(UsernamePasswordAuthenticationToken(details, "secret", listOf()))).isEqualTo(user)
    }

    @Test
    fun `review 1 - an anonymous caller has no submitter`() {
        val token = AnonymousAuthenticationToken("key", "anonymousUser", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertThat(currentWith(token)).isNull()
    }

    @Test
    fun `no security context gives no submitter`() {
        assertThat(currentWith(null)).isNull()
    }
}
