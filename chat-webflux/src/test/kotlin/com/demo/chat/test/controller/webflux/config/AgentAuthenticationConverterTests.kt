package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentAuthenticationToken
import com.demo.chat.config.agent.AgentIdentities
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class AgentAuthenticationConverterTests {

    private val agentA = ChatUserDetails(
        User.create(Key.of(7L, 1L), "agent-a", "agent-a", "http://agent-a"), emptyList<String>(),
    )
    private val agentB = ChatUserDetails(
        User.create(Key.of(8L, 1L), "agent-b", "agent-b", "http://agent-b"), emptyList<String>(),
    )
    private val identities = AgentIdentities().apply {
        resolve(mapOf("client-a" to agentA, "client-b" to agentB))
    }

    private fun jwt(claims: Map<String, Any>): Jwt = Jwt(
        "a-token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        mapOf("alg" to "ES256"),
        claims,
    )

    private fun converter() = AgentAuthenticationConverter(identities)

    @Test
    fun `each client id selects its own agent`() {
        val a = converter().convert(jwt(mapOf("client_id" to "client-a", "scope" to "chat.mcp"))).block()!!
        val b = converter().convert(jwt(mapOf("client_id" to "client-b", "scope" to "chat.mcp"))).block()!!

        assertThat((a.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(7L, 1L))
        assertThat((b.principal as ChatUserDetails<*>).user.key).isEqualTo(Key.of(8L, 1L))
    }

    @Test
    fun `a client id claim in a list raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to listOf("client-a"), "scope" to "chat.mcp"))).block()
        }
    }

    @Test
    fun `unresolved identities refuse to answer`() {
        val failure = assertThrows<IllegalStateException> { AgentIdentities().principalFor("client-a") }

        assertThat(failure.message).isEqualTo("The agent identities are not resolved.")
    }

    @Test
    fun `identities resolve once`() {
        val failure = assertThrows<IllegalStateException> { identities.resolve(emptyMap()) }

        assertThat(failure.message).isEqualTo("The agent identities were resolved more than once.")
    }

    @Test
    fun `a matching client id builds the agent authentication`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-a", "scope" to "chat.mcp"))
        ).block()!!

        assertThat(token).isInstanceOf(AgentAuthenticationToken::class.java)
        assertThat(token.isAuthenticated).isTrue()
        assertThat(token.principal).isInstanceOf(ChatUserDetails::class.java)
        assertThat((token.principal as ChatUserDetails<*>).username).isEqualTo("agent-a")
        assertThat(token.authorities.map { it.authority }).contains("SCOPE_chat.mcp")
    }

    @Test
    fun `a wrong client id raises the controlled failure`() {
        val failure = assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to "another-client", "scope" to "chat.mcp"))).block()
        }

        assertThat(failure.message).doesNotContain("another-client")
    }

    @Test
    fun `an absent client id claim raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("scope" to "chat.mcp"))).block()
        }
    }

    @Test
    fun `a client id claim that is not a string raises the controlled failure`() {
        assertThrows<BadCredentialsException> {
            converter().convert(jwt(mapOf("client_id" to 42, "scope" to "chat.mcp"))).block()
        }
    }

    @Test
    fun `a scope claim in a list gives the same authority as a string`() {
        val fromList = converter().convert(
            jwt(mapOf("client_id" to "client-a", "scope" to listOf("chat.mcp", "profile")))
        ).block()!!
        val fromString = converter().convert(
            jwt(mapOf("client_id" to "client-a", "scope" to "chat.mcp profile"))
        ).block()!!

        assertThat(fromList.authorities.map { it.authority })
            .containsExactlyInAnyOrderElementsOf(fromString.authorities.map { it.authority })
        assertThat(fromList.authorities.map { it.authority }).contains("SCOPE_chat.mcp")
    }

    @Test
    fun `a token without the configured scope still converts, and the chain refuses it later`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-a", "scope" to "profile"))
        ).block()!!

        assertThat(token.authorities.map { it.authority }).doesNotContain("SCOPE_chat.mcp")
    }

    @Test
    fun `the validated jwt stays beside the principal`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-a", "scope" to "chat.mcp"))
        ).block()!! as AgentAuthenticationToken

        assertThat(token.jwt.claims["client_id"]).isEqualTo("client-a")
        assertThat(token.credentials).isNull()
    }
}
