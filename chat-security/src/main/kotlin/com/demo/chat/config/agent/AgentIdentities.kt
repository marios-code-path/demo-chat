package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails

/**
 * Holds the chat identity of each agent client that this deployment accepts.
 * See `CHAT-frcrctdp`.
 *
 * The token `client_id` selects the identity. A client id that this map does
 * not hold is not an agent of this deployment.
 */
class AgentIdentities {

    @Volatile
    private var byClientId: Map<String, ChatUserDetails<*>>? = null

    /** Store the startup-resolved principal of each agent client. */
    fun resolve(byClientId: Map<String, ChatUserDetails<*>>) {
        check(this.byClientId == null) { "The agent identities were resolved more than once." }
        this.byClientId = byClientId.toMap()
    }

    /** Return the principal of one agent client, or null for an unknown client. */
    fun principalFor(clientId: String): ChatUserDetails<*>? =
        (byClientId ?: throw IllegalStateException("The agent identities are not resolved."))[clientId]
}
