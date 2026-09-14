package com.demo.chat.config

import com.demo.chat.domain.EmbeddingIdentity
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
        "simple" to "openai",
        "redis" to "openai",
        "embedded" to "openai",
        "simple" to "local",
        "redis" to "local",
        "embedded" to "local",
    )

    const val EMBEDDED = "embedded"

    /**
     * The Vector API is an incubator module. A JVM without
     * --add-modules jdk.incubator.vector cannot load it. The embedded
     * vector store then throws NoClassDefFoundError at the first recall,
     * far from the cause. This check moves that failure to startup.
     */
    private fun vectorApiPresent(): Boolean =
        try {
            Class.forName("jdk.incubator.vector.FloatVector")
            true
        } catch (absent: ClassNotFoundException) {
            false
        }

    // Derived from legalPairs so the message cannot drift from the set.
    private val legalPairsDescription =
        legalPairs.joinToString(", ") { (vector, embedding) ->
            "vector=$vector with embedding=$embedding"
        }

    fun validate(vector: String?, embedding: String?, identity: String?) {
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

        // Checked after the pair. An illegal pair is the larger error, and the
        // identity rule reads the embedding value that the pair check accepts.
        EmbeddingIdentity.of(embedding!!, identity)

        // Checked last. An illegal pair is a configuration error and must be
        // reported as one, whatever this JVM can load.
        if (vector == EMBEDDED && !vectorApiPresent()) {
            throw IllegalStateException(
                "Recall selector app.service.core.vector=embedded needs the Vector API. " +
                    "The module jdk.incubator.vector is absent from this JVM. " +
                    "Add --add-modules jdk.incubator.vector to the launch command. " +
                    "Without it the vector store fails at the first recall, not at startup."
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
    @Value("\${app.service.core.embedding.identity:}") identity: String,
) {

    private val vectorSelector = vector
    private val embeddingSelector = embedding
    private val identityValue = identity

    @Bean
    open fun vectorSelectorValidation(): SmartInitializingSingleton =
        SmartInitializingSingleton {
            VectorSelectorValidation.validate(vectorSelector, embeddingSelector, identityValue)
        }
}
