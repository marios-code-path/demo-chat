package com.demo.chat.config.agent

import com.demo.chat.domain.ChatException
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Refuses the single agent keys that `CHAT-frcrctdp` replaced.
 *
 * **Spring ignores an unbound key.** Without this guard, a launch with the old
 * keys would start with no agent and refuse every token.
 */
object AgentSecurityPropertiesGuard {

    private val LEGACY = ConfigurationPropertyName.of("app.security.agent")

    const val MESSAGE =
        "app.security.agent.* is replaced by app.security.agents[n] and app.security.required-scope. " +
            "See CHAT-frcrctdp."

    fun requireNoLegacyKeys(environment: ConfigurableEnvironment) {
        val present = ConfigurationPropertySources.get(environment)
            .filterIsInstance<IterableConfigurationPropertySource>()
            .any { source -> source.any { LEGACY.isAncestorOf(it) } }
        if (present) {
            throw ChatException(MESSAGE)
        }
    }
}
