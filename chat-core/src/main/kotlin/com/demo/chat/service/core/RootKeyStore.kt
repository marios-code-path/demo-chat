package com.demo.chat.service.core

import com.demo.chat.domain.knownkey.ChatDomain
import reactor.core.publisher.Mono

/**
 * The stored root id of each domain, for one key type in one store.
 *
 * This is the only place where a root is created. See `CHAT-avduuqwp`, and
 * `CHAT-bafkgkko` for stable roots.
 */
interface RootKeyStore<T : Any> {

    /** This method returns every stored root id, by domain. */
    fun read(): Mono<Map<ChatDomain, T>>

    /**
     * This method stores [id] only when [domain] has no root. It returns [id],
     * or the root that a competing writer stored first.
     */
    fun createIfAbsent(domain: ChatDomain, id: T): Mono<T>
}
