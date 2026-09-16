package com.demo.chat.test.config

import com.demo.chat.config.EmbeddingIdentityConfiguration
import com.demo.chat.domain.EmbeddingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EmbeddingIdentityTests {

    @Test
    fun `a mock embedding resolves the fixed mock identity`() {
        assertThat(EmbeddingIdentity.of("mock", null)).isEqualTo(EmbeddingIdentity.MOCK)
    }

    @Test
    fun `a mock embedding with an identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("mock", "acme-e5") }
            .hasMessageContaining("app.service.core.embedding.identity")
            .hasMessageContaining("mock")
    }

    @Test
    fun `a production embedding without an identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("openai", null) }
            .hasMessageContaining("app.service.core.embedding.identity")
            .hasMessageContaining("openai")
    }

    @Test
    fun `a production embedding with a blank identity fails`() {
        assertThatThrownBy { EmbeddingIdentity.of("local", "   ") }
            .hasMessageContaining("app.service.core.embedding.identity")
    }

    @Test
    fun `an identity outside the character set fails`() {
        for (illegal in listOf("Acme", "acme_e5", "acme.e5", "-acme", "acme e5", "a".repeat(65))) {
            assertThatThrownBy { EmbeddingIdentity.of("openai", illegal) }
                .describedAs("expected a failure for '%s'", illegal)
                .hasMessageContaining("app.service.core.embedding.identity")
        }
    }

    @Test
    fun `a legal identity resolves to its own value`() {
        for (legal in listOf("a", "acme-e5-small-v2", "0", "a".repeat(64))) {
            assertThat(EmbeddingIdentity.of("openai", legal).value).isEqualTo(legal)
        }
    }

    @Test
    fun `the bean is absent when both selectors are absent`() {
        runner(emptyMap()).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).doesNotHaveBean(EmbeddingIdentity::class.java)
        }
    }

    @Test
    fun `the bean is present when both selectors are set`() {
        runner(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "mock",
            )
        ).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(EmbeddingIdentity::class.java))
                .isEqualTo(EmbeddingIdentity.MOCK)
        }
    }

    @Test
    fun `the bean carries the operator value for a production embedding`() {
        runner(
            mapOf(
                "app.service.core.vector" to "simple",
                "app.service.core.embedding" to "openai",
                "app.service.core.embedding.identity" to "acme-e5-small-v2",
            )
        ).run { context ->
            assertThat(context.getBean(EmbeddingIdentity::class.java).value)
                .isEqualTo("acme-e5-small-v2")
        }
    }

    private fun runner(properties: Map<String, String>): ApplicationContextRunner =
        ApplicationContextRunner()
            .withPropertyValues(*properties.map { "${it.key}=${it.value}" }.toTypedArray())
            .withUserConfiguration(EmbeddingIdentityConfiguration::class.java)
}
