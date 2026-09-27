package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.domain.ChatException
import com.demo.chat.service.core.StoreShapeCheck

/**
 * The Cassandra store shape. Each table and column below must exist in
 * [keyspace]. See `CHAT-avduuqwp`, T7.
 */
class CassandraStoreShapeCheck(private val session: CqlSession, private val keyspace: String) : StoreShapeCheck {

    private val required: Map<String, Set<String>> = mapOf(
        "keys" to setOf("id", "root"),
        "root_keys" to setOf("domain", "id"),
        "auth_metadata" to setOf("principal_root", "target_root"),
        "auth_metadata_principal" to setOf("principal_root", "target_root"),
        "auth_metadata_target" to setOf("principal_root", "target_root"),
    )

    override fun check() {
        val columns = session.execute(
            "SELECT table_name, column_name FROM system_schema.columns WHERE keyspace_name = ?", keyspace
        ).map { row -> row.getString("table_name")!! to row.getString("column_name")!! }
            .groupBy({ it.first }, { it.second })

        val missing = required.flatMap { (table, cols) ->
            val have = columns[table]
            if (have == null) listOf(table) else (cols - have.toSet()).map { "$table.$it" }
        }
        if (missing.isNotEmpty()) throw ChatException(
            "Keyspace $keyspace does not match the required schema. Missing: ${missing.joinToString()}. " +
                "Recreate the store from keyspace-*.cql. This release has no migration."
        )
    }
}
