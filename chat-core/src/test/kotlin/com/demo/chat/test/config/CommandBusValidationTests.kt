package com.demo.chat.test.config

import com.demo.chat.config.CommandBusValidation
import com.demo.chat.config.CommandDurations
import com.demo.chat.domain.command.BackendId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.time.Duration

class CommandBusValidationTests {

    private fun composite(vararg pairs: Pair<String, String>) = MockEnvironment().apply {
        setProperty("app.service.composite", "")
        pairs.forEach { (name, value) -> setProperty(name, value) }
    }

    @Test
    fun `a composition without the composite is not checked`() {
        assertThat(CommandBusValidation.appliesTo(MockEnvironment())).isFalse()
    }

    @Test
    fun `memory with defaults passes and gives the starting values`() {
        val settings = CommandBusValidation.validate(composite("app.command.bus" to "memory"))
        assertThat(settings.requirement.backends).containsExactlyInAnyOrder(BackendId.PERSISTENCE, BackendId.INDEX)
        assertThat(settings.timeout).isEqualTo(Duration.ofSeconds(5))
        assertThat(settings.recoveryInterval).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `an unset bus fails and names the property`() {
        assertThatThrownBy { CommandBusValidation.validate(composite()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("app.command.bus is not set")
    }

    @Test
    fun `an unknown bus fails and names the value`() {
        assertThatThrownBy { CommandBusValidation.validate(composite("app.command.bus" to "rabbit")) }
            .hasMessageContaining("app.command.bus=rabbit")
    }

    @Test
    fun `kafka fails and names Stage 2`() {
        assertThatThrownBy { CommandBusValidation.validate(composite("app.command.bus" to "kafka")) }
            .hasMessageContaining("Stage 2")
    }

    @Test
    fun `a finite ttl fails`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.ttl" to "10m"))
        }.hasMessageContaining("app.command.ttl=10m")
    }

    @Test
    fun `a disabled ttl passes`() {
        CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.ttl" to "disabled"))
    }

    @Test
    fun `a declared replica count above one fails and states the limit of the check`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.command.replicas" to "2"))
        }.hasMessageContaining("app.command.replicas=2")
            .hasMessageContaining("does not detect other processes")
    }

    @Test
    fun `a requirement on V without the vector selector fails`() {
        assertThatThrownBy {
            CommandBusValidation.validate(
                composite("app.command.bus" to "memory", "app.command.completion.requirement" to "P,V")
            )
        }.hasMessageContaining("app.command.completion.requirement=P,V")
            .hasMessageContaining("app.service.core.vector")
    }

    @Test
    fun `a requirement on V with the vector selector passes`() {
        CommandBusValidation.validate(
            composite(
                "app.command.bus" to "memory",
                "app.command.completion.requirement" to "P,V",
                "app.service.core.vector" to "simple",
            )
        )
    }

    @Test
    fun `a provider without safe-repeat evidence fails and names the property`() {
        assertThatThrownBy {
            CommandBusValidation.validate(composite("app.command.bus" to "memory", "app.service.core.pubsub" to "redis-xstream"))
        }.hasMessageContaining("app.service.core.pubsub=redis-xstream")
            .hasMessageContaining("no safe-repeat evidence")
    }

    @Test
    fun `each supported provider passes`() {
        CommandBusValidation.SUPPORTED_PROVIDERS.forEach { (property, values) ->
            values.forEach { value -> CommandBusValidation.validate(composite("app.command.bus" to "memory", property to value)) }
        }
    }

    @Test
    fun `durations parse each unit`() {
        assertThat(CommandDurations.parse("x", "250ms")).isEqualTo(Duration.ofMillis(250))
        assertThat(CommandDurations.parse("x", "30s")).isEqualTo(Duration.ofSeconds(30))
        assertThat(CommandDurations.parse("x", "2m")).isEqualTo(Duration.ofMinutes(2))
        assertThat(CommandDurations.parse("x", "1h")).isEqualTo(Duration.ofHours(1))
        assertThat(CommandDurations.parse("x", "30d")).isEqualTo(Duration.ofDays(30))
    }

    @Test
    fun `a malformed or zero duration fails and names the property`() {
        assertThatThrownBy { CommandDurations.parse("app.command.recovery.interval", "soon") }
            .hasMessageContaining("app.command.recovery.interval=soon")
        assertThatThrownBy { CommandDurations.parse("app.command.recovery.interval", "0s") }
            .hasMessageContaining("must be positive")
    }
}
