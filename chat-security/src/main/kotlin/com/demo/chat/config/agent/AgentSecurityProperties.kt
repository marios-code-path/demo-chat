package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.Locale

/**
 * The agent identities of this deployment. See `CHAT-pgpmsgvr` and
 * `CHAT-frcrctdp`.
 *
 * **No agent value has a default.** A default would make every deployment the
 * same agent in silence. That is the `app.nodeid` lesson.
 *
 * The core may omit this configuration. A REST application calls
 * [requireComplete] before it creates its resource server.
 */
@ConfigurationProperties("app.security")
class AgentSecurityProperties {

    /** The scope that every enforced request must carry. This deployment selects `chat.mcp`. */
    var requiredScope: String? = null

    /** One entry per agent. Each entry binds one OAuth client id to one chat user handle. */
    var agents: List<Agent> = emptyList()

    var jwt: Jwt? = null

    /**
     * The handles that hold `ROLE_SERVICE`. `UserDetailsConfiguration` reads the
     * same property with the same default. An agent must not name one of them.
     */
    var serviceAccounts: List<String> = listOf(DEFAULT_SERVICE_ACCOUNT)

    fun isConfigured(): Boolean = requiredScope != null || agents.isNotEmpty() || jwt != null

    fun requireComplete(): Complete {
        val scope = requiredScope?.takeIf { it.isNotBlank() }
            ?: throw ChatException("app.security.required-scope is required.")
        if (agents.isEmpty()) {
            throw ChatException("app.security.agents requires at least one entry.")
        }
        agents.forEachIndexed { index, agent -> agent.validate(index) }
        requireDistinct()
        requireNoReserved()
        val configuredJwt = jwt ?: throw ChatException("app.security.jwt.jwk-path is required.")
        configuredJwt.validate()
        return Complete(agents.toList(), scope, configuredJwt)
    }

    private fun requireDistinct() {
        agents.groupBy { it.clientId }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ChatException("app.security.agents names client id '$it' twice.")
        }
        agents.groupBy { it.username.lowercase(Locale.ROOT) }
            .filterValues { it.size > 1 }.values.firstOrNull()?.let {
                throw ChatException("app.security.agents names username '${it.last().username}' twice.")
            }
    }

    /**
     * An agent must be a plain user. See `CHAT-frcrctdp`.
     *
     * **The comparison ignores case.** The Lucene user index lowercases the
     * handle, so a lookup for `admin` answers the user `Admin`.
     */
    private fun requireNoReserved() {
        val reserved = (RESERVED_IDENTITIES + serviceAccounts.filter { it.isNotBlank() })
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        agents.firstOrNull { it.username.lowercase(Locale.ROOT) in reserved }?.let {
            throw ChatException(
                "app.security.agents names reserved username '${it.username}'. An agent must be a plain user."
            )
        }
    }

    data class Complete(val agents: List<Agent>, val requiredScope: String, val jwt: Jwt) {
        /** The authority that the required scope gives. REST and the core both require it. */
        fun requiredAuthority(): String = authorityFor(requiredScope)
    }

    /** One OAuth client and the chat user that names its agent identity. */
    class Agent {
        /** The `client_id` claim that selects this agent. */
        lateinit var clientId: String
        /** The chat user handle that names this agent identity. */
        lateinit var username: String

        fun validate(index: Int) {
            if (!::clientId.isInitialized || clientId.isBlank()) {
                throw ChatException("app.security.agents[$index].client-id is required.")
            }
            if (!::username.isInitialized || username.isBlank()) {
                throw ChatException("app.security.agents[$index].username is required.")
            }
        }
    }

    /** The trusted signing material. */
    class Jwt {
        /** The JWK file that holds the trusted public key. */
        lateinit var jwkPath: String

        fun validate() {
            if (!::jwkPath.isInitialized || jwkPath.isBlank()) {
                throw ChatException("app.security.jwt.jwk-path is required.")
            }
        }
    }

    companion object {
        /** The prefix that Spring Security gives a scope authority. */
        const val SCOPE_PREFIX = "SCOPE_"

        /** The service account that `userinit.yml` declares. */
        const val DEFAULT_SERVICE_ACCOUNT = "Service"

        /** The two `ChatIdentity` names. Neither one is a plain user. */
        val RESERVED_IDENTITIES = listOf("Admin", "Anon")

        fun authorityFor(scope: String): String = "$SCOPE_PREFIX$scope"
    }
}
