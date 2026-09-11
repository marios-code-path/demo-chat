package com.demo.chat.service.composite.impl

import com.demo.chat.service.vector.VectorIndexClaim
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorRebuildReport
import java.util.concurrent.atomic.AtomicReference

class InMemoryVectorIndexState : VectorIndexState {
    private data class Snapshot(
        val generation: Long,
        val status: VectorIndexStatus,
    )

    private val snapshot = AtomicReference(
        Snapshot(0L, VectorIndexStatus(VectorIndexPhase.INCOMPLETE))
    )

    override fun status(): VectorIndexStatus = snapshot.get().status

    override fun claim(): VectorIndexClaim {
        while (true) {
            val current = snapshot.get()
            if (current.status.running) {
                return VectorIndexClaim(false, current.generation, current.status)
            }
            val running = current.copy(
                status = current.status.copy(phase = VectorIndexPhase.REBUILDING)
            )
            if (snapshot.compareAndSet(current, running)) {
                return VectorIndexClaim(true, running.generation, running.status)
            }
        }
    }

    override fun invalidate(reason: String) {
        snapshot.updateAndGet { current ->
            val phase = if (current.status.running) {
                VectorIndexPhase.REBUILDING
            } else {
                VectorIndexPhase.INCOMPLETE
            }
            current.copy(
                generation = current.generation + 1,
                status = current.status.copy(
                    phase = phase,
                    lastFailure = reason,
                ),
            )
        }
    }

    override fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
    ): VectorIndexStatus {
        val result = snapshot.updateAndGet { current ->
            val unchanged = current.generation == claim.generation
            val succeeded = failure == null && report.failed == 0L && unchanged
            val status = if (succeeded) {
                current.status.copy(
                    phase = VectorIndexPhase.COMPLETE,
                    lastReport = report,
                    lastSuccessAt = report.finishedAt,
                    lastSuccessCount = report.indexed,
                    lastFailure = null,
                )
            } else {
                current.status.copy(
                    phase = VectorIndexPhase.INCOMPLETE,
                    lastReport = report,
                    lastFailure = failureReason(current, report, failure, unchanged),
                )
            }
            current.copy(status = status)
        }
        return result.status
    }

    private fun failureReason(
        current: Snapshot,
        report: VectorRebuildReport,
        failure: String?,
        unchanged: Boolean,
    ): String = failure
        ?: current.status.lastFailure
        ?: if (!unchanged) {
            "Index changed during rebuild"
        } else {
            "Rebuild reported ${report.failed} failed messages"
        }
}
