package com.demo.chat.config.embedding.openai

import org.springframework.ai.document.MetadataMode
import org.springframework.ai.retry.RetryUtils
import org.springframework.ai.retry.TransientAiException
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

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
        val api = OpenAiApi.builder()
            .baseUrl(required("app.service.core.embedding.openai.base-url", baseUrl))
            .apiKey(required("app.service.core.embedding.openai.api-key", apiKey))
            .build()

        return OpenAiEmbeddingModel(
            api,
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder()
                .model(required("app.service.core.embedding.openai.model", model))
                .build(),
            retryTemplateFor(maxAttempts),
        )
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
         * The retry policy of one embedding call.
         *
         * An unset property gives RetryUtils.DEFAULT_RETRY_TEMPLATE, which is what
         * the library uses. That template makes 10 attempts and waits between
         * them. It needs 19 minutes to give up on an endpoint that refuses every
         * connection, because ten attempts make nine waits of 2, 10, 50, and then
         * six of 180 seconds. That is 1142 seconds.
         *
         * A set value gives the same policy with that number of attempts. The
         * value 1 makes one attempt and no retry, which a test needs. A test of a
         * dead endpoint cannot wait 19 minutes.
         *
         * The built template matches the library template in every other way. It
         * retries the same two exception types, and it waits 2 seconds, then 5
         * times longer each attempt, up to 180 seconds. It carries no log
         * listener, which is the one difference.
         */
        fun retryTemplateFor(maxAttempts: String): RetryTemplate {
            if (maxAttempts.isBlank()) return RetryUtils.DEFAULT_RETRY_TEMPLATE

            val attempts = maxAttempts.trim().toIntOrNull()
            if (attempts == null || attempts < 1) {
                throw IllegalStateException(
                    "app.service.core.embedding.openai.max-attempts=$maxAttempts is not a " +
                        "whole number of 1 or more. Remove the property to use the default " +
                        "policy of 10 attempts."
                )
            }

            return RetryTemplate.builder()
                .maxAttempts(attempts)
                .retryOn(TransientAiException::class.java)
                .retryOn(ResourceAccessException::class.java)
                .exponentialBackoff(
                    Duration.ofMillis(2000),
                    5.0,
                    Duration.ofMillis(180000),
                )
                .build()
        }
    }
}
