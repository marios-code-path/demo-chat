package com.demo.chat.service.dummy

import org.springframework.ai.document.Document
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import kotlin.math.sqrt

/**
 * Deterministic embedding for tests. Texts become character-bigram vectors,
 * so documents that share substrings score higher than documents that do
 * not. No network, no model download.
 */
class DummyEmbeddingModel : EmbeddingModel {

    companion object {
        const val DIMENSIONS = 256
    }

    override fun call(request: EmbeddingRequest): EmbeddingResponse {
        val embeddings = request.instructions.mapIndexed { index, text ->
            Embedding(bigramVector(text), index)
        }
        return EmbeddingResponse(embeddings)
    }

    override fun embed(document: Document): FloatArray =
        bigramVector(document.text ?: "")

    override fun dimensions(): Int = DIMENSIONS

    private fun bigramVector(text: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val padded = " ${text.lowercase()} "
        for (i in 0 until padded.length - 1) {
            val slot = (padded[i].code * 128 + padded[i + 1].code) % DIMENSIONS
            vector[slot] += 1f
        }
        val norm = sqrt(vector.fold(0f) { acc, v -> acc + v * v })
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }
}