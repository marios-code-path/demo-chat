package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.ServiceLeaseStore
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The Cassandra store service lease. See `CHAT-lswjobhz`.
 *
 * A lightweight transaction runs at SERIAL, and the coordinator applies the
 * TTL. The lease lives in the session keyspace, so `chat_long` and
 * `chat_uuid` hold separate leases.
 */
class CassandraServiceLeaseStore(
    private val template: ReactiveCassandraTemplate,
    private val keyspace: String,
    private val table: String = "service_lease",
) : ServiceLeaseStore {

    override val backendName: String = "cassandra"

    override val scope: String = "cassandra keyspace $keyspace"

    private val cql get() = template.reactiveCqlOperations

    private val schemaFile: String = "keyspace-" + keyspace.removePrefix("chat_") + ".cql"

    override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows("INSERT INTO $table (name, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?", name, owner, ttl.seconds.toInt())
            .next().map { row ->
                if (row.getBoolean("[applied]")) ClaimResult.Granted as ClaimResult
                else ClaimResult.Denied(holderOf(row) ?: throw IllegalStateException("The $scope denied $name and named no holder."))
            }.onErrorMap { mapMissingTable(it) }

    override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows("UPDATE $table USING TTL ? SET owner_id = ? WHERE name = ? IF owner_id = ?", ttl.seconds.toInt(), owner, name, owner)
            .next().map { row ->
                when {
                    row.getBoolean("[applied]") -> ClaimResult.Granted as ClaimResult
                    holderOf(row) == null -> ClaimResult.Lost
                    else -> ClaimResult.Denied(holderOf(row)!!)
                }
            }.onErrorMap { mapMissingTable(it) }

    override fun release(name: String, owner: String): Mono<Void> =
        cql.queryForRows("DELETE FROM $table WHERE name = ? IF owner_id = ?", name, owner).next().then()
            .onErrorMap { mapMissingTable(it) }

    /** A transaction that found no row returns `[applied]` alone. Test for the column. */
    private fun holderOf(row: Row): String? =
        if (row.columnDefinitions.contains("owner_id")) row.getString("owner_id") else null

    private fun mapMissingTable(error: Throwable): Throwable =
        if (generateSequence(error) { it.cause }.any { it is InvalidQueryException && it.message?.contains("does not exist") == true })
            IllegalStateException(
                "The $scope has no $table table. Recreate the store from $schemaFile in shared-resources-cassandra. " +
                    "See docs/SERVICE-LEASE.md.",
                error
            )
        else error
}
