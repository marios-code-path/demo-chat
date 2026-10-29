package com.demo.chat

import com.demo.chat.config.deploy.authserv.JdbcAssertionReplayStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Instant

/** The stored replay record of a client assertion. See `CHAT-wdvjqtpb`. */
class JdbcAssertionReplayStoreTests {

    private lateinit var url: String
    private lateinit var template: JdbcTemplate
    private lateinit var store: JdbcAssertionReplayStore
    private val later: Instant = Instant.now().plusSeconds(300)

    @BeforeEach
    fun setUp() {
        url = "jdbc:h2:mem:replay-${System.nanoTime()};DB_CLOSE_DELAY=-1"
        template = JdbcTemplate(DriverManagerDataSource(url, "sa", ""))
        template.execute(
            """
            CREATE TABLE oauth2_client_assertion_jti (
                client_id varchar(100) NOT NULL,
                jti varchar(200) NOT NULL,
                expires_at timestamp with time zone NOT NULL,
                PRIMARY KEY (client_id, jti)
            )
            """.trimIndent(),
        )
        store = JdbcAssertionReplayStore(template)
    }

    @Test
    fun `the first claim holds and the repeat does not`() {
        assertThat(store.claim("client-a", "jti-1", later)).isTrue()
        assertThat(store.claim("client-a", "jti-1", later)).isFalse()
    }

    @Test
    fun `two clients may use the same jti`() {
        assertThat(store.claim("client-a", "jti-1", later)).isTrue()
        assertThat(store.claim("client-b", "jti-1", later)).isTrue()
    }

    @Test
    fun `an expired assertion never claims`() {
        assertThat(store.claim("client-a", "jti-1", Instant.now().minusSeconds(1))).isFalse()
    }

    @Test
    fun `a claim survives a restart`() {
        assertThat(store.claim("client-a", "jti-1", later)).isTrue()

        // A new connection stands for a new process on the same database.
        val afterRestart = JdbcAssertionReplayStore(JdbcTemplate(DriverManagerDataSource(url, "sa", "")))

        assertThat(afterRestart.claim("client-a", "jti-1", later)).isFalse()
    }

    @Test
    fun `an expired row makes room for the same jti`() {
        template.update(
            "INSERT INTO oauth2_client_assertion_jti (client_id, jti, expires_at) VALUES (?, ?, ?)",
            "client-a", "jti-1", Timestamp.from(Instant.now().minusSeconds(60)),
        )

        assertThat(store.claim("client-a", "jti-1", later)).isTrue()
        assertThat(store.claim("client-a", "jti-1", later)).isFalse()
    }
}
