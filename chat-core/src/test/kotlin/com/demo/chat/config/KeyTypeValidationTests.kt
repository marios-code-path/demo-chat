package com.demo.chat.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import java.util.concurrent.atomic.AtomicInteger

class KeyTypeValidationTests {

    private fun environment(vararg pairs: Pair<String, String>) = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", mapOf(*pairs)))
    }

    private val import = KeyTypeValidation.REQUIREMENTS.single { it.feature == "claude-import" }

    @Test
    fun `the table declares claude import on its switch with uuid alone`() {
        assertThat(import.switch).isEqualTo("app.message.import")
        assertThat(import.supported).containsExactly("uuid")
        assertThat(import.reason).contains("UUID").contains("long")
    }

    @Test
    fun `the gate off starts with a long key type`() {
        KeyTypeValidation.validate(environment("app.key.type" to "long"))
    }

    @Test
    fun `the gate off starts with an unset key type`() {
        KeyTypeValidation.validate(environment())
    }

    @Test
    fun `the gate on starts with a uuid key type`() {
        KeyTypeValidation.validate(environment("app.message.import" to "true", "app.key.type" to "uuid"))
    }

    @Test
    fun `the gate on refuses a long key type and names the feature, the value, the set and the reason`() {
        assertThatThrownBy {
            KeyTypeValidation.validate(environment("app.message.import" to "true", "app.key.type" to "long"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("claude-import")
            .hasMessageContaining("app.key.type=long")
            .hasMessageContaining("[uuid]")
            .hasMessageContaining(import.reason)
    }

    @Test
    fun `the gate on refuses an unset key type`() {
        assertThatThrownBy { KeyTypeValidation.validate(environment("app.message.import" to "true")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("claude-import")
            .hasMessageContaining("app.key.type is not set")
            .hasMessageContaining("[uuid]")
            .hasMessageContaining(import.reason)
    }

    @Test
    fun `an empty gate counts as set and refuses a long key type`() {
        val env = environment("app.message.import" to "", "app.key.type" to "long")
        assertThat(KeyTypeValidation.enabled(env, import)).isTrue()
        assertThatThrownBy { KeyTypeValidation.validate(env) }.hasMessageContaining("claude-import")
    }

    @ParameterizedTest
    @ValueSource(strings = ["false", "FALSE", "False"])
    fun `a false gate does not count as set`(value: String) {
        val env = environment("app.message.import" to value, "app.key.type" to "long")
        assertThat(KeyTypeValidation.enabled(env, import)).isFalse()
        KeyTypeValidation.validate(env)
    }

    @Test
    fun `an absent gate does not count as set`() {
        assertThat(KeyTypeValidation.enabled(environment(), import)).isFalse()
    }

    @Test
    fun `each declared feature starts with each supported key type and refuses another`() {
        KeyTypeValidation.REQUIREMENTS.forEach { requirement ->
            requirement.supported.forEach { keyType ->
                KeyTypeValidation.validate(environment(requirement.switch to "", "app.key.type" to keyType))
            }
            assertThatThrownBy {
                KeyTypeValidation.validate(environment(requirement.switch to "", "app.key.type" to "unsupported"))
            }.hasMessageContaining(requirement.feature)
        }
    }

    @Test
    fun `the refusal fails the start before any singleton is built`() {
        Sentinel.built.set(0)
        val context = AnnotationConfigApplicationContext()
        context.environment.propertySources.addFirst(
            MapPropertySource("test", mapOf("app.message.import" to "", "app.key.type" to "long"))
        )
        context.register(KeyTypeValidationConfiguration::class.java, Sentinel::class.java)

        assertThatThrownBy { context.refresh() }.hasMessageContaining("claude-import")
        assertThat(Sentinel.built.get()).isZero()
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `a supported key type lets the context start`() {
        Sentinel.built.set(0)
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(
                MapPropertySource("test", mapOf("app.message.import" to "", "app.key.type" to "uuid"))
            )
            context.register(KeyTypeValidationConfiguration::class.java, Sentinel::class.java)
            context.refresh()
            assertThat(Sentinel.built.get()).isEqualTo(1)
        }
    }

    open class Sentinel {
        init {
            built.incrementAndGet()
        }

        companion object {
            val built = AtomicInteger()
        }
    }
}
