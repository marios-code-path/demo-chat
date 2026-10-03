package com.demo.chat.config.agent

import com.demo.chat.security.ChatUserDetails

/** Holds the one chat identity that this REST deployment accepts. */
class AgentIdentity {

    @Volatile
    private var value: ChatUserDetails<*>? = null

    /** Store the startup-resolved agent principal. */
    fun resolve(user: ChatUserDetails<*>) {
        check(value == null) { "The agent identity was resolved more than once." }
        value = user
    }

    /** Return the resolved agent principal. */
    fun principal(): ChatUserDetails<*> =
        value ?: throw IllegalStateException("The agent identity is not resolved.")
}
