package com.demo.chat.test.embedding.openai

import com.demo.chat.config.embedding.openai.OpenAiEmbeddingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * The bean builds and it stays behind its selector.
 *
 * No test here calls the remote service. A build proves the wiring, and the
 * packaged gate proves the request and the response.
 */
class OpenAiEmbeddingConfigurationTests {

    @Test
    fun `the selector openai supplies an embedding model`() {
        runner(
            mapOf(
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
                "app.service.core.embedding.openai.api-key" to "not-a-secret",
                "app.service.core.embedding.openai.model" to "text-embedding-3-small",
            )
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `another selector value supplies no bean`() {
        for (other in listOf("mock", "local")) {
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
    fun `an absent property fails the context and names that property`() {
        val complete = mapOf(
            "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
            "app.service.core.embedding.openai.api-key" to "not-a-secret",
            "app.service.core.embedding.openai.model" to "text-embedding-3-small",
        )

        for (absent in complete.keys) {
            val failure = failureFor(
                complete.filterKeys { it != absent } + ("app.service.core.embedding" to "openai")
            )

            assertThat(failure)
                .describedAs("expected a failure that names %s", absent)
                .hasMessageContaining(absent)
        }
    }

    @Test
    fun `a blank property fails the context and names that property`() {
        // A blank key reaches a remote service as an anonymous call, and an
        // operator cannot tell a missing key from an intended one. The same
        // reasoning holds for a blank base URL and a blank model name. So a
        // blank value is not a value.
        val complete = mapOf(
            "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
            "app.service.core.embedding.openai.api-key" to "not-a-secret",
            "app.service.core.embedding.openai.model" to "text-embedding-3-small",
        )

        for (blanked in complete.keys) {
            for (blank in listOf("", "   ")) {
                val failure = failureFor(
                    complete + mapOf(
                        blanked to blank,
                        "app.service.core.embedding" to "openai",
                    )
                )

                assertThat(failure)
                    .describedAs("expected a failure that names %s, blank '%s'", blanked, blank)
                    .hasMessageContaining(blanked)
            }
        }
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(OpenAiEmbeddingConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context -> failure = context.startupFailure }
        return failure ?: error("expected a startup failure for $properties")
    }
}
