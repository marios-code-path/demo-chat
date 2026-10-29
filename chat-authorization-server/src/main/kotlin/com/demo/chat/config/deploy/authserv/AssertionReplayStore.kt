package com.demo.chat.config.deploy.authserv

import java.time.Instant

/**
 * The memory of used client assertions. See `CHAT-wdvjqtpb`.
 *
 * A store answers true on the first use of one `jti` for one client. A
 * restart or a second replica must keep that answer, so a deployed server
 * needs a store outside the process.
 */
interface AssertionReplayStore {

    /**
     * Answer true on the first use of [jti] for [clientId]. Answer false on a
     * repeat and on an expired assertion.
     */
    fun claim(clientId: String, jti: String, expiresAt: Instant): Boolean
}
