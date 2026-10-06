package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentIdentities
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

    private fun agent(clientId: String, username: String) = AgentSecurityProperties.Agent().apply {
        this.clientId = clientId
        this.username = username
    }

    private fun properties(vararg agents: AgentSecurityProperties.Agent) = AgentSecurityProperties().apply {
        requiredScope = "chat.mcp"
        this.agents = agents.toList()
        jwt = AgentSecurityProperties.Jwt().apply { jwkPath = "/tmp/agent-test.jwk" }
    }

    private val twoAgents = properties(agent("client-a", "agent-a"), agent("client-b", "agent-b"))

    /** `User` is an interface with a factory. There is no `UserStatus`. */
    private fun user(id: Long, handle: String): User<Long> =
        User.create(Key.of(id, 1L), handle, handle, "http://$handle")

    private fun answer(users: ChatUserService<Long>, query: String, vararg found: User<Long>) {
        Mockito.`when`(users.findByUsername(ByStringRequest(query))).thenReturn(Flux.just(*found))
    }

    @Test
    fun `every configured agent resolves to its own principal`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b", user(8L, "agent-b"))
        val identities = AgentIdentities()

        AgentIdentityLifecycle(users, identities, twoAgents).start()

        assertThat(identities.principalFor("client-a")!!.user.key.id).isEqualTo(7L)
        assertThat(identities.principalFor("client-b")!!.user.key.id).isEqualTo(8L)
        assertThat(identities.principalFor("client-c")).isNull()
    }

    @Test
    fun `a missing second handle fails the start and names it`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b")

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), twoAgents).start()
        }

        assertThat(failure.message).isEqualTo(
            "The agent username 'agent-b' for client 'client-b' answered 0 users. It must answer exactly one user."
        )
    }

    @Test
    fun `a lookup that answers another case keeps zero users`() {
        val users = mockUsers()
        answer(users, "agent", user(7L, "Agent"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), properties(agent("client-a", "agent"))).start()
        }

        assertThat(failure.message).contains("'agent'").contains("answered 0 users")
    }

    @Test
    fun `a handle that answers twice fails the start`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"), user(9L, "agent-a"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), properties(agent("client-a", "agent-a"))).start()
        }

        assertThat(failure.message).contains("answered 2 users")
    }

    @Test
    fun `two clients that resolve to one user key fail the start`() {
        val users = mockUsers()
        answer(users, "agent-a", user(7L, "agent-a"))
        answer(users, "agent-b", user(7L, "agent-b"))

        val failure = assertThrows<IllegalStateException> {
            AgentIdentityLifecycle(users, AgentIdentities(), twoAgents).start()
        }

        assertThat(failure.message).isEqualTo(
            "app.security.agents clients 'client-a' and 'client-b' resolve to one user key."
        )
    }

    @Test
    fun `an unconfigured core starts and resolves nothing`() {
        val lifecycle = AgentIdentityLifecycle(mockUsers(), AgentIdentities(), AgentSecurityProperties())

        lifecycle.start()

        assertThat(lifecycle.isRunning).isTrue()
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
