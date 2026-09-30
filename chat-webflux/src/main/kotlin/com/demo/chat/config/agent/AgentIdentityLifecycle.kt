package com.demo.chat.config.agent

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.SmartLifecycle

/** Resolves the configured agent before the reactive web server starts. */
class AgentIdentityLifecycle<T>(
    private val users: ChatUserService<T>,
    private val identity: AgentIdentity,
    private val properties: AgentSecurityProperties,
) : SmartLifecycle {

    @Volatile
    private var running = false

    override fun start() {
        val username = properties.agent.username
        val matches = users.findByUsername(ByStringRequest(username)).collectList().block().orEmpty()
        if (matches.size != 1) {
            throw IllegalStateException(
                "The agent username '$username' answered ${matches.size} users. " +
                    "It must answer exactly one user."
            )
        }
        identity.resolve(ChatUserDetails(matches.single(), listOf("ROLE_AGENT")))
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun isRunning(): Boolean = running

    override fun getPhase(): Int = PHASE

    companion object {
        /** Runs after root loading and before the reactive web server. */
        const val PHASE = Int.MAX_VALUE - 3072
    }
}
