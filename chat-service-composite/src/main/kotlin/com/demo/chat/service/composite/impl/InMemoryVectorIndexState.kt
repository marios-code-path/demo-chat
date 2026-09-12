package com.demo.chat.service.composite.impl

import com.demo.chat.domain.Key
import com.demo.chat.service.vector.VectorFinishResult
import com.demo.chat.service.vector.VectorIndexClaim
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexState
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorInvalidation
import com.demo.chat.service.vector.VectorRebuildReport
import java.util.concurrent.atomic.AtomicReference

class InMemoryVectorIndexState<T> : VectorIndexState<T> {
    private data class Snapshot<T>(
        val generation: Long,
        val coveringJob: Key<T>?,
        val status: VectorIndexStatus,
    )

    private val snapshot = AtomicReference(
        Snapshot<T>(0L, null, VectorIndexStatus(VectorIndexPhase.INCOMPLETE))
    )

    override fun status(): VectorIndexStatus = snapshot.get().status

    override fun coveringJob(): Key<T>? = snapshot.get().coveringJob

    override fun adoptCoveringJob(jobKey: Key<T>?) {
        snapshot.updateAndGet { current -> current.copy(coveringJob = jobKey) }
    }

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

    // getAndUpdate returns the snapshot this call replaced, so the target it
    // reports is the job that covered the index before the clear.
    override fun invalidate(reason: String): VectorInvalidation<T> {
        val previous = snapshot.getAndUpdate { current ->
            val phase = if (current.status.running) {
                VectorIndexPhase.REBUILDING
            } else {
                VectorIndexPhase.INCOMPLETE
            }
            current.copy(
                generation = current.generation + 1,
                coveringJob = null,
                status = current.status.copy(
                    phase = phase,
                    lastFailure = reason,
                ),
            )
        }

        return VectorInvalidation(previous.generation + 1, previous.coveringJob)
    }

    // A compare-and-set loop rather than updateAndGet, because the verdict
    // leaves this method beside the status, and both must come from the one
    // transition that won.
    override fun finish(
        claim: VectorIndexClaim,
        report: VectorRebuildReport,
        failure: String?,
        jobKey: Key<T>?,
    ): VectorFinishResult {
        while (true) {
            val current = snapshot.get()
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

            val next = current.copy(
                status = status,
                coveringJob = if (succeeded && jobKey != null) jobKey else current.coveringJob,
            )

            if (snapshot.compareAndSet(current, next)) {
                return VectorFinishResult(succeeded, next.status)
            }
        }
    }

    private fun failureReason(
        current: Snapshot<T>,
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
