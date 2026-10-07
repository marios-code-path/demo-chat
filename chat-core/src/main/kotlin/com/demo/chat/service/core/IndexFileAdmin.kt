package com.demo.chat.service.core

/** The result of one operator request. [path] names the request file when one exists. */
data class IndexRequestResult(val accepted: Boolean, val reason: String, val path: String?)

/** The state of one file-backed index. Every value is text or a number, so no index library type leaks. */
data class IndexFileReport(
    val name: String,
    val mode: String,
    val path: String?,
    val state: String,
    val liveDocuments: Int?,
    val outcome: String?,
    val reason: String?,
    val entitiesCompared: Long?,
    val documentsWritten: Long?,
    val pendingRequest: String?,
)

/**
 * The operator view of the file-backed indexes. The Lucene module implements
 * it, and the actuator endpoint reads it. See CHAT-ybtirmgj.
 */
interface IndexFileAdmin {
    fun reports(): List<IndexFileReport>
    fun requestRebuild(name: String): IndexRequestResult
    fun requestDrop(name: String): IndexRequestResult
}
