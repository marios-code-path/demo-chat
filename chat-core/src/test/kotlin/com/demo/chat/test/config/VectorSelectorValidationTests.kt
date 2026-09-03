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
    fun `both unset starts`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
        }
    }

    @Test
    fun `legal pairs start`() {
        for ((vector, embedding) in listOf("mock" to "mock", "simple" to "mock", "redis" to "mock")) {
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
