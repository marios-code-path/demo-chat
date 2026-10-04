package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.agent.CoreRestControllers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * A REST launch refuses each core REST controller. See `CHAT-bnnkhgbd`.
 *
 * The guard reads each switch as `@ConditionalOnProperty` reads it. So a flag
 * with no value mounts the controller, and the guard must refuse it too.
 */
class CoreRestControllersTests {

    @ParameterizedTest
    @ValueSource(strings = ["persistence", "index", "key", "secrets", "pubsub"])
    fun `each core switch alone refuses the start and names the property`(name: String) {
        val environment = MockEnvironment().withProperty("app.controller.$name", "true")

        assertThatThrownBy { CoreRestControllers.requireAbsent(environment) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("app.controller.$name")
            .hasMessageContaining("CHAT-bnnkhgbd")
    }

    @Test
    fun `a switch with no value mounts its controller, so it refuses the start`() {
        val environment = MockEnvironment().withProperty("app.controller.persistence", "")

        assertThat(CoreRestControllers.enabled(environment)).containsExactly("persistence")
    }

    @Test
    fun `a switch set to false mounts nothing, so the start proceeds`() {
        val environment = MockEnvironment()
            .withProperty("app.controller.persistence", "false")
            .withProperty("app.controller.index", "FALSE")

        assertThatCode { CoreRestControllers.requireAbsent(environment) }.doesNotThrowAnyException()
    }

    @Test
    fun `the composite and recall switches do not refuse the start`() {
        val environment = MockEnvironment()
            .withProperty("app.controller.topic", "true")
            .withProperty("app.controller.message", "true")
            .withProperty("app.controller.user", "true")
            .withProperty("app.controller.recall", "true")

        assertThatCode { CoreRestControllers.requireAbsent(environment) }.doesNotThrowAnyException()
    }

    @Test
    fun `every refused switch is named`() {
        val environment = MockEnvironment()
            .withProperty("app.controller.persistence", "true")
            .withProperty("app.controller.key", "true")

        assertThatThrownBy { CoreRestControllers.requireAbsent(environment) }
            .hasMessageContaining("app.controller.persistence, app.controller.key")
    }
}
