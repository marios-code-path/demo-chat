package com.demo.chat.security.access

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.command.SubmitterIdentity
import reactor.core.publisher.Mono

/**
 * The sender and owner of an ordinary submission is the authenticated user.
 * `ContextIdentity` holds the rule. Every anonymous caller holds one shared
 * `Anon` key, so `Anon` owns nothing here. The spec scopes request identity to
 * an authenticated owner. An empty answer refuses the call.
 */
class ContextSubmitterIdentity<T>(
    private val identity: ContextIdentity<T>,
    private val rootKeys: RootKeys<T>,
) : SubmitterIdentity<T> {
    override fun current(): Mono<Key<T>> = identity.identity().filter { it != rootKeys.anon() }
}
