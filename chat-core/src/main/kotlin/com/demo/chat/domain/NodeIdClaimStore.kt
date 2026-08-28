package com.demo.chat.domain

import reactor.core.publisher.Mono
import java.time.Duration

/**
 * A store-side lease on one [NodeId].
 *
 * A registry check cannot enforce node id uniqueness. One store can be
 * reached by deployments that do not share a registry. The claim therefore
 * lives in the store that the deployments share.
 *
 * Implementations return a [Mono]. The guard is the only place that blocks.
 */
interface NodeIdClaimStore {

    /** `redis` or `cassandra`. This orders the stores in the guard. */
    val backendName: String

    /**
     * The full phrase that names the space this claim covers.
     *
     * Redis supplies `redis store for key type long`. Cassandra supplies
     * `cassandra keyspace chat_long`. The phrase is complete, so that one
     * message template serves every backend.
     */
    val scope: String

    /**
     * Takes the lease.
     *
     * Returns [ClaimResult.Granted] or [ClaimResult.Denied]. It never
     * returns [ClaimResult.Lost].
     */
    fun claim(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Extends the lease when this owner still holds it. */
    fun renew(nodeId: NodeId, owner: String, ttl: Duration): Mono<ClaimResult>

    /** Drops the lease when this owner holds it. Best effort. */
    fun release(nodeId: NodeId, owner: String): Mono<Void>
}
