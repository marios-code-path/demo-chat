package com.demo.chat.config

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

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

/**
 * Runs the selector check before the container builds any singleton.
 *
 * A BeanFactoryPostProcessor runs at that moment, and a
 * SmartInitializingSingleton runs after every singleton exists. The later
 * moment is too late for two reasons. An incomplete pair removes the
 * EmbeddingIdentity bean, so a provider that injects the identity fails with
 * NoSuchBeanDefinitionException and hides the real error. An illegal pair
 * loads an 86.2 MiB ONNX model before anything reports the pair.
 *
 * The class reads the Environment and not a bean, because no bean exists at
 * this moment.
 */
open class VectorSelectorValidationPostProcessor(
    private val environment: Environment,
) : BeanFactoryPostProcessor {

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        VectorSelectorValidation.validate(
            environment.getProperty("app.service.core.vector"),
            environment.getProperty("app.service.core.embedding"),
            environment.getProperty(EmbeddingIdentity.PROPERTY),
        )
    }
}

// The module does not enable the Kotlin all-open compiler plugin. A
// configuration class must be open, like BaseDomainConfiguration.
@Configuration
open class VectorSelectorValidationConfiguration {

    companion object {
        /**
         * The method is static, which is what Spring requires of a
         * BeanFactoryPostProcessor bean. A method on the instance would build
         * the configuration class before every bean post processor exists.
         */
        @Bean
        @JvmStatic
        fun vectorSelectorValidation(environment: Environment): BeanFactoryPostProcessor =
            VectorSelectorValidationPostProcessor(environment)
    }
}
