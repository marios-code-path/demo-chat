package com.demo.chat.config.embedding.openai

import com.openai.client.OpenAIClient
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import org.springframework.ai.document.MetadataMode
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * An EmbeddingModel that reaches an OpenAI-compatible endpoint.
 *
 * Spring AI reaches such an endpoint through a base URL, so this provider
 * serves OpenAI and any service that speaks the same API.
 *
 * The module depends on the model library and not on the starter. A starter
 * carries auto-configuration. Both provider modules sit on one classpath, and
 * two starters would let Spring AI build models that no selector asked for.
 * The selector must be the only thing that decides. So this class constructs
 * the model, and a condition guards it.
 *
 * All three properties are required. OpenAiApi accepts a NoopApiKey in Spring
 * AI 1.0.3, so the library does not force a key. This design requires one
 * anyway. A blank key reaches a remote service as an anonymous call, and an
 * operator cannot tell a missing key from an intended one.
 *
 * max-attempts is optional. See retryTemplateFor in the companion object.
 *
 * That function sits in the companion object because a test calls it. A test
 * cannot read the retry template of a built model, because the library keeps
 * it private.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "openai")
class OpenAiEmbeddingConfiguration {

    @Bean
    fun openAiEmbeddingModel(
        @Value("\${app.service.core.embedding.openai.base-url:}") baseUrl: String,
        @Value("\${app.service.core.embedding.openai.api-key:}") apiKey: String,
        @Value("\${app.service.core.embedding.openai.model:}") model: String,
        @Value("\${app.service.core.embedding.openai.max-attempts:}") maxAttempts: String,
    ): EmbeddingModel {
        val options = ClientOptions.builder()
            // Spring AI supplies the transport. The convenience builder of the
            // SDK lives in another artifact that this classpath does not hold.
            .httpClient(SpringAiOpenAiHttpClient.builder().build())
            .baseUrl(required("app.service.core.embedding.openai.base-url", baseUrl))
            .apiKey(required("app.service.core.embedding.openai.api-key", apiKey))
            .maxRetries(maxRetriesFor(maxAttempts))
            .build()

        val client: OpenAIClient = OpenAIClientImpl(options)

        return OpenAiEmbeddingModel.builder()
            .openAiClient(client)
            .metadataMode(MetadataMode.EMBED)
            .options(
                OpenAiEmbeddingOptions.builder()
                    .model(required("app.service.core.embedding.openai.model", model))
                    .build()
            )
            .build()
    }

    /**
     * Each property carries an empty default, and this function rejects it.
     *
     * A @Value with no default fails the context, but the message names the
     * bean and the parameter rather than the property. An operator reads the
     * property name. A blank value must fail the same way as an absent one,
     * because a blank key reaches a remote service as an anonymous call.
     */
    private fun required(property: String, value: String): String {
        if (value.isBlank()) {
            throw IllegalStateException(
                "$property is not set, and app.service.core.embedding=openai. " +
                    "This provider requires a value that is not blank."
            )
        }
        return value
    }

    companion object {
        /**
         * The retry count of one embedding call, in the unit the SDK uses.
         *
         * **The property counts calls. The SDK counts retries after the first
         * call.** So a property value of 1 means one call and no retry, and it
         * maps to 0. The translation is `attempts - 1`, and it lives here so
         * that one place holds it.
         *
         * An absent property gives 9, which is ten calls. Spring AI 1.0.3 made
         * ten attempts through `RetryUtils.DEFAULT_RETRY_TEMPLATE`, and this
         * value keeps that behaviour for every deployment that sets nothing.
         *
         * **The SDK default is 2, which is three calls.** Taking it would cut
         * ten calls to three, and no test would report the change. So this
         * function always sets a value. See CHAT-chsvdqbi.
         *
         * Spring AI 2.0 removed `RetryTemplate`, `RetryUtils` and
         * `TransientAiException`. The SDK owns the retry now, so a test reads
         * the number of calls rather than a template.
         */
        fun maxRetriesFor(maxAttempts: String): Int {
            if (maxAttempts.isBlank()) return DEFAULT_ATTEMPTS - 1

            val attempts = maxAttempts.trim().toIntOrNull()
            if (attempts == null || attempts < 1) {
                throw IllegalStateException(
                    "app.service.core.embedding.openai.max-attempts=$maxAttempts is not a " +
                        "whole number of 1 or more. Remove the property to use the default " +
                        "of $DEFAULT_ATTEMPTS attempts."
                )
            }

            return attempts - 1
        }

        /** Ten calls, which is what Spring AI 1.0.3 did with no property set. */
        const val DEFAULT_ATTEMPTS = 10
    }
}
