package com.demo.chat.test.embedding.local

import com.demo.chat.config.embedding.local.LocalEmbeddingConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a real model through the configuration, and embeds real text.
 *
 * The other tests in this module prove the selector rule and the cache path
 * function. None of them loads a model, so none of them proves that this
 * module can supply a working EmbeddingModel.
 *
 * The revision is pinned. A main URL serves whatever that branch holds today,
 * and the identity states which model wrote a corpus. A mutable URL under a
 * fixed identity would let two different models share one name.
 *
 * Every test here needs an opt in, which is what the design document
 * requires. It says that a run of this module stays manual and never runs
 * unattended.
 *
 * The integration tag keeps all three out of the default build.
 * -Dchat.embedding.local.manual=true is the switch for the class. Without it
 * every test here is skipped, whatever sits in the cache directory. A check on
 * the downloaded files alone would not hold the rule, because a developer who
 * ran the download once would load a 90 MiB model in every later integration
 * build.
 *
 * The two file tests also need the two downloaded files, which live outside
 * the repository because they total near 87 MiB. The one remote test also
 * needs -Dchat.embedding.local.remote=true, because it downloads two more
 * sets of that size. The plan step beside this class carries the download,
 * the two checksums, and both properties.
 */
@Tag("integration")
class LocalEmbeddingModelTests {

    companion object {
        const val REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41"
        const val BASE =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/$REVISION"
        const val EXPECTED_DIMENSIONS = 384

        val modelPath: Path = Path.of(
            System.getProperty("user.home"), ".cache", "chat-embedding-local-model", "model.onnx"
        )
        val tokenizerPath: Path = Path.of(
            System.getProperty("user.home"), ".cache", "chat-embedding-local-model", "tokenizer.json"
        )

        /**
         * The opt in that every test in this class needs.
         *
         * The design document states that a run of this module stays manual
         * and never runs unattended. A check on the downloaded files alone
         * would not hold that rule. Once a developer runs the download, every
         * later integration build on that machine would load a 90 MiB model
         * without anyone asking for it.
         */
        @JvmStatic
        fun manualEnabled(): Boolean =
            System.getProperty("chat.embedding.local.manual") == "true"

        /** The opt in, and the two downloaded files. */
        @JvmStatic
        fun localModelEnabled(): Boolean =
            manualEnabled() &&
                Files.isRegularFile(modelPath) &&
                Files.isRegularFile(tokenizerPath)

        /**
         * The opt in, and a second one for the network.
         *
         * The one remote test downloads two file sets, which is near 173 MiB.
         * So it carries its own switch beside the class switch.
         */
        @JvmStatic
        fun remoteEnabled(): Boolean =
            manualEnabled() &&
                System.getProperty("chat.embedding.local.remote") == "true"
    }

    private fun runnerFor(
        modelUri: String,
        tokenizerUri: String,
        cache: Path,
        identity: String,
    ): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(
                "app.service.core.embedding=local",
                "app.service.core.embedding.local.model-uri=$modelUri",
                "app.service.core.embedding.local.tokenizer-uri=$tokenizerUri",
                "app.service.core.embedding.local.cache-path=$cache",
            )
            .withBean(EmbeddingIdentity::class.java, { EmbeddingIdentity(identity) })
            .withUserConfiguration(LocalEmbeddingConfiguration::class.java)

    @Test
    @EnabledIf("localModelEnabled")
    fun `the bean loads a real model and embeds text`() {
        val cache = Files.createTempDirectory("local-model-test")

        runnerFor("file:$modelPath", "file:$tokenizerPath", cache, "minilm-l6-v2")
            .run { context ->
                assertThat(context).hasNotFailed()

                val model = context.getBean(EmbeddingModel::class.java)

                assertThat(model.dimensions())
                    .describedAs("all-MiniLM-L6-v2 emits %d dimensions", EXPECTED_DIMENSIONS)
                    .isEqualTo(EXPECTED_DIMENSIONS)

                val vector = model.embed("apple pie recipe")
                assertThat(vector).hasSize(EXPECTED_DIMENSIONS)
                assertThat(vector.any { it != 0f })
                    .describedAs("a real model returns a vector that is not all zero")
                    .isTrue()
            }
    }

    @Test
    @EnabledIf("localModelEnabled")
    fun `two texts that share meaning score above two that do not`() {
        val cache = Files.createTempDirectory("local-model-test")

        runnerFor("file:$modelPath", "file:$tokenizerPath", cache, "minilm-l6-v2")
            .run { context ->
                val model = context.getBean(EmbeddingModel::class.java)

                val near = cosine(
                    model.embed("a recipe for apple pie"),
                    model.embed("how to bake an apple tart"),
                )
                val far = cosine(
                    model.embed("a recipe for apple pie"),
                    model.embed("the compiler emits bytecode"),
                )

                // This is the whole point of a real model. The mock matches on
                // shared substrings, and this one matches on meaning.
                assertThat(near)
                    .describedAs("near %s must beat far %s", near, far)
                    .isGreaterThan(far)
            }
    }

    @Test
    @EnabledIf("remoteEnabled")
    fun `a remote model caches under a directory per identity`() {
        // This test uses https, and that choice is the point of it.
        // ResourceCacheService copies a remote resource into the cache
        // directory. It does not copy a file: resource, so a file: test can
        // never show that the bean passed the right cache directory. A
        // hardcoded setter would pass every other test in this class.
        //
        // Two identities in one test, and not two tests. An earlier shape of
        // this class held one test of a single identity beside one test of
        // two, which cost three file sets per run. This shape costs two, and
        // it proves the same two facts.
        //
        // The second identity is the point. A new identity with unchanged
        // URIs must not load the old bytes, which is the defect this cache
        // directory exists to prevent.
        //
        // This test needs network access, so it needs its own opt in beside
        // the class opt in. The design document keeps every run of this module
        // manual.
        val cache = Files.createTempDirectory("local-model-cache-test")
        val identities = listOf("cache-one", "cache-two")

        for (identity in identities) {
            runnerFor("$BASE/onnx/model.onnx", "$BASE/tokenizer.json", cache, identity)
                .run { context ->
                    assertThat(context).describedAs(identity).hasNotFailed()
                }
        }

        for (identity in identities) {
            val identityDirectory = cache.resolve(identity)

            assertThat(Files.isDirectory(identityDirectory))
                .describedAs("the bean must cache under the directory of %s", identity)
                .isTrue()

            val cached = Files.walk(identityDirectory).use { walk ->
                walk.filter { Files.isRegularFile(it) }.toList()
            }

            assertThat(cached)
                .describedAs("the cache directory of %s must hold the copies", identity)
                .isNotEmpty()

            assertThat(cached.sumOf { Files.size(it) })
                .describedAs("a cached ONNX model is tens of megabytes")
                .isGreaterThan(1_000_000L)
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb))
    }
}
