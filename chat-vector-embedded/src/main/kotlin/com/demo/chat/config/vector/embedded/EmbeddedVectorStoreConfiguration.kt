package com.demo.chat.config.vector.embedded

import com.integrallis.vectors.core.SimilarityFunction
import com.integrallis.vectors.db.IndexType
import com.integrallis.vectors.db.VectorCollection
import com.integrallis.vectors.spring.ai.JavaVectorsVectorStore
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

/**
 * In-process vector store, backed by the Vectors library.
 *
 * The store is a derived cache. The persisted messages are the source of
 * truth. A lost storage directory is a rebuild, not a data loss, so the
 * default path is ephemeral and no deployment mounts a volume. Issue
 * CHAT-oghjsnad tracks the rebuild path, which does not exist yet.
 *
 * The index is FLAT with the cosine metric and no quantization. FLAT is
 * exact and needs no tuning. A measured query over 100000 vectors at topK
 * 50 costs near 8.3 ms, and the recall API rejects a limit above 50. Move
 * to HNSW above 100000 vectors in one collection.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector"], havingValue = "embedded")
class EmbeddedVectorStoreConfiguration {

    /**
     * The collection holds the vectors and owns the storage directory.
     *
     * The dimension comes from the embedding model rather than a property.
     * A property could disagree with the model, and the collection rejects
     * a vector of the wrong width only when the first add runs.
     */
    @Bean(destroyMethod = "close")
    fun embeddedVectorCollection(
        embeddingModel: EmbeddingModel,
        @Value("\${app.service.core.vector.embedded.path:}") configuredPath: String,
    ): VectorCollection =
        VectorCollection.builder()
            .dimension(embeddingModel.dimensions())
            .metric(SimilarityFunction.COSINE)
            .indexType(IndexType.FLAT)
            .storagePath(storageDirectory(configuredPath))
            .build()

    @Bean(destroyMethod = "close")
    fun embeddedVectorStore(
        embeddingModel: EmbeddingModel,
        collection: VectorCollection,
    ): VectorStore =
        JavaVectorsVectorStore
            .builder(embeddingModel, collection)
            .collectionName(COLLECTION_NAME)
            .commitAfterAdd(true)
            .build()

    /**
     * An unset path gives a temporary directory. That matches the rebuild
     * on failure decision. A set path is created when it does not exist.
     */
    private fun storageDirectory(configuredPath: String): Path =
        if (configuredPath.isBlank()) {
            Files.createTempDirectory(TEMP_PREFIX)
        } else {
            Files.createDirectories(Path.of(configuredPath))
        }

    companion object {
        const val COLLECTION_NAME = "messages"
        const val TEMP_PREFIX = "chat-vector-embedded"
    }
}
