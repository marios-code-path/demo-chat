package com.demo.chat.test.vector

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter
import kotlin.math.sqrt

/**
 * In-memory VectorStore for unit tests. Cosine scoring over character
 * bigram vectors, the same scheme as DummyEmbeddingModel. Filter support is
 * the subset the recall service emits: EQ clauses joined by AND, evaluated
 * as a parsed Filter.Expression tree. Not thread-safe; tests use it from
 * one thread.
 *
 * Captures the thread that runs add and similaritySearch so tests can prove
 * the boundedElastic bridge, and captures the last search request so tests
 * can assert on the exact filter and topK.
 */
class MockVectorStore : VectorStore {

    private class Entry(val document: Document, val embedding: FloatArray)

    private val entries = LinkedHashMap<String, Entry>()

    var lastWriteThread: String? = null
        private set

    var lastSearchThread: String? = null
        private set

    var lastFilter: Filter.Expression? = null
        private set

    var lastTopK: Int = 0
        private set

    var lastThreshold: Double = 0.0
        private set

    val ids: List<String>
        get() = entries.keys.toList()

    override fun add(documents: List<Document>) {
        lastWriteThread = Thread.currentThread().name
        for (doc in documents) {
            entries[doc.id] = Entry(doc, bigramVector(doc.text ?: ""))
        }
    }

    override fun delete(idsToDrop: List<String>) {
        lastWriteThread = Thread.currentThread().name
        idsToDrop.forEach { entries.remove(it) }
    }

    override fun delete(expression: Filter.Expression) {
        throw UnsupportedOperationException("MockVectorStore does not support filter deletes")
    }

    override fun similaritySearch(request: SearchRequest): List<Document> {
        lastSearchThread = Thread.currentThread().name
        val filter = request.filterExpression
        lastFilter = filter
        lastTopK = request.topK
        lastThreshold = request.similarityThreshold

        val queryEmbedding = bigramVector(request.query ?: "")
        val threshold =
            if (request.similarityThreshold == SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL) 0.0
            else request.similarityThreshold

        return entries.values
            .map { it.document to cosine(queryEmbedding, it.embedding) }
            .filter { (doc, score) -> score >= threshold }
            .filter { (doc, _) -> filter == null || evaluate(filter, doc.metadata) }
            .sortedByDescending { it.second }
            .take(request.topK)
            .map { (doc, score) ->
                doc.mutate()
                    .score(score)
                    .build()
            }
    }

    // The recall service emits only EQ clauses joined by AND. The mock
    // supports exactly that subset and rejects everything else.
    private fun evaluate(expression: Filter.Expression, metadata: Map<String, Any?>): Boolean =
        when (expression.type()) {
            Filter.ExpressionType.AND -> {
                evaluate(expression.left() as Filter.Expression, metadata) &&
                    evaluate(expression.right() as Filter.Expression, metadata)
            }
            Filter.ExpressionType.EQ -> {
                val key = (expression.left() as Filter.Key).key()
                val value = (expression.right() as Filter.Value).value()
                metadata[key]?.toString() == value?.toString()
            }
            else -> throw UnsupportedOperationException("MockVectorStore does not support ${expression.type()}")
        }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }

    private fun bigramVector(text: String): FloatArray {
        val vector = FloatArray(256)
        val padded = " ${text.lowercase()} "
        for (i in 0 until padded.length - 1) {
            val slot = (padded[i].code * 128 + padded[i + 1].code) % 256
            vector[slot] += 1f
        }
        return vector
    }
}