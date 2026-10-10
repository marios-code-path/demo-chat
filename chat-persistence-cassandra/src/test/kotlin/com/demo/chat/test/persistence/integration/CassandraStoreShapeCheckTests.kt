package com.demo.chat.test.persistence.integration

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.persistence.cassandra.impl.CassandraStoreShapeCheck
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.concurrent.atomic.AtomicInteger

/**
 * A keyspace without a required element fails the start check, and the
 * message names the element and the recreation. See `CHAT-avduuqwp`, T7.
 *
 * **Only one test builds the full keyspace.** It proves that the real script
 * satisfies the full required map. Each refusal case builds the tables it
 * names, from the same script statements. The check evaluates each required
 * table and column on its own, so a smaller map gives the same verdict for the
 * removed element. Each refusal case first passes the check, so the removed
 * element alone causes the refusal. `CHAT-pggtduxz`.
 *
 * Each test drops its keyspace. The test classes of this module share one
 * Cassandra container since `CHAT-znodyvcc`.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [RepositoryTestConfiguration::class])
@TestPropertySource(properties = ["app.key.type=long"])
@Tag("integration")
class CassandraStoreShapeCheckTests @Autowired constructor(private val session: CqlSession) {

    private companion object {
        val next = AtomicInteger()

        /** Every table that a backend requires. The index tables belong to chat-index-cassandra. */
        val ALL = CassandraStoreShapeCheck.KEY_TABLES + CassandraStoreShapeCheck.PERSISTENCE_TABLES + mapOf(
            "auth_metadata_principal" to setOf("id", "principal_root", "target_root"),
            "auth_metadata_target" to setOf("id", "principal_root", "target_root"),
            "auth_metadata_by_id" to setOf("target", "principal", "target_root", "principal_root"),
        )

        /** The column types of a long store. */
        val LONG_TYPES = CassandraStoreShapeCheck.persistenceTypes(TypeUtil.LongUtil)

        /** The statements of keyspace-long.cql, with no comment line. */
        val SCRIPT: List<String> = ClassPathResource("keyspace-long.cql").inputStream.bufferedReader().readText()
            .lines().filterNot { it.trim().startsWith("--") }.joinToString("\n")
            .split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val created = mutableListOf<String>()

    @AfterEach
    fun dropKeyspaces() {
        created.forEach { session.execute("DROP KEYSPACE IF EXISTS $it") }
        created.clear()
    }

    private fun newKeyspaceName(): String = "shape_${next.incrementAndGet()}".also { created += it }

    /** A new keyspace from keyspace-long.cql, with every table this release reads. */
    private fun completeKeyspace(): String {
        val name = newKeyspaceName()
        SCRIPT.map { it.replace("chat_long", name) }.forEach { session.execute(it) }
        return name
    }

    /** The statement of keyspace-long.cql that creates [table]. A name that matches nothing fails the test. */
    private fun tableStatement(table: String): String {
        val pattern = Regex("^CREATE TABLE\\s+chat_long\\.${Regex.escape(table)}\\s*\\(")
        return checkNotNull(SCRIPT.singleOrNull { pattern.containsMatchIn(it) }) {
            "keyspace-long.cql creates no table named $table."
        }
    }

    /** A new keyspace that holds [tables] alone, each from its keyspace-long.cql statement. */
    private fun keyspaceWith(vararg tables: String): String {
        val name = newKeyspaceName()
        val keyspace = checkNotNull(SCRIPT.singleOrNull { it.startsWith("CREATE KEYSPACE") }) {
            "keyspace-long.cql holds no CREATE KEYSPACE statement."
        }
        session.execute(keyspace.replace("chat_long", name))
        tables.forEach { session.execute(tableStatement(it).replace("chat_long", name)) }
        return name
    }

    @Test
    fun `a complete keyspace passes`() {
        assertThatCode { CassandraStoreShapeCheck(session, completeKeyspace(), ALL, LONG_TYPES).check() }
            .doesNotThrowAnyException()
    }

    // The long schema before CHAT-xcmpudyb. Every name exists, and msg_id has
    // the wrong type. Such a store started, and failed at the first message write.
    @Test
    fun `a long store with a TIMESTAMP message id fails at start`() {
        val keyspace = keyspaceWith("chat_message_id")
        assertThatCode { CassandraStoreShapeCheck(session, keyspace, emptyMap(), LONG_TYPES).check() }
            .doesNotThrowAnyException()

        session.execute("DROP TABLE $keyspace.chat_message_id")
        session.execute(
            "CREATE TABLE $keyspace.chat_message_id (msg_id TIMESTAMP, user_id BIGINT, topic_id BIGINT, " +
                "text varchar, msg_time TIMESTAMP, visible Boolean, PRIMARY KEY (msg_id, msg_time))"
        )

        assertThatThrownBy { CassandraStoreShapeCheck(session, keyspace, emptyMap(), LONG_TYPES).check() }
            .hasMessageContaining("chat_message_id.msg_id is timestamp, required bigint")
            .hasMessageContaining("Recreate the store")
    }

    @Test
    fun `the message id type follows the key type`() {
        assertThat(CassandraStoreShapeCheck.idType(TypeUtil.LongUtil)).isEqualTo("bigint")
        assertThat(CassandraStoreShapeCheck.idType(UUIDUtil())).isEqualTo("timeuuid")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "keys.root", "root_keys", "auth_metadata.principal_root", "auth_metadata.target_root",
            "auth_metadata_principal.principal_root", "auth_metadata_principal.target_root",
            "auth_metadata_target.principal_root", "auth_metadata_target.target_root",
            "auth_metadata_by_id",
        ]
    )
    fun `a store without a required element fails at start`(element: String) {
        val table = element.substringBefore(".")
        val required = ALL.filterKeys { it == table }
        assertThat(required).describedAs("the required map names $table").isNotEmpty
        val keyspace = keyspaceWith(table)
        assertThatCode { CassandraStoreShapeCheck(session, keyspace, required).check() }
            .describedAs("the keyspace passes before $element is removed")
            .doesNotThrowAnyException()

        if ("." in element) {
            session.execute("ALTER TABLE $keyspace.$table DROP ${element.substringAfter(".")}")
        } else {
            session.execute("DROP TABLE $keyspace.$table")
        }

        assertThatThrownBy { CassandraStoreShapeCheck(session, keyspace, required).check() }
            .hasMessageContaining(element)
            .hasMessageContaining("Recreate the store")
    }
}
