package com.demo.chat.config.embedding.openai

import org.springframework.ai.document.MetadataMode
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingModel
import org.springframework.ai.openai.OpenAiEmbeddingOptions
import org.springframework.ai.openai.api.OpenAiApi
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
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "openai")
class OpenAiEmbeddingConfiguration {

    @Bean
    fun openAiEmbeddingModel(
        @Value("\${app.service.core.embedding.openai.base-url:}") baseUrl: String,
        @Value("\${app.service.core.embedding.openai.api-key:}") apiKey: String,
        @Value("\${app.service.core.embedding.openai.model:}") model: String,
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
}
