package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClientProperties
import com.demo.chat.config.deploy.authserv.AgentClients
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionDecoderFactory
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
        val client = AgentClients.buildPrivateKeyJwt("client-a", "chat.mcp", "https://agents.example/client-a/jwks")

        assertThat(client.id).isEqualTo("client-a")
        assertThat(client.clientId).isEqualTo("client-a")
        assertThat(client.authorizationGrantTypes).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS)
        assertThat(client.clientAuthenticationMethods).containsExactly(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
        assertThat(client.scopes).containsExactly("chat.mcp")
        assertThat(client.clientSettings.isRequireAuthorizationConsent).isFalse()
        assertThat(client.clientSecret).isNull()
        assertThat(client.clientSettings.jwkSetUrl).isEqualTo("https://agents.example/client-a/jwks")
        assertThat(client.clientSettings.tokenEndpointAuthenticationSigningAlgorithm)
            .isEqualTo(SignatureAlgorithm.RS256)
        assertThat(client.tokenSettings.accessTokenFormat).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED)
        assertThat(client.tokenSettings.accessTokenTimeToLive).isEqualTo(Duration.ofSeconds(300))
        assertThat(AgentClients.differences(client, "chat.mcp", "https://agents.example/client-a/jwks")).isEmpty()
    }

    @Test
    fun `a secret agent keeps its original client shape`() {
        val client = AgentClients.build("client-a", "chat.mcp", "raw-secret")

        assertThat(client.clientAuthenticationMethods).containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        assertThat(client.clientSecret).startsWith("{bcrypt}")
        assertThat(client.clientSettings.jwkSetUrl).isNull()
        assertThat(client.tokenSettings.accessTokenFormat).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED)
        assertThat(client.tokenSettings.accessTokenTimeToLive).isEqualTo(Duration.ofSeconds(300))
        assertThat(AgentClients.differences(client, "chat.mcp")).isEmpty()
    }

    @Test
    fun `the token endpoint builds a verifier for a private key agent`() {
        val client = AgentClients.buildPrivateKeyJwt("client-a", "chat.mcp", "https://agents.example/client-a/jwks")

        // Without a signing algorithm, the token endpoint returns invalid_client.
        // This test creates the decoder factory. See CHAT-wdvjqtpb.
        JwtClientAssertionDecoderFactory().createDecoder(client)
    }

    @Test
    fun `a private key agent receives no generated secret`() {
        val agent = AgentClientProperties.AgentClient().apply {
            clientId = "client-a"
            username = "Agent"
            jwkSetUri = "https://agents.example/client-a/jwks"
        }
        val issued = AgentClients.issue(agent, "chat.mcp")

        assertThat(issued.secret).isNull()
        assertThat(issued.client.clientAuthenticationMethods)
            .containsExactly(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
        assertThat(issued.client.clientSettings.jwkSetUrl).isEqualTo(agent.jwkSetUri)
    }

    @Test
    fun `a private key agent requires an HTTPS JWKS URI`() {
        val agent = AgentClientProperties.AgentClient().apply {
            clientId = "client-a"
            username = "Agent"
            jwkSetUri = "http://agents.example/jwks"
        }
        val props = AgentClientProperties().apply { agentScope = "chat.mcp"; agents = listOf(agent) }

        assertThatThrownBy { props.requireValid() }
            .hasMessage("app.oauth2.agents[0].jwk-set-uri must be an HTTPS URI.")
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

    /**
     * The reconciliation limits that a launch-time mapping meets. See
     * `CHAT-vidnpywh`. These cases record the behavior of today. They make no
     * claim that the behavior is right.
     */
    @Test
    fun `a changed JWKS URI is drift, so a relaunch alone cannot apply it`() {
        val stored = AgentClients.buildPrivateKeyJwt(
            "client-a", "chat.mcp", "https://agents.example/old/jwks")

        assertThat(AgentClients.differences(stored, "chat.mcp", "https://agents.example/new/jwks"))
            .containsExactly("JWK Set URI")
    }

    @Test
    fun `a changed JWKS URI fails the start and names the repair`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(
            AgentClients.buildPrivateKeyJwt("client-a", "chat.mcp", "https://agents.example/old/jwks"))
        val keyed = AgentClientProperties.AgentClient().apply {
            clientId = "client-a"
            username = "Agent"
            jwkSetUri = "https://agents.example/new/jwks"
        }

        assertThatThrownBy { AgentClients.reconcile(repository, listOf(keyed), "chat.mcp") {} }
            .hasMessage(
                "The stored client 'client-a' is not an agent client. It differs in JWK Set URI. " +
                    "Delete the row, and the next start registers it again.")
    }

    @Test
    fun `an agent that leaves the mapping keeps its stored client`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        val out = mutableListOf<String>()

        AgentClients.reconcile(repository, emptyList(), "chat.mcp") { out += it }

        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
        Mockito.verifyNoMoreInteractions(repository)
        assertThat(out).describedAs("reconcile reads no row it was not given").isEmpty()
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
