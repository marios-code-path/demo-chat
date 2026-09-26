package com.demo.chat.config.service.composite

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.config.CoreRecallBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.composite.impl.MessageRecallServiceImpl
import com.demo.chat.service.vector.MessageRecallService
import com.demo.chat.service.vector.VectorIndexState
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The one provider of the recall service.
 *
 * The class supplies CoreRecallBeans, and every controller takes that
 * interface. No controller takes MessageRecallService by type, because the
 * RSocket controller implements that interface by delegation and a second bean
 * of the type would make each injection point ambiguous.
 *
 * The class carries the recall selector pair as its condition, so a
 * composition with no vector store supplies no recall beans. The VectorStore
 * bean comes from the active vector provider module.
 *
 * CompositeServiceBeansConfiguration reads the same way. Its collaborators
 * arrive through the constructor, and each interface method takes none.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
class VectorRecallBeansConfiguration<T>(
    private val vectorStore: VectorStore,
    private val typeUtil: TypeUtil<T>,
    private val state: VectorIndexState<T>,
    @Value("\${app.key.type}") private val keyType: String,
    private val rootKeys: RootKeys<T>,
) : CoreRecallBeans<T> {

    @Bean
    override fun recallService(): MessageRecallService<T> =
        MessageRecallServiceImpl(vectorStore, typeUtil, keyType, state, rootKeys)
}
