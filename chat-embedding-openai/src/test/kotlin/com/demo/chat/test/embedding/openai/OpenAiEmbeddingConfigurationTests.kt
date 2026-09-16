package com.demo.chat.test.embedding.openai

import com.demo.chat.config.embedding.openai.OpenAiEmbeddingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.retry.RetryUtils
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.ResourceAccessException

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

    @Test
    fun `a max attempts value of one supplies an embedding model`() {
        // A test of a dead endpoint needs one attempt. The default policy
        // makes 10 attempts and needs 19 minutes to give up.
        runner(
            mapOf(
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
                "app.service.core.embedding.openai.api-key" to "not-a-secret",
                "app.service.core.embedding.openai.model" to "text-embedding-3-small",
                "app.service.core.embedding.openai.max-attempts" to "1",
            )
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(EmbeddingModel::class.java)
        }
    }

    @Test
    fun `an illegal max attempts value fails the context and names that property`() {
        for (illegal in listOf("0", "-1", "two", "1.5")) {
            val failure = failureFor(
                mapOf(
                    "app.service.core.embedding" to "openai",
                    "app.service.core.embedding.openai.base-url" to "http://localhost:9999",
                    "app.service.core.embedding.openai.api-key" to "not-a-secret",
                    "app.service.core.embedding.openai.model" to "text-embedding-3-small",
                    "app.service.core.embedding.openai.max-attempts" to illegal,
                )
            )

            assertThat(failure)
                .describedAs("expected a failure for '%s'", illegal)
                .hasMessageContaining("app.service.core.embedding.openai.max-attempts")
        }
    }

    @Test
    fun `an unset value gives the library template`() {
        // The identity check, and not a count. A template that this class
        // built could make 10 attempts and still differ from the library
        // template, because the library template also carries a log listener.
        // No deployment may lose that template.
        assertThat(OpenAiEmbeddingConfiguration.retryTemplateFor(""))
            .isSameAs(RetryUtils.DEFAULT_RETRY_TEMPLATE)
        assertThat(OpenAiEmbeddingConfiguration.retryTemplateFor("   "))
            .isSameAs(RetryUtils.DEFAULT_RETRY_TEMPLATE)
    }

    @Test
    fun `each value makes that many attempts`() {
        // The value must reach the template. A hardcoded one attempt would
        // pass every other test in this class, and so would a template that
        // ignored the value.
        //
        // The values stop at 2, which costs one wait of 2 seconds. The waits
        // are 2, 10, 50, and then 180 seconds, so a third value would add 10
        // seconds to every build. Two values are enough. A template that
        // ignored the value would report the builder default of 3, and a
        // hardcoded template would report one number for both values.
        for (attempts in 1..2) {
            assertThat(attemptsUnder(OpenAiEmbeddingConfiguration.retryTemplateFor("$attempts")))
                .describedAs("max-attempts=%d", attempts)
                .isEqualTo(attempts)
        }
    }

    @Test
    fun `a value other than one does not give the library template`() {
        assertThat(OpenAiEmbeddingConfiguration.retryTemplateFor("2"))
            .isNotSameAs(RetryUtils.DEFAULT_RETRY_TEMPLATE)
    }

    /**
     * Counts the calls that one template makes before it gives up.
     *
     * The callback throws ResourceAccessException, which is one of the two
     * types the library template retries. A refused connection reaches the
     * caller as that type.
     */
    private fun attemptsUnder(template: RetryTemplate): Int {
        var calls = 0

        try {
            template.execute<Unit, ResourceAccessException> {
                calls++
                throw ResourceAccessException("the endpoint refused the connection")
            }
        } catch (expected: ResourceAccessException) {
            // Every attempt failed, which is the case under test.
        }

        return calls
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
