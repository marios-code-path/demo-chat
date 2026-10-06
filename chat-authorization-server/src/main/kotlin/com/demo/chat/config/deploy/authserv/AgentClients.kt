package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.oauth2.core.AuthorizationGrantType
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

    /** The agent shape fields in which [row] differs. An empty answer means the shape holds. */
    fun differences(row: RegisteredClient, scope: String): List<String> = buildList {
        if (row.authorizationGrantTypes != setOf(AuthorizationGrantType.CLIENT_CREDENTIALS)) add("grant")
        if (row.clientAuthenticationMethods != setOf(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)) {
            add("authentication method")
        }
        if (row.scopes != setOf(scope)) add("scope")
        if (row.clientSettings.isRequireAuthorizationConsent) add("consent")
        if (row.clientSecret?.startsWith(BCRYPT_PREFIX) != true) add("secret prefix")
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
    data class Issued(val agent: AgentClientProperties.AgentClient, val client: RegisteredClient, val secret: String)

    fun issue(agent: AgentClientProperties.AgentClient, scope: String): Issued {
        val secret = generateSecret()
        return Issued(agent, build(agent.clientId, scope, secret), secret)
    }

    /**
     * Print the secret of a client that the server now holds. Call it only
     * after the repository holds the client, so that a failed save prints no
     * secret.
     */
    fun announce(issued: Issued, out: (String) -> Unit = ::println) =
        out("Generated secret for agent client '${issued.agent.clientId}' (${issued.agent.username}): ${issued.secret}")

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
            val drift = differences(row, scope)
            if (drift.isNotEmpty()) {
                throw ChatException(
                    "The stored client '${agent.clientId}' is not an agent client. It differs in " +
                        "${drift.joinToString()}. Delete the row, and the next start registers it again."
                )
            }
            out("Agent client '${agent.clientId}' (${agent.username}) is registered. Its secret is unchanged.")
        }
    }
}

/** The client id and the row id of one configured client. A blank value is left out. */
fun clientIds(props: Oauth2ClientProperties): List<String> =
    listOf(props.clientId, props.id).filter { it.isNotBlank() }.distinct()

/** The client ids of the `spring.security.oauth2.authorizationserver.client` map. Each row id equals its client id. */
fun bootClientIds(serverProps: ObjectProvider<OAuth2AuthorizationServerProperties>): List<String> =
    serverProps.ifAvailable?.client?.values?.mapNotNull { it.registration.clientId }.orEmpty()
