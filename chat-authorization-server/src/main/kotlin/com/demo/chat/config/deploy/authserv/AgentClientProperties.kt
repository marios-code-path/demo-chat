package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The agent clients that this authorization server issues tokens to. See
 * `CHAT-frcrctdp`. `chat-build authserv --agent` sets them.
 */
@ConfigurationProperties("app.oauth2")
class AgentClientProperties {

    var agents: List<AgentClient> = emptyList()

    /** The one scope of every agent client. This deployment selects `chat.mcp`. */
    var agentScope: String? = null

    class AgentClient {
        var clientId: String = ""
        var username: String = ""
        var jwkSetUri: String? = null
    }

    fun requireValid(): List<AgentClient> {
        if (agents.isEmpty()) return agents
        if (agentScope.isNullOrBlank()) {
            throw ChatException("app.oauth2.agent-scope is required when app.oauth2.agents is set.")
        }
        agents.forEachIndexed { index, agent ->
            if (agent.clientId.isBlank()) throw ChatException("app.oauth2.agents[$index].client-id is required.")
            if (agent.username.isBlank()) throw ChatException("app.oauth2.agents[$index].username is required.")
            agent.jwkSetUri?.let { uri ->
                if (runCatching { java.net.URI(uri) }.getOrNull()?.let { it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null } != true) {
                    throw ChatException("app.oauth2.agents[$index].jwk-set-uri must be an HTTPS URI.")
                }
            }
        }
        agents.groupBy { it.clientId }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw ChatException("app.oauth2.agents names client id '$it' twice.")
        }
        return agents
    }
}
