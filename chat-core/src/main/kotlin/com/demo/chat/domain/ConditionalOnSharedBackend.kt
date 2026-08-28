package com.demo.chat.domain

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Activates a configuration when either core selector names this backend.
 *
 * Generated ids reach the key store and the persistence store. A condition
 * on one selector alone would leave `key=memory` with `persistence=redis`
 * unprotected, and the configuration permits that pair.
 *
 * `@ConditionalOnProperty` cannot express an OR across two properties.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Conditional(OnSharedBackendCondition::class)
annotation class ConditionalOnSharedBackend(val value: String)

class OnSharedBackendCondition : Condition {

    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val backend = metadata
            .getAnnotationAttributes(ConditionalOnSharedBackend::class.java.name)
            ?.get("value") as? String
            ?: return false

        val environment = context.environment
        return environment.getProperty("app.service.core.key") == backend ||
            environment.getProperty("app.service.core.persistence") == backend
    }
}
