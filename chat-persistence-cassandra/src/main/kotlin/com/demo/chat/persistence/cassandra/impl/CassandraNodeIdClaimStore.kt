package com.demo.chat.persistence.cassandra.impl

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.domain.NodeIdClaimStore
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * The cassandra node id lease.
 *
 * A lightweight transaction runs at SERIAL, and the coordinator applies the
 * TTL. No application clock enters the decision.
 *
 * The claim lives in the session keyspace, so `chat_long` and `chat_uuid`
 * hold separate leases. That matches the redis key type scoping.
 *
 * The table name is a parameter so that a test can point at an absent table
 * and prove the message.
 */
class CassandraNodeIdClaimStore(
    private val template: ReactiveCassandraTemplate,
    private val keyspace: String,
    private val table: String = "node_claim"
) : NodeIdClaimStore {

    override val backendName: String = "cassandra"

    override val scope: String = "cassandra keyspace $keyspace"

    private val cql get() = template.reactiveCqlOperations

    private val schemaFile: String = "keyspace-" + keyspace.removePrefix("chat_") + ".cql"

    override fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows(
            "INSERT INTO $table (node_id, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?",
            nodeId.value, owner, ttl.seconds.toInt()
        ).next().map { row ->
            if (row.getBoolean("[applied]")) ClaimResult.Granted as ClaimResult
            else ClaimResult.Denied(
                holderOf(row)
                    ?: throw IllegalStateException(
                        "The $scope denied app.nodeid=${nodeId.value} and named no holder."
                    )
            )
        }.onErrorMap { mapMissingTable(it) }

    override fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult> =
        cql.queryForRows(
            "UPDATE $table USING TTL ? SET owner_id = ? WHERE node_id = ? IF owner_id = ?",
            ttl.seconds.toInt(), owner, nodeId.value, owner
        ).next().map { row ->
            when {
                row.getBoolean("[applied]") -> ClaimResult.Granted as ClaimResult
                // No live row. The lease is gone, and no other owner is named.
                holderOf(row) == null -> ClaimResult.Lost
                else -> ClaimResult.Denied(holderOf(row)!!)
            }
        }.onErrorMap { mapMissingTable(it) }

    override fun release(nodeId: NodeId, owner: String): Mono<Void> =
        cql.queryForRows(
            "DELETE FROM $table WHERE node_id = ? IF owner_id = ?",
            nodeId.value, owner
        ).next().then()
            .onErrorMap { mapMissingTable(it) }

    /**
     * Reads the current holder from a lightweight transaction result.
     *
     * A transaction that did not apply because no row exists returns a row
     * that carries `[applied]` and nothing else. Asking that row for
     * `owner_id` throws. So test for the column, do not test for null.
     */
    private fun holderOf(row: Row): String? =
        if (row.columnDefinitions.contains("owner_id")) row.getString("owner_id") else null

    /**
     * Spring Data wraps the driver exception before it reaches the caller,
     * so the driver type alone does not match. Walk the cause chain instead.
     */
    private fun mapMissingTable(error: Throwable): Throwable =
        if (isMissingTable(error)) missingTable(error) else error

    private fun isMissingTable(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any {
            it is InvalidQueryException && it.message?.contains("does not exist") == true
        }

    private fun missingTable(cause: Throwable): Throwable =
        IllegalStateException(
            "The $scope has no $table table. " +
                "Apply $schemaFile from shared-resources-cassandra, or run the CREATE TABLE " +
                "statement in docs/NODEID-CLAIM.md against an existing keyspace.",
            cause
        )
}
