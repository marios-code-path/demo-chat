package com.demo.chat.service.vector

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

interface VectorIndexState {
    fun status(): VectorIndexStatus

    fun claim(): VectorIndexClaim

    fun invalidate(reason: String)

    fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
    ): VectorIndexStatus
}
