package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties
import jakarta.annotation.PostConstruct

/**
 * The agent identity of this deployment. See `CHAT-pgpmsgvr`.
 *
 * **No value here has a default.** A default would make every deployment the
 * same agent in silence. That is the `app.nodeid` lesson.
 *
 * A field is a non-null [String] with no default, so Spring refuses to bind a
 * context that does not carry the value. The bind failure names the property.
 */
@ConfigurationProperties("app.security")
class AgentSecurityProperties {

    lateinit var agent: Agent
    lateinit var jwt: Jwt

    @PostConstruct
    fun validate() {
        if (!::agent.isInitialized) {
            throw ChatException("app.security.agent is required.")
        }
        agent.validate()
        if (!::jwt.isInitialized) {
            throw ChatException("app.security.jwt.jwk-path is required.")
        }
        jwt.validate()
    }

    /** The OAuth client and the chat user that name the agent. */
    class Agent {
        /** The `client_id` claim that an agent token must carry. */
        lateinit var clientId: String
        /** The chat user handle that names the agent identity. */
        lateinit var username: String
        /** The scope that every enforced request must carry. This deployment selects `chat.mcp`. */
        lateinit var requiredScope: String

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
}
