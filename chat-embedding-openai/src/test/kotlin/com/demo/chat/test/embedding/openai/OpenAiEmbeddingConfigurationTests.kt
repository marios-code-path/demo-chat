package com.demo.chat.test.embedding.openai

import com.demo.chat.config.embedding.openai.OpenAiEmbeddingConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.embedding.EmbeddingModel
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.openai.core.RequestOptions
import com.openai.core.http.Headers
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import org.springframework.ai.document.MetadataMode
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

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
    fun `an unset value keeps ten calls`() {
        // Spring AI 1.0.3 made ten attempts with no property set. The SDK
        // default is 2 retries, which is three calls. So an unset property
        // must still give nine retries, or every deployment that sets nothing
        // quietly loses seven calls.
        assertThat(OpenAiEmbeddingConfiguration.maxRetriesFor(""))
            .isEqualTo(OpenAiEmbeddingConfiguration.DEFAULT_ATTEMPTS - 1)
        assertThat(OpenAiEmbeddingConfiguration.maxRetriesFor("   "))
            .isEqualTo(9)
    }

    @Test
    fun `each value makes that many calls against a dead endpoint`() {
        // The measurement is the number of calls, not the shape of a policy.
        // The property counts calls and the SDK counts retries, so a direct
        // copy of the number would make one call fewer than the operator asked
        // for. This test fails on that mistake.
        //
        // The values stop at 3. The SDK waits between retries, so a larger
        // value adds seconds to every build.
        for (attempts in 1..3) {
            val calls = callsUnder(attempts)

            assertThat(calls)
                .describedAs("max-attempts=%d", attempts)
                .isEqualTo(attempts)
        }
    }

    /**
     * Counts the calls that one `max-attempts` value makes.
     *
     * The endpoint is dead by construction. The transport answers every call
     * with 503, which the SDK retries, and it never opens a socket.
     */
    private fun callsUnder(attempts: Int): Int {
        val calls = AtomicInteger()

        val options = ClientOptions.builder()
            .httpClient(RefusingHttpClient(calls))
            .baseUrl("http://dead.invalid")
            .apiKey("test-key")
            .maxRetries(OpenAiEmbeddingConfiguration.maxRetriesFor("$attempts"))
            .build()

        val model = OpenAiEmbeddingModel.builder()
            .openAiClient(OpenAIClientImpl(options))
            .metadataMode(MetadataMode.EMBED)
            .options(OpenAiEmbeddingOptions.builder().model("test-model").build())
            .build()

        runCatching { model.embed("one text") }

        return calls.get()
    }

    /** Answers 503 and counts. It opens no connection. */
    private class RefusingHttpClient(private val calls: AtomicInteger) : HttpClient {

        override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
            calls.incrementAndGet()
            return RefusedResponse()
        }

        override fun executeAsync(
            request: HttpRequest,
            requestOptions: RequestOptions,
        ): CompletableFuture<HttpResponse> =
            CompletableFuture.completedFuture(execute(request, requestOptions))

        override fun close() = Unit
    }

    private class RefusedResponse : HttpResponse {
        override fun statusCode(): Int = 503
        override fun headers(): Headers = Headers.builder().build()
        override fun body(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun close() = Unit
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
