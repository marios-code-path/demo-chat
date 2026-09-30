package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentAuthenticationToken
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.security.access.ContextIdentity
import com.demo.chat.test.key.RootKeysFixture
import com.demo.chat.test.key.TestKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class AgentIdentityResolutionTests {

    @Test
    fun `the agent authentication answers the agent user key`() {
        val user = User.create(USER_KEY, "agent-svc", "agent-svc", "http://agent-svc")
        val token = AgentAuthenticationToken(
            ChatUserDetails(user, emptyList()),
            Jwt("value", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "ES256"), mapOf("sub" to "agent-svc")),
            emptyList(),
        )

        val identity = ContextIdentity(rootKeys()).identityOf(token)

        assertThat(identity).isEqualTo(USER_KEY)
    }

    @Test
    fun `the agent authentication does not answer the anon root key`() {
        val user = User.create(USER_KEY, "agent-svc", "agent-svc", "http://agent-svc")
        val token = AgentAuthenticationToken(
            ChatUserDetails(user, emptyList()),
            Jwt("value", Instant.now(), Instant.now().plusSeconds(60), mapOf("alg" to "ES256"), mapOf("sub" to "agent-svc")),
            emptyList(),
        )

        assertThat(ContextIdentity(rootKeys()).identityOf(token)).isNotEqualTo(ANON_KEY)
    }

    private fun rootKeys() =
        RootKeysFixture.ofLong(emptyMap(), admin = TestKeys.key(9999L), anon = ANON_KEY)

    private companion object {
        val ANON_KEY: Key<Long> = TestKeys.key(1L)
        val USER_KEY: Key<Long> = TestKeys.key(2L)
    }
}
