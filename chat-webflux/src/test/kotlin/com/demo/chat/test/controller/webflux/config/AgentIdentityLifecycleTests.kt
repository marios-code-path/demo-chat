package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentIdentity
import com.demo.chat.config.agent.AgentIdentityLifecycle
import com.demo.chat.config.agent.AgentSecurityProperties
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.composite.ChatUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import reactor.core.publisher.Flux

class AgentIdentityLifecycleTests {

    private val properties = AgentSecurityProperties().apply {
        agent = AgentSecurityProperties.Agent().apply {
            clientId = "client-under-test"
            username = "agent-svc"
            requiredScope = "chat.mcp"
        }
        jwt = AgentSecurityProperties.Jwt().apply {
            jwkPath = "/tmp/agent-test.jwk"
        }
    }

    /** `User` is an interface with a factory. There is no `UserStatus`. */
    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 1L), handle, handle, "http://$handle")

    @Test
    fun `the configured username resolves the agent principal`() {
        val users = mockUsers()
        Mockito.`when`(users.findByUsername(ByStringRequest("agent-svc")))
            .thenReturn(Flux.just(user(7L, "agent-svc")))
        val identity = AgentIdentity()

        AgentIdentityLifecycle(users, identity, properties).start()

        assertThat(identity.principal().user.key.id).isEqualTo(7L)
        assertThat(identity.principal().username).isEqualTo("agent-svc")
    }

    @Test
    fun `an unknown username fails the start and names the username`() {
        val users = mockUsers()
        Mockito.`when`(users.findByUsername(ByStringRequest("agent-svc")))
            .thenReturn(Flux.empty())
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> {
            AgentIdentityLifecycle(users, identity, properties).start()
        }

        assertThat(failure.message).contains("agent-svc")
    }

    @Test
    fun `a username that answers twice fails the start`() {
        val users = mockUsers()
        Mockito.`when`(users.findByUsername(ByStringRequest("agent-svc")))
            .thenReturn(Flux.just(user(7L, "agent-svc"), user(8L, "agent-svc")))
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> {
            AgentIdentityLifecycle(users, identity, properties).start()
        }

        assertThat(failure.message).contains("agent-svc")
    }

    @Test
    fun `an unresolved identity refuses to answer a principal`() {
        val identity = AgentIdentity()

        val failure = assertThrows<RuntimeException> { identity.principal() }

        assertThat(failure.message).contains("agent")
    }

    @Test
    fun `the phase sits between the root keys and the web server`() {
        assertThat(AgentIdentityLifecycle.PHASE).isGreaterThan(Int.MAX_VALUE - 4096)
        assertThat(AgentIdentityLifecycle.PHASE).isLessThan(Int.MAX_VALUE - 2048)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockUsers(): ChatUserService<Long> =
        Mockito.mock(ChatUserService::class.java) as ChatUserService<Long>
}
