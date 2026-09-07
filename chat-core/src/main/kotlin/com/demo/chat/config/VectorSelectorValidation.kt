package com.demo.chat.config

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Startup check for the recall selector pair. The capability mechanism does
 * not exist yet; this bean is the interim gate. Migrate to
 * @ProvidesCapability when it lands.
 */
object VectorSelectorValidation {

    private val legalPairs = setOf(
        "mock" to "mock",
        "simple" to "mock",
        "redis" to "mock",
        "embedded" to "mock",
    )

    // Derived from legalPairs so the message cannot drift from the set.
    private val legalPairsDescription =
        legalPairs.joinToString(", ") { (vector, embedding) ->
            "vector=$vector with embedding=$embedding"
        }

    fun validate(vector: String?, embedding: String?) {
        val vectorSet = !vector.isNullOrBlank()
        val embeddingSet = !embedding.isNullOrBlank()
        if (!vectorSet && !embeddingSet) return

        if (vectorSet != embeddingSet) {
            throw IllegalStateException(
                "Recall selector pair incomplete: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Both selectors must be set together."
            )
        }

        if (vector to embedding !in legalPairs) {
            throw IllegalStateException(
                "Illegal recall selector pair: app.service.core.vector=$vector, " +
                    "app.service.core.embedding=$embedding. Legal pairs: " +
                    "$legalPairsDescription."
            )
        }
    }
}

// The module does not enable the Kotlin all-open compiler plugin. A
// configuration class must be open, like BaseDomainConfiguration.
@Configuration
open class VectorSelectorValidationConfiguration(
    @Value("\${app.service.core.vector:}") vector: String,
    @Value("\${app.service.core.embedding:}") embedding: String,
) {

    private val vectorSelector = vector
    private val embeddingSelector = embedding

    @Bean
    open fun vectorSelectorValidation(): SmartInitializingSingleton =
        SmartInitializingSingleton {
            VectorSelectorValidation.validate(vectorSelector, embeddingSelector)
        }
}
