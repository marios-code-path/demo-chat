package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.agent.AgentSecurityConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner

/**
 * A REST start with the old agent keys fails with the guard message. See
 * `CHAT-frcrctdp`.
 *
 * The decoder bean reads the agent properties before the resource server
 * chain. So the message must not depend on which bean is created first.
 */
class AgentSecurityConfigurationStartTests {

    @Test
    fun `the old keys alone fail the REST start with the guard message`() {
        ReactiveWebApplicationContextRunner()
            .withUserConfiguration(AgentSecurityConfiguration::class.java)
            .withBean(CompositeServiceBeans::class.java, { Mockito.mock(CompositeServiceBeans::class.java) })
            .withPropertyValues(
                "app.primary=REST",
                "app.security.agent.client-id=client-a",
                "app.security.agent.username=agent-a",
                "app.security.agent.required-scope=chat.mcp",
                "app.security.jwt.jwk-path=${WebFluxTestSigningKey.path()}",
            )
            .run { context ->
                assertThat(context.startupFailure).hasRootCauseMessage(
                    "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. See CHAT-frcrctdp."
                )
            }
    }
}
