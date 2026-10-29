package com.demo.chat.config.deploy.authserv

import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant

/**
 * Remembers used client assertions in the `oauth2_client_assertion_jti`
 * table. A restart keeps the table, so a captured assertion stays refused.
 * See `CHAT-wdvjqtpb`.
 *
 * The primary key on `(client_id, jti)` makes the claim atomic. Two servers
 * may race, and only one insert holds.
 */
class JdbcAssertionReplayStore(private val template: JdbcTemplate) : AssertionReplayStore {

    override fun claim(clientId: String, jti: String, expiresAt: Instant): Boolean {
        val now = Instant.now()
        if (!expiresAt.isAfter(now)) return false
        // Keep the table small. An expired row may take the same jti again.
        template.update("DELETE FROM oauth2_client_assertion_jti WHERE expires_at < ?", Timestamp.from(now))
        return try {
            template.update(
                "INSERT INTO oauth2_client_assertion_jti (client_id, jti, expires_at) VALUES (?, ?, ?)",
                clientId, jti, Timestamp.from(expiresAt),
            )
            true
        } catch (repeat: DuplicateKeyException) {
            false
        }
    }
}
