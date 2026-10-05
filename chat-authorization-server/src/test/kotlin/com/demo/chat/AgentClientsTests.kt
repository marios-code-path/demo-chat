package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClientProperties
import com.demo.chat.config.deploy.authserv.AgentClients
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration

class AgentClientsTests {

    private fun properties(scope: String?, vararg clients: Pair<String, String>) = AgentClientProperties().apply {
        agentScope = scope
        agents = clients.map { (id, handle) -> AgentClientProperties.AgentClient().apply { clientId = id; username = handle } }
    }

    @Test
    fun `an agent client has the agent shape`() {
        val client = AgentClients.build("client-a", "chat.mcp", "raw-secret")

        assertThat(client.id).isEqualTo("client-a")
        assertThat(client.clientId).isEqualTo("client-a")
        assertThat(client.authorizationGrantTypes).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS)
        assertThat(client.clientAuthenticationMethods).containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        assertThat(client.scopes).containsExactly("chat.mcp")
        assertThat(client.clientSettings.isRequireAuthorizationConsent).isFalse()
        assertThat(client.clientSecret).startsWith("{bcrypt}")
        assertThat(client.tokenSettings.accessTokenFormat).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED)
        assertThat(client.tokenSettings.accessTokenTimeToLive).isEqualTo(Duration.ofSeconds(300))
        assertThat(AgentClients.differences(client, "chat.mcp")).isEmpty()
    }

    @Test
    fun `each drifted field is named`() {
        val good = AgentClients.build("client-a", "chat.mcp", "raw-secret")
        fun drift(change: RegisteredClient.Builder.() -> Unit) =
            AgentClients.differences(RegisteredClient.from(good).apply(change).build(), "chat.mcp")

        assertThat(drift { authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE); redirectUri("http://x") })
            .containsExactly("grant")
        assertThat(drift { clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST) })
            .containsExactly("authentication method")
        assertThat(drift { scope("openid") }).containsExactly("scope")
        assertThat(drift { clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build()) })
            .containsExactly("consent")
        assertThat(drift { clientSecret("{noop}raw-secret") }).containsExactly("secret prefix")
        assertThat(drift {
            tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE).build())
        }).containsExactly("token format")
        assertThat(drift {
            tokenSettings(TokenSettings.withSettings(good.tokenSettings.settings)
                .accessTokenTimeToLive(Duration.ofMinutes(30)).build())
        }).containsExactly("token lifetime")
    }

    @Test
    fun `a generated secret is 32 bytes without padding`() {
        val secret = AgentClients.generateSecret()

        assertThat(java.util.Base64.getUrlDecoder().decode(secret)).hasSize(32)
        assertThat(secret).doesNotContain("=")
    }

    @Test
    fun `agents without a scope fail and name the property`() {
        assertThatThrownBy { properties(null, "client-a" to "Agent").requireValid() }
            .hasMessage("app.oauth2.agent-scope is required when app.oauth2.agents is set.")
    }

    @Test
    fun `no agents need no scope`() {
        assertThat(properties(null).requireValid()).isEmpty()
    }

    @Test
    fun `a shared agent client id fails and names it`() {
        assertThatThrownBy { properties("chat.mcp", "client-a" to "Agent", "client-a" to "Claude").requireValid() }
            .hasMessage("app.oauth2.agents names client id 'client-a' twice.")
    }

    @Test
    fun `an agent client id in another source fails and names both`() {
        assertThatThrownBy {
            AgentClients.requireNoCollision(listOf("client-a"), mapOf("app.oauth2.client" to listOf("client-a")))
        }.hasMessage("Agent client id 'client-a' is also registered by app.oauth2.client.")
    }

    private val agent = AgentClientProperties.AgentClient().apply { clientId = "client-a"; username = "Agent" }

    @Test
    fun `a saved agent client prints its secret after the save`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        val out = mutableListOf<String>()
        Mockito.`when`(repository.save(Mockito.any())).then {
            assertThat(out).describedAs("output before the save completes").isEmpty()
            null
        }

        AgentClients.reconcile(repository, listOf(agent), "chat.mcp") { out += it }

        assertThat(out).singleElement().asString()
            .matches("Generated secret for agent client 'client-a' \\(Agent\\): \\S+")
    }

    @Test
    fun `a matching agent row prints no secret`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(AgentClients.build("client-a", "chat.mcp", "kept"))
        val out = mutableListOf<String>()

        AgentClients.reconcile(repository, listOf(agent), "chat.mcp") { out += it }

        assertThat(out).containsExactly("Agent client 'client-a' (Agent) is registered. Its secret is unchanged.")
    }

    @Test
    fun `a failed save prints no secret`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.save(Mockito.any())).thenThrow(IllegalStateException("store refused"))
        val out = mutableListOf<String>()

        assertThatThrownBy { AgentClients.reconcile(repository, listOf(agent), "chat.mcp") { out += it } }
            .hasMessage("store refused")
        assertThat(out).isEmpty()
    }
}
