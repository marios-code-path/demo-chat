package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentAuthenticationConverter
import com.demo.chat.config.agent.AgentAuthenticationToken
import com.demo.chat.config.agent.AgentIdentity
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

    private val identity = AgentIdentity().apply {
        resolve(
            ChatUserDetails(
                User.create(Key.of(7L, 1L), "agent-svc", "agent-svc", "http://agent-svc"),
                emptyList(),
            )
        )
    }

    private fun jwt(claims: Map<String, Any>): Jwt = Jwt(
        "a-token-value",
        Instant.now(),
        Instant.now().plusSeconds(60),
        mapOf("alg" to "ES256"),
        claims,
    )

    private fun converter() = AgentAuthenticationConverter(identity, "client-under-test")

    @Test
    fun `a matching client id builds the agent authentication`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp"))
        ).block()!!

        assertThat(token).isInstanceOf(AgentAuthenticationToken::class.java)
        assertThat(token.isAuthenticated).isTrue()
        assertThat(token.principal).isInstanceOf(ChatUserDetails::class.java)
        assertThat((token.principal as ChatUserDetails<*>).username).isEqualTo("agent-svc")
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
            jwt(mapOf("client_id" to "client-under-test", "scope" to listOf("chat.mcp", "profile")))
        ).block()!!
        val fromString = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp profile"))
        ).block()!!

        assertThat(fromList.authorities.map { it.authority })
            .containsExactlyInAnyOrderElementsOf(fromString.authorities.map { it.authority })
        assertThat(fromList.authorities.map { it.authority }).contains("SCOPE_chat.mcp")
    }

    @Test
    fun `a token without the configured scope still converts, and the chain refuses it later`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "profile"))
        ).block()!!

        assertThat(token.authorities.map { it.authority }).doesNotContain("SCOPE_chat.mcp")
    }

    @Test
    fun `the validated jwt stays beside the principal`() {
        val token = converter().convert(
            jwt(mapOf("client_id" to "client-under-test", "scope" to "chat.mcp"))
        ).block()!! as AgentAuthenticationToken

        assertThat(token.jwt.claims["client_id"]).isEqualTo("client-under-test")
        assertThat(token.credentials).isNull()
    }
}
