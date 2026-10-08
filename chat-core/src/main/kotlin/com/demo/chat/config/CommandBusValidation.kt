package com.demo.chat.config

import com.demo.chat.domain.command.BackendId
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertyResolver

/**
 * The Stage 1 startup check of the command bus. Decisions 3, 4, and 5 of the spec.
 *
 * The replica check reads a declaration. It does not detect other processes.
 * The handler contract check runs where the runtime is built, because it reads beans.
 */
object CommandBusValidation {
    const val COMPOSITE = "app.service.composite"
    const val BUS = "app.command.bus"
    const val TTL = "app.command.ttl"
    const val REPLICAS = "app.command.replicas"
    const val REQUIREMENT = "app.command.completion.requirement"
    const val TIMEOUT = "app.command.completion.timeout"
    const val RECOVERY_INTERVAL = "app.command.recovery.interval"
    const val VECTOR = "app.service.core.vector"

    const val DEFAULT_REQUIREMENT = "P,I"
    const val DEFAULT_TIMEOUT = "5s"
    const val DEFAULT_RECOVERY_INTERVAL = "30s"

    /** Stage 1 providers with safe-repeat evidence in CI. Task 17 holds that evidence. */
    val SUPPORTED_PROVIDERS: Map<String, Set<String>> = linkedMapOf(
        "app.service.core.key" to setOf("memory", "redis", "cassandra"),
        "app.service.core.persistence" to setOf("memory", "redis", "cassandra"),
        "app.service.core.index" to setOf("lucene", "cassandra"),
        "app.service.core.pubsub" to setOf("memory", "redis-pubsub", "kafka"),
        "app.service.core.vector" to setOf("simple", "embedded", "redis"),
    )

    fun appliesTo(env: PropertyResolver): Boolean =
        env.containsProperty(COMPOSITE) && env.getProperty(COMPOSITE) != "false"

    fun validate(env: PropertyResolver): CommandBusSettings {
        when (val bus = env.getProperty(BUS)?.trim()) {
            null, "" -> throw IllegalStateException("$BUS is not set. Stage 1 accepts $BUS=memory.")
            "memory" -> Unit
            "kafka" -> throw IllegalStateException("$BUS=kafka is Stage 2 work. Stage 1 accepts $BUS=memory.")
            else -> throw IllegalStateException("$BUS=$bus is unknown. Stage 1 accepts $BUS=memory.")
        }

        val ttl = env.getProperty(TTL)?.trim()
        if (!ttl.isNullOrEmpty() && ttl != "disabled") {
            throw IllegalStateException("Stage 1 rejects a finite $TTL=$ttl. Remove it or set it to disabled.")
        }

        val replicas = env.getProperty(REPLICAS)?.trim()
        if (!replicas.isNullOrEmpty() && replicas != "1") {
            throw IllegalStateException(
                "$REPLICAS=$replicas is unsupported with $BUS=memory. Stage 1 runs one process. " +
                    "This check reads the declaration and does not detect other processes."
            )
        }

        SUPPORTED_PROVIDERS.forEach { (property, supported) ->
            val value = env.getProperty(property)?.trim()
            if (!value.isNullOrEmpty() && value !in supported) {
                throw IllegalStateException(
                    "Stage 1 has no safe-repeat evidence for $property=$value. Supported values: ${supported.joinToString()}."
                )
            }
        }

        val requirementText = env.getProperty(REQUIREMENT, DEFAULT_REQUIREMENT)
        val settings = try {
            CommandBusSettings.from(env)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("$REQUIREMENT=$requirementText is not valid. ${e.message}", e)
        }
        if (BackendId.VECTOR in settings.requirement.backends && env.getProperty(VECTOR).isNullOrBlank()) {
            throw IllegalStateException(
                "$REQUIREMENT=$requirementText names V, and $VECTOR is not set. No vector handler is active."
            )
        }
        return settings
    }
}

open class CommandBusValidationPostProcessor(private val environment: Environment) : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        if (CommandBusValidation.appliesTo(environment)) CommandBusValidation.validate(environment)
    }
}
