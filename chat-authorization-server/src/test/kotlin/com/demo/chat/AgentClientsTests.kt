package com.demo.chat

import com.demo.chat.config.deploy.authserv.AgentClientProperties
import com.demo.chat.config.deploy.authserv.AgentClients
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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

    private val oldUri = "https://agents.example/old/jwks"
    private val newUri = "https://agents.example/new/jwks"

    private fun keyedAgent(uri: String? = newUri) = AgentClientProperties.AgentClient().apply {
        clientId = "client-a"
        username = "Agent"
        jwkSetUri = uri
    }

    /**
     * One stored private key agent client.
     *
     * [rowId] lets a case hold a row id that differs from the client id.
     * `requireProofKey` and `reuseRefreshTokens` hold values that
     * [AgentClients.differences] never reads. A client rebuilt from the mapping
     * would lose both, so a case can prove the update keeps them.
     */
    private fun storedKeyed(rowId: String = "client-a", uri: String = oldUri): RegisteredClient {
        val base = AgentClients.buildPrivateKeyJwt("client-a", "chat.mcp", uri)
        return RegisteredClient.from(base)
            .id(rowId)
            .clientSettings(ClientSettings.withSettings(base.clientSettings.settings)
                .requireProofKey(true).build())
            .tokenSettings(TokenSettings.withSettings(base.tokenSettings.settings)
                .reuseRefreshTokens(false).build())
            .build()
    }

    /** Reconciles one stored client, and returns the saved client with the output. */
    private fun updateRun(
        stored: RegisteredClient,
        agent: AgentClientProperties.AgentClient = keyedAgent(),
        scope: String = "chat.mcp",
    ): Pair<RegisteredClient, List<String>> {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(stored)
        val out = mutableListOf<String>()

        AgentClients.reconcile(repository, listOf(agent), scope) { out += it }

        val saved = ArgumentCaptor.forClass(RegisteredClient::class.java)
        Mockito.verify(repository).save(saved.capture())
        return saved.value to out
    }

    /**
     * Commit `6f321b43` recorded the opposite case, which asserted that a URI
     * change fails the start. The approved design `da829b7e` replaced that
     * behavior, so this case replaces it.
     */
    @Test
    fun `a URI change alone saves one updated client`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(storedKeyed())

        AgentClients.reconcile(repository, listOf(keyedAgent()), "chat.mcp") {}

        Mockito.verify(repository, Mockito.times(1)).save(Mockito.any())
    }

    @Test
    fun `the saved client holds the new URI`() {
        val (saved, _) = updateRun(storedKeyed())

        assertThat(saved.clientSettings.jwkSetUrl).isEqualTo(newUri)
    }

    /**
     * `docs/MCP-CREDENTIAL-ISSUANCE.md` quotes this line, so the case pins the
     * whole text rather than its parts.
     */
    @Test
    fun `the printed line names the old URI and the new URI`() {
        val (_, out) = updateRun(storedKeyed())

        assertThat(out).containsExactly(
            "Private_key_jwt agent client 'client-a' (Agent) changed its JWKS URI " +
                "from '$oldUri' to '$newUri'.")
    }

    /**
     * The required guard. A client rebuilt from the mapping would carry a row
     * id equal to the client id, so it would write a second row and leave the
     * old URI in place. See `CHAT-uizwrxmf` and `CHAT-vidnpywh`.
     */
    @Test
    fun `a stored row id that differs from the client id survives the update`() {
        val (saved, _) = updateRun(storedKeyed(rowId = "row-9"))

        assertThat(saved.id).isEqualTo("row-9")
        assertThat(saved.clientId).isEqualTo("client-a")
    }

    @Test
    fun `the update keeps each client setting that drift never reads`() {
        val (saved, _) = updateRun(storedKeyed())

        assertThat(saved.clientSettings.isRequireProofKey).isTrue()
        assertThat(saved.clientSettings.isRequireAuthorizationConsent).isFalse()
        assertThat(saved.clientSettings.tokenEndpointAuthenticationSigningAlgorithm)
            .isEqualTo(AgentClients.ASSERTION_ALGORITHM)
    }

    @Test
    fun `the update keeps the stored token settings and the stored scope`() {
        val (saved, _) = updateRun(storedKeyed())

        assertThat(saved.tokenSettings.isReuseRefreshTokens).isFalse()
        assertThat(saved.tokenSettings.accessTokenFormat).isEqualTo(OAuth2TokenFormat.SELF_CONTAINED)
        assertThat(saved.tokenSettings.accessTokenTimeToLive).isEqualTo(AgentClients.TOKEN_TIME_TO_LIVE)
        assertThat(saved.scopes).containsExactly("chat.mcp")
    }

    @Test
    fun `a URI change with a scope change still fails the start`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(storedKeyed())

        assertThatThrownBy {
            AgentClients.reconcile(repository, listOf(keyedAgent()), "chat.other") {}
        }.hasMessageContaining("It differs in scope, JWK Set URI.")
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `a stored secret client against a JWKS mapping still fails the start`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a"))
            .thenReturn(AgentClients.build("client-a", "chat.mcp", "kept"))

        assertThatThrownBy {
            AgentClients.reconcile(repository, listOf(keyedAgent()), "chat.mcp") {}
        }.hasMessageContaining("authentication method")
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `a stored private key client against a secret mapping still fails the start`() {
        val repository = Mockito.mock(RegisteredClientRepository::class.java)
        Mockito.`when`(repository.findByClientId("client-a")).thenReturn(storedKeyed())

        assertThatThrownBy {
            AgentClients.reconcile(repository, listOf(keyedAgent(uri = null)), "chat.mcp") {}
        }.hasMessageContaining("authentication method")
        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    /**
     * The reconciliation limit that a launch-time mapping still meets. See
     * `CHAT-vidnpywh`. A removed agent keeps its stored client, and no app
     * function withdraws it. `CHAT-vepxpmlk` holds that gap. This case records
     * the behavior of today. It makes no claim that the behavior is right.
     */
    @Test
    fun `a changed JWKS URI is the only named difference`() {
        val stored = AgentClients.buildPrivateKeyJwt(
            "client-a", "chat.mcp", "https://agents.example/old/jwks")

        assertThat(AgentClients.differences(stored, "chat.mcp", "https://agents.example/new/jwks"))
            .containsExactly("JWK Set URI")
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
