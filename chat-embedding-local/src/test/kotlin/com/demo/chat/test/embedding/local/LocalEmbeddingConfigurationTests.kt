package com.demo.chat.test.embedding.local

import com.demo.chat.config.embedding.local.LocalEmbeddingConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Path

/**
 * The selector rule and the cache path rule.
 *
 * No test here loads an ONNX model. A real model is a large file that this
 * repository does not carry, so the model load is a manual procedure. See
 * docs/EMBEDDING-PROVIDERS.md.
 */
class LocalEmbeddingConfigurationTests {

    @Test
    fun `another selector value supplies no bean`() {
        for (other in listOf("mock", "openai")) {
            runner(mapOf("app.service.core.embedding" to other)).run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
            }
        }
    }

    @Test
    fun `an absent selector supplies no bean`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `the cache directory carries the identity under the configured base`() {
        val base = "/tmp/chat-embedding-local-test"

        assertThat(
            LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("acme-e5-small-v2"))
        ).isEqualTo(Path.of(base, "acme-e5-small-v2"))
    }

    @Test
    fun `two identities give two cache directories`() {
        val base = "/tmp/chat-embedding-local-test"

        val first = LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("one"))
        val second = LocalEmbeddingConfiguration.cacheDirectoryFor(base, EmbeddingIdentity("two"))

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a missing model uri fails the context and names the property`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.local.tokenizer-uri" to "file:/tmp/tokenizer.json",
            )
        )

        assertThat(failure).hasMessageContaining("app.service.core.embedding.local.model-uri")
    }

    @Test
    fun `a missing tokenizer uri fails the context and names the property`() {
        val failure = failureFor(
            mapOf(
                "app.service.core.embedding" to "local",
                "app.service.core.embedding.local.model-uri" to "file:/tmp/model.onnx",
            )
        )

        assertThat(failure).hasMessageContaining("app.service.core.embedding.local.tokenizer-uri")
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity("acme-e5-small-v2") })
            .withUserConfiguration(LocalEmbeddingConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context -> failure = context.startupFailure }
        return failure ?: error("expected a startup failure for $properties")
    }
}
