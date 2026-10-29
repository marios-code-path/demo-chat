package com.demo.chat.config.deploy.authserv

import java.time.Instant

/**
 * Store used client assertions. See `CHAT-wdvjqtpb`.
 *
 * Return true for the first use of a `jti` by one client.
 * A deployed server must keep the record after a restart.
 * All server replicas must share the record.
 */
interface AssertionReplayStore {

    /**
     * Return true for the first use of [jti] by [clientId].
     * Return false for a repeat or an expired assertion.
     */
    fun claim(clientId: String, jti: String, expiresAt: Instant): Boolean
}
