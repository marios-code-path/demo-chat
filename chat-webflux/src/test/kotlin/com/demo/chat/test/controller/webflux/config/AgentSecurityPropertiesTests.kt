package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
        "app.security.required-scope=chat.mcp",
        "app.security.agents[0].client-id=client-a",
        "app.security.agents[0].username=agent-a",
        "app.security.agents[1].client-id=client-b",
        "app.security.agents[1].username=agent-b",
        "app.security.jwt.jwk-path=/tmp/agent-test.jwk",
    )

    private fun refusal(vararg values: String, message: String) {
        runner.withPropertyValues(*values).run { context ->
            assertThat(context.startupFailure).isNull()
            assertThatThrownBy { context.getBean(AgentSecurityProperties::class.java).requireComplete() }
                .hasMessageContaining(message)
        }
    }

    @Test
    fun `two agents and the scope bind`() {
        runner.withPropertyValues(*complete).run { context ->
            val complete = context.getBean(AgentSecurityProperties::class.java).requireComplete()
            assertThat(complete.agents.map { it.clientId }).containsExactly("client-a", "client-b")
            assertThat(complete.agents.map { it.username }).containsExactly("agent-a", "agent-b")
            assertThat(complete.requiredScope).isEqualTo("chat.mcp")
            assertThat(complete.requiredAuthority()).isEqualTo("SCOPE_chat.mcp")
            assertThat(complete.jwt.jwkPath).isEqualTo("/tmp/agent-test.jwk")
        }
    }

    @Test
    fun `the core may omit all agent values`() {
        runner.run { context ->
            assertThat(context.startupFailure).isNull()
            assertThat(context.getBean(AgentSecurityProperties::class.java).isConfigured()).isFalse()
        }
    }

    @Test
    fun `a service account list alone does not configure the agent path`() {
        runner.withPropertyValues("app.security.service-accounts=Service,Relay").run { context ->
            assertThat(context.getBean(AgentSecurityProperties::class.java).isConfigured()).isFalse()
        }
    }

    @Test
    fun `an absent scope names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.required-scope") }.toTypedArray(),
            message = "app.security.required-scope is required.")

    @Test
    fun `an empty agent list names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents") }.toTypedArray(),
            message = "app.security.agents requires at least one entry.")

    @Test
    fun `an entry with no client id names the index`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents[1].client-id") }.toTypedArray(),
            message = "app.security.agents[1].client-id is required.")

    @Test
    fun `an entry with no username names the index`() =
        refusal(*complete.filterNot { it.startsWith("app.security.agents[0].username") }.toTypedArray(),
            message = "app.security.agents[0].username is required.")

    @Test
    fun `an absent jwk path names the property`() =
        refusal(*complete.filterNot { it.startsWith("app.security.jwt.jwk-path") }.toTypedArray(),
            message = "app.security.jwt.jwk-path is required.")

    @Test
    fun `a shared client id fails and names it`() =
        refusal(*complete, "app.security.agents[1].client-id=client-a",
            message = "app.security.agents names client id 'client-a' twice.")

    @Test
    fun `a shared username fails and names it`() =
        refusal(*complete, "app.security.agents[1].username=agent-a",
            message = "app.security.agents names username 'agent-a' twice.")

    @Test
    fun `client ids compare exactly`() {
        runner.withPropertyValues(*complete, "app.security.agents[1].client-id=CLIENT-A").run { context ->
            assertThat(context.getBean(AgentSecurityProperties::class.java).requireComplete().agents)
                .hasSize(2)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Admin", "admin", "ANON", "Anon", "service", "Service"])
    fun `a reserved username fails and names it`(name: String) =
        refusal(*complete, "app.security.agents[1].username=$name",
            message = "app.security.agents names reserved username '$name'. An agent must be a plain user.")

    @Test
    fun `a configured service account is reserved`() =
        refusal(*complete, "app.security.service-accounts=Service,Relay", "app.security.agents[1].username=relay",
            message = "app.security.agents names reserved username 'relay'. An agent must be a plain user.")

    @Test
    fun `usernames that differ by case alone are one username`() =
        refusal(*complete, "app.security.agents[0].username=Claude", "app.security.agents[1].username=claude",
            message = "app.security.agents names username 'claude' twice.")

    @Test
    fun `a higher source replaces the whole agent list`() {
        val sources = org.springframework.core.env.MutablePropertySources().apply {
            addLast(org.springframework.core.env.MapPropertySource("launch", mapOf(
                "app.security.agents[0].client-id" to "client-launch",
                "app.security.agents[0].username" to "launch",
            )))
            addLast(org.springframework.core.env.MapPropertySource("file", mapOf(
                "app.security.agents[0].client-id" to "client-file-a",
                "app.security.agents[0].username" to "file-a",
                "app.security.agents[1].client-id" to "client-file-b",
                "app.security.agents[1].username" to "file-b",
            )))
        }

        val bound = org.springframework.boot.context.properties.bind.Binder(
            org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(sources)
        ).bind("app.security", AgentSecurityProperties::class.java).get()

        assertThat(bound.agents.map { it.clientId }).containsExactly("client-launch")
    }

    /**
     * The old keys alone give the guard message, whatever bean calls first.
     * See `CHAT-frcrctdp`.
     */
    @Test
    fun `an old key alone fails requireComplete with the guard message`() {
        runner.withPropertyValues(
            "app.security.agent.client-id=client-a",
            "app.security.agent.username=agent-a",
            "app.security.jwt.jwk-path=/tmp/agent-test.jwk",
        ).run { context ->
            val properties = context.getBean(AgentSecurityProperties::class.java)
            assertThat(properties.isConfigured()).isTrue()
            assertThatThrownBy { properties.requireComplete() }.hasMessage(
                "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp."
            )
        }
    }
}
