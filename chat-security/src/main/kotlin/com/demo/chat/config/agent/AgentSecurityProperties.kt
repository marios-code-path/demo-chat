package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The agent identity of this deployment. See `CHAT-pgpmsgvr`.
 *
 * **No value here has a default.** A default would make every deployment the
 * same agent in silence. That is the `app.nodeid` lesson.
 *
 * The core may omit this configuration. A REST application calls
 * [requireComplete] before it creates its resource server.
 */
@ConfigurationProperties("app.security")
class AgentSecurityProperties {

    var agent: Agent? = null
    var jwt: Jwt? = null

    fun isConfigured(): Boolean = agent != null || jwt != null

    fun requireComplete(): Complete {
        val configuredAgent = agent ?: throw ChatException(
            "app.security.agent.client-id is required."
        )
        configuredAgent.validate()
        val configuredJwt = jwt ?: throw ChatException(
            "app.security.jwt.jwk-path is required."
        )
        configuredJwt.validate()
        return Complete(configuredAgent, configuredJwt)
    }

    data class Complete(val agent: Agent, val jwt: Jwt)

    /** The OAuth client and the chat user that name the agent. */
    class Agent {
        /** The `client_id` claim that an agent token must carry. */
        lateinit var clientId: String
        /** The chat user handle that names the agent identity. */
        lateinit var username: String
        /** The scope that every enforced request must carry. This deployment selects `chat.mcp`. */
        lateinit var requiredScope: String

        /** The authority that the required scope gives. REST and the core both require it. */
        fun requiredAuthority(): String = authorityFor(requiredScope)

        fun validate() {
            if (!::clientId.isInitialized || clientId.isBlank()) {
                throw ChatException("app.security.agent.client-id is required.")
            }
            if (!::username.isInitialized || username.isBlank()) {
                throw ChatException("app.security.agent.username is required.")
            }
            if (!::requiredScope.isInitialized || requiredScope.isBlank()) {
                throw ChatException("app.security.agent.required-scope is required.")
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

        fun authorityFor(scope: String): String = "$SCOPE_PREFIX$scope"
    }
}
