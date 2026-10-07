package com.demo.chat.index.lucene.storage

enum class StartKind { REUSED, BUILT }

enum class BuildReason { NO_COMMIT, HEADER, MISMATCH, DAMAGE, REQUESTED_REBUILD, REQUESTED_DROP }

/** The result of one start. [reason] is null for REUSED and never null for BUILT. */
data class StartOutcome(
    val kind: StartKind,
    val reason: BuildReason?,
    val entitiesCompared: Long,
    val documentsWritten: Long,
) {
    init {
        require((kind == StartKind.REUSED) == (reason == null)) {
            "A REUSED outcome has no reason, and a BUILT outcome has one. Received $kind with $reason."
        }
    }

    fun logLine(name: String): String = when (kind) {
        StartKind.REUSED -> "lucene index $name: reused, $entitiesCompared entries compared, $documentsWritten written"
        StartKind.BUILT -> "lucene index $name: built, reason=$reason, $documentsWritten entries written"
    }
}
