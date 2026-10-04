package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.core.ColumnShapeCheck
import com.demo.chat.service.core.StoreShapeCheck
import java.util.UUID

/**
 * The Cassandra store shape of one backend. Each table and column in
 * [required] must exist in [keyspace]. See `CHAT-avduuqwp`, T7. Each column in
 * [types] must also have the named type. See `CHAT-xcmpudyb`.
 *
 * The key services require [KEY_TABLES], and the persistence services require
 * [PERSISTENCE_TABLES]. Each registers its own check, so a Cassandra store is
 * checked when another backend provides the keys.
 */
class CassandraStoreShapeCheck(
    session: CqlSession,
    keyspace: String,
    required: Map<String, Set<String>>,
    types: Map<String, Map<String, String>> = emptyMap(),
) : StoreShapeCheck by ColumnShapeCheck("Keyspace $keyspace", required, types, { columnsOf(session, keyspace) }) {

    companion object {
        val KEY_TABLES: Map<String, Set<String>> = mapOf(
            "keys" to setOf("id", "root"),
            "root_keys" to setOf("domain", "id"),
        )

        val PERSISTENCE_TABLES: Map<String, Set<String>> = mapOf(
            "auth_metadata" to setOf("principal_root", "target_root"),
        )

        /**
         * The CQL type of a message id for [typeUtil]. A `Long` id is `bigint`.
         * A `UUID` id is `timeuuid`. `chat-index-cassandra` holds the same rule
         * in `IndexServiceConfiguration`.
         */
        fun idType(typeUtil: TypeUtil<*>): String = when (typeUtil.empty()) {
            is Long -> "bigint"
            is UUID -> "timeuuid"
            else -> throw ChatException("No Cassandra message id type for the key type of ${typeUtil::class.java.name}.")
        }

        /** The column types that the persistence services require for [typeUtil]. */
        fun persistenceTypes(typeUtil: TypeUtil<*>): Map<String, Map<String, String>> =
            mapOf("chat_message_id" to mapOf("msg_id" to idType(typeUtil)))

        /** The type of each column, for each table of [keyspace]. */
        fun columnsOf(session: CqlSession, keyspace: String): Map<String, Map<String, String>> =
            session.execute("SELECT table_name, column_name, type FROM system_schema.columns WHERE keyspace_name = ?", keyspace)
                .groupBy({ it.getString("table_name")!! }, { it.getString("column_name")!! to it.getString("type")!! })
                .mapValues { it.value.toMap() }

        /** The keyspace of [session], read when a check runs, at start. */
        fun keyspaceOf(session: CqlSession): String = session.keyspace.map { it.asInternal() }.orElseThrow {
            ChatException("The Cassandra session names no keyspace, so the store shape cannot be checked.")
        }

        /** A check that reads the keyspace when it runs, and not when the bean is built. */
        fun lazy(
            session: CqlSession,
            required: Map<String, Set<String>>,
            types: Map<String, Map<String, String>> = emptyMap(),
        ): StoreShapeCheck =
            StoreShapeCheck { CassandraStoreShapeCheck(session, keyspaceOf(session), required, types).check() }
    }
}
