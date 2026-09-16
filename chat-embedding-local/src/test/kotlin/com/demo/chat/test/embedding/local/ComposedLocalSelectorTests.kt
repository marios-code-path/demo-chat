package com.demo.chat.test.embedding.local

import com.demo.chat.config.EmbeddingIdentityConfiguration
import com.demo.chat.config.VectorSelectorValidationConfiguration
import com.demo.chat.config.embedding.local.LocalEmbeddingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The three configurations together, which is the shape a deployment builds.
 *
 * LocalEmbeddingConfigurationTests supplies the identity as a test bean, so it
 * cannot show what happens when the resolver refuses to supply one. This class
 * composes the selector check, the resolver, and the provider.
 *
 * Every model URI here names a file that does not exist. So a message that
 * names that file proves that the container built the model, and a selector
 * error must arrive before that moment.
 */
class ComposedLocalSelectorTests {

    @Test
    fun `an incomplete pair reports both selectors and builds no model`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.identity" to "acme-e5-small-v2",
            )
        )

        assertThat(failure.message)
            .contains("app.service.core.vector")
            .contains("app.service.core.embedding")
            .contains("Both selectors must be set together")

        assertThat(failure.message)
            .describedAs("the identity bean must not be the reported error")
            .doesNotContain("NoSuchBeanDefinitionException")
        assertThat(failure.message)
            .describedAs("the model must not load")
            .doesNotContain(ABSENT_MODEL)
    }

    @Test
    fun `an illegal pair reports the pair and builds no model`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.vector" to "mock",
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.identity" to "acme-e5-small-v2",
            )
        )

        assertThat(failure.message)
            .contains("Illegal recall selector pair")
            .contains("app.service.core.vector=mock")
            .contains("app.service.core.embedding=local")

        assertThat(failure.message)
            .describedAs("the model must not load")
            .doesNotContain(ABSENT_MODEL)
    }

    @Test
    fun `a legal pair without an identity reports the identity and builds no model`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "local",
            )
        )

        assertThat(failure.message).contains("app.service.core.embedding.identity")
        assertThat(failure.message)
            .describedAs("the model must not load")
            .doesNotContain(ABSENT_MODEL)
    }

    private companion object {
        const val ABSENT_MODEL = "/tmp/chat-embedding-local-absent-model.onnx"
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withPropertyValues(
                "app.service.core.embedding.local.model-uri=file:$ABSENT_MODEL",
                "app.service.core.embedding.local.tokenizer-uri=file:/tmp/chat-embedding-local-absent-tokenizer.json",
            )
            .withUserConfiguration(
                VectorSelectorValidationConfiguration::class.java,
                EmbeddingIdentityConfiguration::class.java,
                LocalEmbeddingConfiguration::class.java,
            )

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context -> failure = context.startupFailure }
        return failure ?: error("expected a startup failure for $properties")
    }
}
