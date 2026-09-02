package com.demo.chat.config.service.composite

import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.impl.VectorStoreMessageVectorIndexer
import com.demo.chat.service.vector.MessageDocumentMapper
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Recall wiring for the composite services. The class-level gate is the
 * composite selector; each bean needs both recall selectors set. The
 * VectorStore bean comes from the active vector provider module.
 *
 * Interim capability wiring: @ConditionalOnProperty stands in for
 * @ProvidesCapability until the capability mechanism lands.
 */
@Configuration
@ConditionalOnProperty("app.service.composite")
class VectorRecallServiceConfiguration<T, V, Q>(
    private val typeUtil: TypeUtil<T>,
) {

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
    fun messageVectorIndexer(
        vectorStore: VectorStore,
        @Value("\${app.key.type}") keyType: String,
    ): MessageVectorIndexer<T> =
        VectorStoreMessageVectorIndexer(vectorStore, MessageDocumentMapper(typeUtil, keyType))
}
