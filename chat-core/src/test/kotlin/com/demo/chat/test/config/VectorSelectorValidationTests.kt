package com.demo.chat.test.config

import com.demo.chat.config.VectorSelectorValidationConfiguration
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class VectorSelectorValidationTests {

    @Test
    fun `vector set without embedding fails startup naming both selectors`() {
        val failure = failureFor(mapOf("app.service.core.vector" to "simple"))

        assertThat(failure.message)
            .contains("app.service.core.vector")
            .contains("app.service.core.embedding")
    }

    @Test
    fun `embedding set without vector fails startup naming both selectors`() {
        val failure = failureFor(mapOf("app.service.core.embedding" to "mock"))

        assertThat(failure.message)
            .contains("app.service.core.vector")
            .contains("app.service.core.embedding")
    }

    @Test
    fun `reserved gateway embedding fails startup`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "redis", "app.service.core.embedding" to "gateway")
        )

        assertThat(failure.message).contains("app.service.core.embedding=gateway")
    }

    @Test
    fun `unknown vector value fails startup`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "sqlite", "app.service.core.embedding" to "mock")
        )

        assertThat(failure.message).contains("app.service.core.vector=sqlite")
    }

    @Test
    fun `embedded vector without embedding fails startup naming both selectors`() {
        val failure = failureFor(mapOf("app.service.core.vector" to "embedded"))

        assertThat(failure.message)
            .contains("app.service.core.vector=embedded")
            .contains("app.service.core.embedding")
    }

    @Test
    fun `embedded vector with a reserved embedding fails startup`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "embedded", "app.service.core.embedding" to "local")
        )

        assertThat(failure.message).contains("app.service.core.embedding=local")
    }

    @Test
    fun `the illegal pair message lists every legal pair`() {
        val failure = failureFor(
            mapOf("app.service.core.vector" to "sqlite", "app.service.core.embedding" to "mock")
        )

        for ((vector, embedding) in LEGAL_PAIRS) {
            assertThat(failure.message).contains("vector=$vector with embedding=$embedding")
        }
    }

    @Test
    fun `embedded fails startup when the Vector API is absent`() {
        // This module declares no --add-modules jdk.incubator.vector, so the
        // module is absent here. That is the condition under test.
        val failure = failureFor(
            mapOf("app.service.core.vector" to "embedded", "app.service.core.embedding" to "mock")
        )

        assertThat(failure.message)
            .contains("jdk.incubator.vector")
            .contains("--add-modules")
    }

    @Test
    fun `both unset starts`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun `legal pairs start`() {
        // embedded is excluded. This module's test JVM carries no
        // --add-modules jdk.incubator.vector, so the Vector API check
        // rejects it. The positive path is proven in chat-deploy-memory.
        for ((vector, embedding) in LEGAL_PAIRS.filterNot { it.first == "embedded" }) {
            runner(
                mapOf(
                    "app.service.core.vector" to vector,
                    "app.service.core.embedding" to embedding,
                )
            ).run { context ->
                assertThat(context).hasNotFailed()
            }
        }
    }

    private companion object {
        // Mirrors VectorSelectorValidation.legalPairs. Both must change together.
        val LEGAL_PAIRS = listOf(
            "mock" to "mock",
            "simple" to "mock",
            "redis" to "mock",
            "embedded" to "mock",
        )
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(VectorSelectorValidationConfiguration::class.java)

    private fun failureFor(properties: Map<String, String>): Throwable {
        var failure: Throwable? = null
        runner(properties).run { context ->
            failure = context.startupFailure
        }
        return failure
            ?: Assertions.fail<Throwable>("expected a startup failure for $properties")
    }
}
