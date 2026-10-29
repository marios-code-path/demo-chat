package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

/**
 * One `client_credentials` client per agent. See `CHAT-frcrctdp`.
 *
 * **The token settings are explicit.** REST and the core decode a JWT
 * locally, so an opaque token would answer 401 on every call. The factory does
 * not rely on the library defaults.
 */
object AgentClients {

    val TOKEN_TIME_TO_LIVE: Duration = Duration.ofSeconds(300)

    /** The token endpoint accepts RS256 for a client assertion. */
    val ASSERTION_ALGORITHM: SignatureAlgorithm = SignatureAlgorithm.RS256
    private const val SECRET_BYTES = 32
    private const val BCRYPT_PREFIX = "{bcrypt}"
    private val random = SecureRandom()
    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun build(clientId: String, scope: String, rawSecret: String): RegisteredClient =
        RegisteredClient.withId(clientId)
            .clientId(clientId)
            .clientSecret(encoder.encode(rawSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(scope)
            .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    .accessTokenTimeToLive(TOKEN_TIME_TO_LIVE)
                    .build()
            )
            .build()

    fun buildPrivateKeyJwt(clientId: String, scope: String, jwkSetUri: String): RegisteredClient =
        RegisteredClient.withId(clientId)
            .clientId(clientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(scope)
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(false)
                    .jwkSetUrl(jwkSetUri)
                    // The library sets no default signing algorithm.
                    // Set RS256 so the token endpoint can verify the assertion.
                    // See CHAT-wdvjqtpb.
                    .tokenEndpointAuthenticationSigningAlgorithm(ASSERTION_ALGORITHM)
                    .build()
            )
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                    .accessTokenTimeToLive(TOKEN_TIME_TO_LIVE)
                    .build()
            )
            .build()

    /** The agent shape fields in which [row] differs. An empty answer means the shape holds. */
    fun differences(row: RegisteredClient, scope: String, jwkSetUri: String? = null): List<String> = buildList {
        if (row.authorizationGrantTypes != setOf(AuthorizationGrantType.CLIENT_CREDENTIALS)) add("grant")
        val method = if (jwkSetUri == null) ClientAuthenticationMethod.CLIENT_SECRET_BASIC else ClientAuthenticationMethod.PRIVATE_KEY_JWT
        if (row.clientAuthenticationMethods != setOf(method)) {
            add("authentication method")
        }
        if (row.scopes != setOf(scope)) add("scope")
        if (row.clientSettings.isRequireAuthorizationConsent) add("consent")
        if (jwkSetUri == null && row.clientSecret?.startsWith(BCRYPT_PREFIX) != true) add("secret prefix")
        if (jwkSetUri != null && row.clientSecret != null) add("secret")
        if (jwkSetUri != null && row.clientSettings.jwkSetUrl != jwkSetUri) add("JWK Set URI")
        if (jwkSetUri != null && row.clientSettings.tokenEndpointAuthenticationSigningAlgorithm != ASSERTION_ALGORITHM) {
            add("signing algorithm")
        }
        if (row.tokenSettings.accessTokenFormat != OAuth2TokenFormat.SELF_CONTAINED) add("token format")
        if (row.tokenSettings.accessTokenTimeToLive != TOKEN_TIME_TO_LIVE) add("token lifetime")
    }

    /**
     * Refuse an agent client id that another source uses as a client id or a
     * row id. An agent row id equals its client id, so either match puts two
     * clients on one row.
     */
    fun requireNoCollision(agentIds: List<String>, sources: Map<String, Collection<String>>) {
        agentIds.forEach { id ->
            sources.entries.firstOrNull { id in it.value }?.let {
                throw ChatException("Agent client id '$id' is also registered by ${it.key}.")
            }
        }
    }

    /**
     * Refuse a row id that the store holds for another client. A save of
     * such a client updates that row in place, and the other client loses its
     * secret, scopes and settings. See `CHAT-uizwrxmf`.
     */
    fun requireRowFree(repository: RegisteredClientRepository, client: RegisteredClient) {
        val row = repository.findById(client.id) ?: return
        if (row.clientId != client.clientId) {
            throw ChatException(
                "Client '${client.clientId}' needs row id '${client.id}', and the store holds that row " +
                    "for client '${row.clientId}'."
            )
        }
    }

    /** One agent client and its raw secret, before the server holds it. */
    data class Issued(val agent: AgentClientProperties.AgentClient, val client: RegisteredClient, val secret: String?)

    fun issue(agent: AgentClientProperties.AgentClient, scope: String): Issued {
        agent.jwkSetUri?.let { return Issued(agent, buildPrivateKeyJwt(agent.clientId, scope, it), null) }
        val secret = generateSecret()
        return Issued(agent, build(agent.clientId, scope, secret), secret)
    }

    /**
     * Print the secret of a client that the server now holds. Call it only
     * after the repository holds the client, so that a failed save prints no
     * secret.
     */
    fun announce(issued: Issued, out: (String) -> Unit = ::println) {
        if (issued.secret != null) out("Generated secret for agent client '${issued.agent.clientId}' (${issued.agent.username}): ${issued.secret}")
        else out("Registered private_key_jwt agent client '${issued.agent.clientId}' (${issued.agent.username}).")
    }

    /**
     * Save an absent agent client, keep a matching one, and refuse any other
     * row. A refused row fails the start. The operator deletes it, and the
     * next start registers it again.
     */
    fun reconcile(
        repository: RegisteredClientRepository,
        agents: List<AgentClientProperties.AgentClient>,
        scope: String,
        out: (String) -> Unit = ::println,
    ) {
        agents.forEach { agent ->
            val row = repository.findByClientId(agent.clientId)
            if (row == null) {
                val issued = issue(agent, scope)
                requireRowFree(repository, issued.client)
                repository.save(issued.client)
                announce(issued, out)
                return@forEach
            }
            val drift = differences(row, scope, agent.jwkSetUri)
            if (drift.isNotEmpty()) {
                throw ChatException(
                    "The stored client '${agent.clientId}' is not an agent client. It differs in " +
                        "${drift.joinToString()}. Delete the row, and the next start registers it again."
                )
            }
            if (agent.jwkSetUri == null) out("Agent client '${agent.clientId}' (${agent.username}) is registered. Its secret is unchanged.")
            else out("Private_key_jwt agent client '${agent.clientId}' (${agent.username}) is registered.")
        }
    }
}

/** The client id and the row id of one configured client. A blank value is left out. */
fun clientIds(props: Oauth2ClientProperties): List<String> =
    listOf(props.clientId, props.id).filter { it.isNotBlank() }.distinct()

/** The client ids of the `spring.security.oauth2.authorizationserver.client` map. Each row id equals its client id. */
fun bootClientIds(serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>): List<String> =
    serverProps.ifAvailable?.client?.values?.mapNotNull { it.registration.clientId }.orEmpty()
