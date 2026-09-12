package com.demo.chat.service.vector

import com.demo.chat.domain.Key
import java.time.Instant

enum class VectorIndexPhase {
    INCOMPLETE,
    REBUILDING,
    COMPLETE,
}

data class VectorRebuildReport(
    val startedAt: Instant,
    val finishedAt: Instant,
    val attempted: Long,
    val indexed: Long,
    val skipped: Long,
    val failed: Long,
)

data class VectorIndexStatus(
    val phase: VectorIndexPhase,
    val lastReport: VectorRebuildReport? = null,
    val lastSuccessAt: Instant? = null,
    val lastSuccessCount: Long? = null,
    val lastFailure: String? = null,
) {
    val complete: Boolean
        get() = phase == VectorIndexPhase.COMPLETE

    val running: Boolean
        get() = phase == VectorIndexPhase.REBUILDING
}

data class VectorIndexClaim(
    val accepted: Boolean,
    val generation: Long,
    val status: VectorIndexStatus,
)

/**
 * One invalidation. [target] is the covering job that the state held, or null
 * when no job covered the index.
 */
data class VectorInvalidation<T>(
    val generation: Long,
    val target: Key<T>?,
)

/**
 * The outcome of one run.
 *
 * [succeeded] is true only when this run finished with no failed message, no
 * scan error, and an unchanged generation. [status] describes the index, which
 * an earlier job can still cover.
 */
data class VectorFinishResult(
    val succeeded: Boolean,
    val status: VectorIndexStatus,
)

interface VectorIndexState<T> {
    fun status(): VectorIndexStatus

    fun claim(): VectorIndexClaim

    /**
     * Increments the process generation, returns the covering job that the
     * state holds, and clears that target.
     *
     * The caller updates the returned job and no other. A query for the newest
     * successful job could name a job that a concurrent finish has already
     * superseded.
     *
     * The clear is what makes coverage drop. An invalidated job no longer
     * covers, and no earlier job may take its place. A later successful finish
     * installs a new target.
     */
    fun invalidate(reason: String): VectorInvalidation<T>

    /**
     * Installs [jobKey] as the covering job when this run succeeded and the
     * generation did not move. A losing run leaves the current target
     * unchanged, which is null when an invalidation cleared it.
     *
     * [jobKey] is null when the caller holds no durable job. The run can then
     * still succeed in process, and it installs no target.
     *
     * The result carries a verdict for **this run**, not for the index. A
     * caller that writes a durable outcome must read [VectorFinishResult.succeeded],
     * never `status.complete`.
     */
    fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
        jobKey: Key<T>?,
    ): VectorFinishResult

    /** Startup adopts the job that the coverage policy selected. */
    fun adoptCoveringJob(jobKey: Key<T>?)

    fun coveringJob(): Key<T>?
}
