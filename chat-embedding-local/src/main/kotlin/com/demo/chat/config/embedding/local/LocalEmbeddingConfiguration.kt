package com.demo.chat.config.embedding.local

import com.demo.chat.domain.EmbeddingIdentity
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.transformers.TransformersEmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

/**
 * An in-process EmbeddingModel that loads an ONNX model and a tokenizer.
 *
 * Spring AI reads file:, classpath:, and https: resources. So an operator
 * supplies a model from disk, from the artifact, or from a remote host.
 *
 * The module depends on the model library and not on the starter. See
 * OpenAiEmbeddingConfiguration for the reason.
 *
 * The cache directory carries the identity, and that is the point of this
 * class. ResourceCacheService in Spring AI 1.0.3 caches a remote resource by
 * its location. A new identity with unchanged URIs would load the old bytes,
 * and the corpus would carry vectors from the previous model under the new
 * name.
 *
 * An operator who wants no caching names the local resources with file:, which
 * the cache does not copy.
 *
 * TransformersEmbeddingModel implements InitializingBean, so the container
 * loads the model after this factory method returns. The method must not call
 * afterPropertiesSet itself. A direct call loads the 86.2 MiB ONNX file twice
 * and builds two ONNX sessions, and the second session replaces the first.
 * The model still loads during startup, so an absent file still fails startup.
 *
 * Both URI properties are required, and the module reads each one with an
 * empty default. See the required function below for the reason.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["embedding"], havingValue = "local")
class LocalEmbeddingConfiguration {

    @Bean
    fun localEmbeddingModel(
        identity: EmbeddingIdentity,
        @Value("\${app.service.core.embedding.local.model-uri:}") modelUri: String,
        @Value("\${app.service.core.embedding.local.tokenizer-uri:}") tokenizerUri: String,
        @Value("\${app.service.core.embedding.local.cache-path:#{systemProperties['java.io.tmpdir']}/chat-embedding-local}")
        cachePath: String,
    ): EmbeddingModel {
        val cacheDirectory = cacheDirectoryFor(cachePath, identity)
        Files.createDirectories(cacheDirectory)

        val model = TransformersEmbeddingModel()
        model.setModelResource(required("app.service.core.embedding.local.model-uri", modelUri))
        model.setTokenizerResource(
            required("app.service.core.embedding.local.tokenizer-uri", tokenizerUri)
        )
        model.setResourceCacheDirectory(cacheDirectory.toString())
        return model
    }

    /**
     * Each URI property carries an empty default, and this function rejects it.
     *
     * A @Value with no default fails the context, and the property name then
     * sits in the cause of the failure rather than in its message. An operator
     * reads the property name. The openai provider uses the same function for
     * the same reason.
     */
    private fun required(property: String, value: String): String {
        if (value.isBlank()) {
            throw IllegalStateException(
                "$property is not set, and app.service.core.embedding=local. " +
                    "This provider requires a value that is not blank."
            )
        }
        return value
    }

    companion object {
        /**
         * The cache directory of one identity, under the configured base.
         *
         * The default base is ephemeral, which follows the embedded store. The
         * corpus is a derived cache, so a lost cache is a rebuild.
         */
        fun cacheDirectoryFor(base: String, identity: EmbeddingIdentity): Path =
            Path.of(base, identity.value)
    }
}
