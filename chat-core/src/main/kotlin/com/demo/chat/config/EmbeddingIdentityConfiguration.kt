package com.demo.chat.config

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The one place that reads the identity property.
 *
 * Five call sites inject the resolved value, and none of them reads the
 * property. See the design document.
 *
 * The bean is absent when both vector selectors are absent. Most deployments
 * set neither selector today, and they must keep starting. So this class
 * carries the same condition the recall beans carry.
 *
 * The module does not enable the Kotlin all-open compiler plugin. A
 * configuration class must be open, like BaseDomainConfiguration.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["vector", "embedding"])
open class EmbeddingIdentityConfiguration(
    @Value("\${app.service.core.embedding}") embedding: String,
    @Value("\${app.service.core.embedding.identity:}") identity: String,
) {

    private val embeddingSelector = embedding
    private val identityValue = identity

    @Bean
    open fun embeddingIdentity(): EmbeddingIdentity =
        EmbeddingIdentity.of(embeddingSelector, identityValue)
}
