package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.AgentResourceServerChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentResourceServerChainTests {

    @Test
    fun `the required authority comes from the configured scope`() {
        assertThat(AgentResourceServerChain.authorityFor("chat.mcp"))
            .isEqualTo("SCOPE_chat.mcp")
    }

    @Test
    fun `a different scope gives a different authority`() {
        assertThat(AgentResourceServerChain.authorityFor("other.scope"))
            .isEqualTo("SCOPE_other.scope")
    }
}
