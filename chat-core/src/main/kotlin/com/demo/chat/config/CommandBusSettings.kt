package com.demo.chat.config

import com.demo.chat.domain.command.CompletionRequirement
import org.springframework.core.env.PropertyResolver
import java.time.Duration

/** The duration syntax of the command properties: an integer and one of ms, s, m, h, d. */
object CommandDurations {
    private val pattern = Regex("^(\\d+)(ms|s|m|h|d)$")

    fun parse(property: String, text: String): Duration {
        val match = pattern.matchEntire(text.trim())
            ?: throw IllegalStateException(
                "$property=$text is not a duration. Use an integer and one unit: ms, s, m, h, or d. Example: 30s."
            )
        val amount = match.groupValues[1].toLong()
        val duration = when (match.groupValues[2]) {
            "ms" -> Duration.ofMillis(amount)
            "s" -> Duration.ofSeconds(amount)
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            else -> Duration.ofDays(amount)
        }
        if (duration.isZero) throw IllegalStateException("$property=$text must be positive.")
        return duration
    }
}

data class CommandBusSettings(
    val requirement: CompletionRequirement,
    val timeout: Duration,
    val recoveryInterval: Duration,
) {
    companion object {
        fun from(env: PropertyResolver): CommandBusSettings = CommandBusSettings(
            requirement = CompletionRequirement.parse(
                env.getProperty(CommandBusValidation.REQUIREMENT, CommandBusValidation.DEFAULT_REQUIREMENT)
            ),
            timeout = CommandDurations.parse(
                CommandBusValidation.TIMEOUT,
                env.getProperty(CommandBusValidation.TIMEOUT, CommandBusValidation.DEFAULT_TIMEOUT),
            ),
            recoveryInterval = CommandDurations.parse(
                CommandBusValidation.RECOVERY_INTERVAL,
                env.getProperty(CommandBusValidation.RECOVERY_INTERVAL, CommandBusValidation.DEFAULT_RECOVERY_INTERVAL),
            ),
        )
    }
}
