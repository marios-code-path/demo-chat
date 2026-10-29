package com.demo.chat

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.util.UUID

class FlywaySchemaMigrationTests {

    @Test
    fun `empty database receives both migrations once`() {
        val database = database()
        val flyway = flyway(database)

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2)
        assertThat(applicationTables(database)).containsExactlyInAnyOrder(*APPLICATION_TABLES)
        assertThat(replayIndexes(database)).contains("oauth2_client_assertion_jti_expires_at_index")
        assertThat(history(database)).containsExactly("1", "2")
        assertThat(flyway.migrate().migrationsExecuted).isZero()
    }

    @Test
    fun `three table database keeps client data and receives replay table`() {
        val database = database()
        existingSchema(database, 1)
        val jdbc = JdbcTemplate(database)
        insertClient(jdbc)
        val flyway = flyway(database)

        assertThatThrownBy { flyway.migrate() }
            .hasMessageContaining("non-empty schema")
        flyway.baseline()
        assertThat(history(database)).containsExactly("1")
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1)
        assertThat(applicationTables(database)).containsExactlyInAnyOrder(*APPLICATION_TABLES)
        assertThat(replayIndexes(database)).contains("oauth2_client_assertion_jti_expires_at_index")
        assertThat(history(database)).containsExactly("1", "2")
        assertClient(jdbc)
        assertThat(flyway.migrate().migrationsExecuted).isZero()
    }

    @Test
    fun `four table database keeps client data without a schema change`() {
        val database = database()
        existingSchema(database, 2)
        val jdbc = JdbcTemplate(database)
        insertClient(jdbc)
        val flyway = flyway(database, "2")

        flyway.baseline()
        assertThat(history(database)).containsExactly("2")
        assertThat(flyway.migrate().migrationsExecuted).isZero()
        assertThat(applicationTables(database)).containsExactlyInAnyOrder(*APPLICATION_TABLES)
        assertThat(replayIndexes(database)).contains("oauth2_client_assertion_jti_expires_at_index")
        assertClient(jdbc)
        assertThat(flyway.migrate().migrationsExecuted).isZero()
    }

    private fun database() = DriverManagerDataSource(
        "jdbc:h2:mem:flyway-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""
    )

    private fun flyway(database: DriverManagerDataSource, version: String = "1") =
        Flyway.configure().dataSource(database).baselineOnMigrate(false)
            .baselineVersion(version).locations("classpath:db/migration").load()

    private fun existingSchema(database: DriverManagerDataSource, version: Int) {
        val resources = mutableListOf(ClassPathResource("db/migration/V1__oauth2_core.sql"))
        if (version == 2) resources.add(ClassPathResource("db/migration/V2__client_assertion_replay.sql"))
        ResourceDatabasePopulator(*resources.toTypedArray()).execute(database)
    }

    private fun applicationTables(database: DriverManagerDataSource): List<String> =
        JdbcTemplate(database).queryForList(
            "SELECT lower(table_name) FROM information_schema.tables " +
                "WHERE table_schema = 'PUBLIC' AND lower(table_name) IN (${APPLICATION_TABLES.joinToString { "'${it}'" }})",
            String::class.java,
        ).filterNotNull()

    private fun history(database: DriverManagerDataSource): List<String> =
        JdbcTemplate(database).queryForList(
            "SELECT \"version\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"", String::class.java
        ).filterNotNull()

    private fun replayIndexes(database: DriverManagerDataSource): List<String> =
        JdbcTemplate(database).queryForList(
            "SELECT lower(index_name) FROM information_schema.indexes " +
                "WHERE table_schema = 'PUBLIC' AND table_name = 'OAUTH2_CLIENT_ASSERTION_JTI'",
            String::class.java,
        ).filterNotNull()

    private fun insertClient(jdbc: JdbcTemplate) {
        jdbc.update(
            "INSERT INTO oauth2_registered_client " +
                "(id, client_id, client_name, client_authentication_methods, authorization_grant_types, " +
                "scopes, client_settings, token_settings) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            "saved-id", "saved-client", "Saved client", "client_secret_basic", "client_credentials",
            "chat.mcp", "{}", "{}",
        )
    }

    private fun assertClient(jdbc: JdbcTemplate) {
        assertThat(jdbc.queryForObject(
            "SELECT client_name FROM oauth2_registered_client WHERE client_id = ?",
            String::class.java, "saved-client"
        )).isEqualTo("Saved client")
    }

    companion object {
        private val APPLICATION_TABLES = arrayOf(
            "oauth2_registered_client", "oauth2_authorization", "oauth2_authorization_consent",
            "oauth2_client_assertion_jti",
        )
    }
}
