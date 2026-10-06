package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentSecurityPropertiesGuard
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

/**
 * The single agent keys fail the start. See `CHAT-frcrctdp`.
 *
 * `StandardEnvironment` also holds the real system environment. Run these
 * tests where no `APP_SECURITY_AGENT_*` variable is set.
 */
class AgentSecurityPropertiesGuardTests {

    private fun environment(vararg values: Pair<String, Any>) = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", mapOf(*values)))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "app.security.agent.client-id", "app.security.agent.username",
            "app.security.agent.required-scope", "app.security.agent.clientId",
        ]
    )
    fun `an old key fails the start`(key: String) {
        assertThatThrownBy { AgentSecurityPropertiesGuard.requireNoLegacyKeys(environment(key to "x")) }
            .hasMessage(
                "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp."
            )
    }

    @Test
    fun `an environment variable form of an old key fails the start`() {
        val env = StandardEnvironment().apply {
            propertySources.addFirst(
                SystemEnvironmentPropertySource("systemEnvironment", mapOf("APP_SECURITY_AGENT_CLIENTID" to "x"))
            )
        }

        assertThatThrownBy { AgentSecurityPropertiesGuard.requireNoLegacyKeys(env) }
            .hasMessageContaining("app.security.agent.* is replaced")
    }

    @Test
    fun `the new keys pass`() {
        assertThatCode {
            AgentSecurityPropertiesGuard.requireNoLegacyKeys(
                environment(
                    "app.security.agents[0].client-id" to "a",
                    "app.security.agents[0].username" to "b",
                    "app.security.required-scope" to "chat.mcp",
                )
            )
        }.doesNotThrowAnyException()
    }
}
