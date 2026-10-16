package com.demo.chat.config

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertyResolver

/**
 * One feature that supports a limited set of key types. See `CHAT-zxrgxdlx`.
 *
 * A deployment that sets [switch] must launch with an `app.key.type` in [supported].
 */
data class KeyTypeRequirement(
    val feature: String,
    val switch: String,
    val supported: Set<String>,
    val reason: String,
)

/**
 * The startup check of the key type. Each feature declares the key types that it supports.
 *
 * The check reads the launch declaration. It does not detect a store built for another key type.
 */
object KeyTypeValidation {
    const val KEY_TYPE = "app.key.type"
    const val IMPORT = "app.message.import"

    val REQUIREMENTS: List<KeyTypeRequirement> = listOf(
        KeyTypeRequirement(
            feature = "claude-import",
            switch = IMPORT,
            supported = setOf("uuid"),
            reason = "An imported message id is a name-based UUID, derived from the room id and the source id. " +
                "A long key type stores a bigint id, and a bigint cannot hold a UUID.",
        ),
    )

    /**
     * The rule of `CoreRestControllers.enabled`. A present value enables the
     * feature, an empty value included, unless the value is `false`.
     */
    fun enabled(properties: PropertyResolver, requirement: KeyTypeRequirement): Boolean =
        properties.getProperty(requirement.switch)
            ?.let { !it.equals("false", ignoreCase = true) }
            ?: false

    /**
     * Refuses the first enabled feature whose key type is unset or unsupported.
     * The comparison ignores case, as the `havingValue` of `BaseDomainConfiguration` does.
     */
    fun validate(properties: PropertyResolver) {
        val keyType = properties.getProperty(KEY_TYPE)
        REQUIREMENTS.filter { enabled(properties, it) }.forEach { requirement ->
            if (requirement.supported.none { it.equals(keyType, ignoreCase = true) }) {
                val measured = if (keyType.isNullOrBlank()) "$KEY_TYPE is not set" else "$KEY_TYPE=$keyType"
                throw IllegalStateException(
                    "${requirement.feature} is enabled by ${requirement.switch}, and $measured. " +
                        "Supported key types: ${requirement.supported.joinToString(prefix = "[", postfix = "]")}. " +
                        requirement.reason
                )
            }
        }
    }
}

open class KeyTypeValidationPostProcessor(private val environment: Environment) : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        KeyTypeValidation.validate(environment)
    }
}

// The module does not enable the Kotlin all-open compiler plugin, so this class is open.
@Configuration
open class KeyTypeValidationConfiguration {
    companion object {
        /** Static, as Spring requires of a `BeanFactoryPostProcessor` bean. */
        @Bean
        @JvmStatic
        fun keyTypeValidation(environment: Environment): BeanFactoryPostProcessor =
            KeyTypeValidationPostProcessor(environment)
    }
}
