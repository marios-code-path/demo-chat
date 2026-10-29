package com.demo.chat.config.deploy.authserv

import com.demo.chat.domain.ChatException
import org.springframework.dao.DataAccessException
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
class JdbcAssertionReplayStore(
    private val template: JdbcTemplate,
    requireTable: Boolean = false,
) : AssertionReplayStore {

    init {
        // An existing database does not run the init scripts again. Fail the
        // start, and name the file, before an agent meets a broken request.
        if (requireTable) verifyTable()
    }

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

    private fun verifyTable() {
        try {
            template.queryForList("SELECT client_id FROM oauth2_client_assertion_jti WHERE 1 = 0")
        } catch (missing: DataAccessException) {
            throw ChatException(
                "The table oauth2_client_assertion_jti is absent. Create it from " +
                    "sql-scripts/oauth2-client-assertion-jti-schema.sql. See CHAT-wdvjqtpb."
            )
        }
    }
}
