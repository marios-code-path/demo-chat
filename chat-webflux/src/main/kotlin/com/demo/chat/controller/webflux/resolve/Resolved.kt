package com.demo.chat.controller.webflux.resolve

import com.demo.chat.domain.knownkey.ChatDomain

/**
 * A path id that resolves to a `VerifiedKey` before the handler runs. See
 * `CHAT-avduuqwp`, D2.
 *
 * - One [domain] names the domain of the id.
 * - No [domain] means the domain of the handler. The handler implements
 *   [DomainScoped].
 * - [anyDomain] resolves the id in its stored domain, with no domain check.
 *
 * The parameter name is the path variable name, unless [name] names it.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Resolved(
    vararg val domain: ChatDomain,
    val anyDomain: Boolean = false,
    val name: String = "",
)

/** A handler that serves one domain. A [Resolved] path id with no domain resolves in it. */
interface DomainScoped {
    fun domain(): ChatDomain
}
