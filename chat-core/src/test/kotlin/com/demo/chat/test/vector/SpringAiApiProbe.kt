package com.demo.chat.test.vector

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.Filter

/**
 * Compile-only probe for the Spring AI 1.0.3 API surface this feature uses.
 *
 * It never runs. If the BOM is ever repinned, this file is the first thing
 * to fail, and it names the exact accessors that moved.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class SpringAiApiProbe {

    fun documentProbe(doc: Document): Triple<String, Map<String, Any?>, Double?> {
        val id: String = doc.id
        val text: String? = doc.text
        val metadata: Map<String, Any?> = doc.metadata
        val score: Double? = doc.score
        return Triple(id, metadata, score)
    }

    fun searchRequestProbe(query: String, limit: Int, threshold: Double, filter: String): SearchRequest =
        SearchRequest.builder()
            .query(query)
            .topK(limit)
            .similarityThreshold(threshold)
            .filterExpression(filter)
            .build()

    fun acceptAllProbe(query: String): SearchRequest =
        SearchRequest.builder()
            .query(query)
            .similarityThresholdAll()
            .build()

    fun requestGetters(request: SearchRequest): Triple<Int, Double, Filter.Expression?> {
        val topK: Int = request.topK
        val threshold: Double = request.similarityThreshold
        val filter: Filter.Expression? = request.filterExpression
        return Triple(topK, threshold, filter)
    }

    fun acceptAllConstant(): Double = SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL

    fun embeddingRequestProbe(request: EmbeddingRequest): List<String> = request.instructions

    fun embeddingResponseProbe(texts: List<String>): EmbeddingResponse {
        val embeddings = texts.mapIndexed { index, _ -> Embedding(FloatArray(256), index) }
        return EmbeddingResponse(embeddings)
    }

    fun documentBuilderProbe(id: String, text: String, metadata: Map<String, Any?>, score: Double): Document =
        Document.builder()
            .id(id)
            .text(text)
            .metadata(metadata)
            .score(score)
            .build()

    fun storeProbe(vectorStore: VectorStore, request: SearchRequest): List<Document> =
        vectorStore.similaritySearch(request)

    fun simpleStoreProbe(embeddingModel: EmbeddingModel): VectorStore =
        SimpleVectorStore.builder(embeddingModel).build()
}
