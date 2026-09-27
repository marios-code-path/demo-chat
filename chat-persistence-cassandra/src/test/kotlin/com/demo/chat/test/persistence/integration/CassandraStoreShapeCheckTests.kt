package com.demo.chat.test.persistence.integration

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.persistence.cassandra.impl.CassandraStoreShapeCheck
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
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
            "auth_metadata_principal" to setOf("principal_root", "target_root"),
            "auth_metadata_target" to setOf("principal_root", "target_root"),
        )
    }

    /** A new keyspace from keyspace-long.cql, with every table this release reads. */
    private fun completeKeyspace(): String {
        val name = "shape_${next.incrementAndGet()}"
        val script = ClassPathResource("keyspace-long.cql").inputStream.bufferedReader().readText()
            .lines().filterNot { it.trim().startsWith("--") }.joinToString("\n")
            .replace("chat_long", name)
        script.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { session.execute(it) }
        return name
    }

    /** The keyspace without [element]: a whole table, or one `table.column`. */
    private fun keyspaceWithout(element: String): String {
        val keyspace = completeKeyspace()
        if ("." in element) {
            val (table, column) = element.split(".")
            session.execute("ALTER TABLE $keyspace.$table DROP $column")
        } else {
            session.execute("DROP TABLE $keyspace.$element")
        }
        return keyspace
    }

    @Test
    fun `a complete keyspace passes`() {
        assertThatCode { CassandraStoreShapeCheck(session, completeKeyspace(), ALL).check() }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "keys.root", "root_keys", "auth_metadata.principal_root", "auth_metadata.target_root",
            "auth_metadata_principal.principal_root", "auth_metadata_principal.target_root",
            "auth_metadata_target.principal_root", "auth_metadata_target.target_root",
        ]
    )
    fun `a store without a required element fails at start`(element: String) {
        val keyspace = keyspaceWithout(element)

        assertThatThrownBy { CassandraStoreShapeCheck(session, keyspace, ALL).check() }
            .hasMessageContaining(element)
            .hasMessageContaining("Recreate the store")
    }
}
