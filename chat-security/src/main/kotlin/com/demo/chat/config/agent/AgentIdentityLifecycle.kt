package com.demo.chat.config.agent

import com.demo.chat.domain.ByStringRequest
import com.demo.chat.security.ChatUserDetails
import com.demo.chat.service.composite.ChatUserService
import org.springframework.context.SmartLifecycle

/**
 * Resolves every configured agent before the web server starts. See
 * `CHAT-frcrctdp`.
 *
 * **The handle match is exact.** The Lucene user index analyzes the handle and
 * lowercases it, so a lookup for `admin` answers the user `Admin`. This class
 * keeps only a user whose handle equals the configured handle.
 */
class AgentIdentityLifecycle<T>(
    private val users: ChatUserService<T>,
    private val identities: AgentIdentities,
    private val properties: AgentSecurityProperties,
) : SmartLifecycle {

    @Volatile
    private var running = false

    override fun start() {
        if (!properties.isConfigured()) {
            running = true
            return
        }
        val resolved = linkedMapOf<String, ChatUserDetails<*>>()
        properties.requireComplete().agents.forEach { agent ->
            val matches = users.findByUsername(ByStringRequest(agent.username))
                .collectList().block().orEmpty()
                .filter { it.handle == agent.username }
            if (matches.size != 1) {
                throw IllegalStateException(
                    "The agent username '${agent.username}' for client '${agent.clientId}' " +
                        "answered ${matches.size} users. It must answer exactly one user."
                )
            }
            resolved[agent.clientId] = ChatUserDetails(matches.single(), listOf("ROLE_AGENT"))
        }
        requireOneClientPerUser(resolved)
        identities.resolve(resolved)
        running = true
    }

    private fun requireOneClientPerUser(resolved: Map<String, ChatUserDetails<*>>) {
        resolved.entries.groupBy { it.value.user.key }.values.firstOrNull { it.size > 1 }?.let {
            throw IllegalStateException(
                "app.security.agents clients '${it[0].key}' and '${it[1].key}' resolve to one user key."
            )
        }
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
