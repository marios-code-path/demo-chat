package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.domain.ChatException
import com.demo.chat.service.core.ColumnShapeCheck
import com.demo.chat.service.core.StoreShapeCheck

/**
 * The Cassandra store shape of one backend. Each table and column in
 * [required] must exist in [keyspace]. See `CHAT-avduuqwp`, T7.
 *
 * The key services require [KEY_TABLES], and the persistence services require
 * [PERSISTENCE_TABLES]. Each registers its own check, so a Cassandra store is
 * checked when another backend provides the keys.
 */
class CassandraStoreShapeCheck(
    session: CqlSession,
    keyspace: String,
    required: Map<String, Set<String>>,
) : StoreShapeCheck by ColumnShapeCheck("Keyspace $keyspace", required, { columnsOf(session, keyspace) }) {

    companion object {
        val KEY_TABLES: Map<String, Set<String>> = mapOf(
            "keys" to setOf("id", "root"),
            "root_keys" to setOf("domain", "id"),
        )

        val PERSISTENCE_TABLES: Map<String, Set<String>> = mapOf(
            "auth_metadata" to setOf("principal_root", "target_root"),
        )

        fun columnsOf(session: CqlSession, keyspace: String): Map<String, Set<String>> =
            session.execute("SELECT table_name, column_name FROM system_schema.columns WHERE keyspace_name = ?", keyspace)
                .map { row -> row.getString("table_name")!! to row.getString("column_name")!! }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.toSet() }

        /** The keyspace of [session], read when a check runs, at start. */
        fun keyspaceOf(session: CqlSession): String = session.keyspace.map { it.asInternal() }.orElseThrow {
            ChatException("The Cassandra session names no keyspace, so the store shape cannot be checked.")
        }

        /** A check that reads the keyspace when it runs, and not when the bean is built. */
        fun lazy(session: CqlSession, required: Map<String, Set<String>>): StoreShapeCheck =
            StoreShapeCheck { CassandraStoreShapeCheck(session, keyspaceOf(session), required).check() }
    }
}
