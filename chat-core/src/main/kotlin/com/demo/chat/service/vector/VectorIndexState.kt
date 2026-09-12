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

data class VectorIndexStatus<T>(
    val phase: VectorIndexPhase,
    val lastReport: VectorRebuildReport? = null,
    val lastSuccessAt: Instant? = null,
    val lastSuccessCount: Long? = null,
    val lastFailure: String? = null,
    val activeJob: Key<T>? = null,
    val coveringJob: Key<T>? = null,
) {
    /**
     * A job covers the index. The phase never decides this value.
     *
     * `claim()` moves a complete index to REBUILDING, and a repair must not
     * lower the reported coverage while it runs.
     */
    val complete: Boolean
        get() = coveringJob != null

    val running: Boolean
        get() = phase == VectorIndexPhase.REBUILDING
}

data class VectorIndexClaim<T>(
    val accepted: Boolean,
    val generation: Long,
    val status: VectorIndexStatus<T>,
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
 * scan error, and an unchanged generation.
 *
 * [status] answers a question about the index. [succeeded] answers a question
 * about this run. A caller that writes a durable outcome reads [succeeded],
 * because the two questions can have different answers.
 */
data class VectorFinishResult<T>(
    val succeeded: Boolean,
    val status: VectorIndexStatus<T>,
)

interface VectorIndexState<T> {
    fun status(): VectorIndexStatus<T>

    fun claim(): VectorIndexClaim<T>

    /**
     * Names the job of the run that holds the claim.
     *
     * The write applies only while the state reports running. The claim
     * generation cannot identify a run, because an invalidation raises that
     * value during the same run. One process runs at most one rebuild, so the
     * running flag is a sufficient guard.
     *
     * Every finish clears the value.
     */
    fun markActiveJob(jobKey: Key<T>)

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
     * The result carries a verdict for this run, not for the index. A caller
     * that writes a durable outcome reads [VectorFinishResult.succeeded].
     */
    fun finish(
        claim: VectorIndexClaim<T>,
        report: VectorRebuildReport,
        failure: String?,
        jobKey: Key<T>?,
    ): VectorFinishResult<T>

    /** Startup adopts the job that the coverage policy selected. */
    fun adoptCoveringJob(jobKey: Key<T>?)

    fun coveringJob(): Key<T>?
}
