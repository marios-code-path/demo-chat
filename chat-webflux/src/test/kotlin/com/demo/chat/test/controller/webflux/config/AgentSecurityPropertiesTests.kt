package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class AgentSecurityPropertiesTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentSecurityProperties::class)
    class PropertiesOnly

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(PropertiesOnly::class.java)

    private val complete = arrayOf(
        "app.security.agent.client-id=31649af5-0154-4be5-8695-fda9d18b7981",
        "app.security.agent.username=agent-svc",
        "app.security.agent.required-scope=chat.mcp",
        "app.security.jwt.jwk-path=/tmp/agent-test.jwk",
    )

    @Test
    fun `the four values bind`() {
        runner.withPropertyValues(*complete).run { context ->
            val properties = context.getBean(AgentSecurityProperties::class.java)
            assertThat(properties.agent.username).isEqualTo("agent-svc")
            assertThat(properties.agent.requiredScope).isEqualTo("chat.mcp")
            assertThat(properties.jwt.jwkPath).isEqualTo("/tmp/agent-test.jwk")
        }
    }

    @Test
    fun `an absent client id fails the context and names the property`() {
        val withoutClientId = complete.filterNot { it.startsWith("app.security.agent.client-id") }
        runner.withPropertyValues(*withoutClientId.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.client-id") }
        }
    }

    @Test
    fun `an absent username fails the context and names the property`() {
        val withoutUsername = complete.filterNot { it.startsWith("app.security.agent.username") }
        runner.withPropertyValues(*withoutUsername.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.username") }
        }
    }

    @Test
    fun `an absent scope fails the context and names the property`() {
        val withoutScope = complete.filterNot { it.startsWith("app.security.agent.required-scope") }
        runner.withPropertyValues(*withoutScope.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.agent.required-scope") }
        }
    }

    @Test
    fun `an absent jwk path fails the context and names the property`() {
        val withoutJwk = complete.filterNot { it.startsWith("app.security.jwt.jwk-path") }
        runner.withPropertyValues(*withoutJwk.toTypedArray()).run { context ->
            assertThat(context.startupFailure).isNotNull
            assertThat(rootMessages(context.startupFailure!!))
                .anyMatch { it.contains("app.security.jwt.jwk-path") }
        }
    }

    private fun rootMessages(failure: Throwable): List<String> {
        val messages = mutableListOf<String>()
        var current: Throwable? = failure
        while (current != null) {
            messages += current.message.orEmpty()
            current = current.cause
        }
        return messages
    }
}
