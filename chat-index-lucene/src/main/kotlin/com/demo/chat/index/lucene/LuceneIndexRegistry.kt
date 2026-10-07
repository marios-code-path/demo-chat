package com.demo.chat.index.lucene

import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.service.core.IndexFileAdmin
import com.demo.chat.service.core.IndexFileReport
import com.demo.chat.service.core.IndexRequestResult

object LuceneIndexNames {
    const val USER = "user"
    const val MESSAGE = "message"
    const val TOPIC = "topic"
    const val MEMBERSHIP = "membership"
    const val AUTH = "auth"
    const val KEY_VALUE = "keyvalue"
}

/** The operator view of the six Lucene indexes. A request takes effect at the next start. */
class LuceneIndexRegistry(
    private val storage: LuceneStorage,
    private val indexes: Map<String, LuceneIndex<*, *>>,
) : IndexFileAdmin {

    override fun reports(): List<IndexFileReport> = indexes.map { (name, index) ->
        val outcome = index.startOutcome()
        IndexFileReport(
            name = name,
            mode = storage.mode,
            path = storage.path(name)?.toString(),
            state = index.state().name,
            liveDocuments = index.liveDocuments(),
            outcome = outcome?.kind?.name,
            reason = outcome?.reason?.name,
            entitiesCompared = outcome?.entitiesCompared,
            documentsWritten = outcome?.documentsWritten,
            pendingRequest = storage.requests(name)?.pending()?.name,
        )
    }

    override fun requestRebuild(name: String) = request(name, IndexRequest.REBUILD)

    override fun requestDrop(name: String) = request(name, IndexRequest.DROP)

    private fun request(name: String, kind: IndexRequest): IndexRequestResult {
        if (name !in indexes) {
            return IndexRequestResult(
                false, "No Lucene index is named '$name'. The indexes are: ${indexes.keys.joinToString(", ")}.", null,
            )
        }
        val requests = storage.requests(name) ?: return IndexRequestResult(
            false, "The Lucene indexes are in memory. Every start already builds them, so no request is needed.", null,
        )
        return requests.write(kind)
    }
}
