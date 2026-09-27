package com.demo.chat.controller.resolve

import com.demo.chat.domain.knownkey.ChatDomain

/**
 * A `Key` payload that verifies to a `VerifiedKey` before the handler runs.
 * See `CHAT-avduuqwp`, D1.
 *
 * - One [domain] names the domain of the key.
 * - No [domain] means the [KeyDomain] of the controller class.
 * - [anyDomain] verifies the key in its stored domain, with no domain check.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Verified(
    vararg val domain: ChatDomain,
    val anyDomain: Boolean = false,
)

/**
 * The domain that a controller class serves. A [Verified] key with no domain
 * verifies in it. A proxy subclass inherits it.
 */
@java.lang.annotation.Inherited
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KeyDomain(val value: ChatDomain)

/** The [KeyDomain] of a controller class. A class with none fails, because its routes cannot verify. */
fun keyDomainOf(type: Class<*>): ChatDomain =
    type.getAnnotation(KeyDomain::class.java)?.value
        ?: throw IllegalStateException("The controller ${type.name} declares no @KeyDomain.")
