package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

enum class JobOutcome {
    RUNNING,
    SUCCEEDED,
    FAILED,
    RELEASED,
}

/**
 * Durable evidence of one vector index rebuild.
 *
 * The record never acts as a lock. A RUNNING job from an earlier incarnation
 * does not block a new claim.
 *
 * `invalidationCount` is a flag for coverage. Its value is advisory, because a
 * concurrent update can undercount without compare-and-set support. Any value
 * above zero removes coverage.
 */
data class IndexJob<T>(
    override val key: Key<T>,
    val nodeId: Int,
    val keyType: String,
    /**
     * The model that wrote this corpus, as the operator named it.
     *
     * The field is nullable. A record written before this field existed
     * carries null, which states that the writer named no model. An empty
     * string would state that the writer named an empty model, and those are
     * different facts.
     */
    val embeddingIdentity: String? = null,
    val incarnationId: String,
    val startedBy: Key<T>,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val outcome: JobOutcome = JobOutcome.RUNNING,
    val attempted: Long = 0L,
    val indexed: Long = 0L,
    val skipped: Long = 0L,
    val failed: Long = 0L,
    val failureSummary: String? = null,
    val invalidationCount: Long = 0L,
    val lastInvalidationAt: Instant? = null,
) : KeyBearer<T> {
    /**
     * Derived, and never written.
     *
     * Jackson serializes a getter, and this class has no matching constructor
     * argument, so a stored job would fail to read back with an unrecognized
     * field. The redis and cassandra shapes both go through Jackson.
     */
    @get:JsonIgnore
    val covers: Boolean
        get() = outcome == JobOutcome.SUCCEEDED && invalidationCount == 0L
}
