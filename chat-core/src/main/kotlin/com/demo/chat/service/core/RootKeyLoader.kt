package com.demo.chat.service.core

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.knownkey.ChatDomain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * This class loads the root id of every domain from a [RootKeyStore]. It
 * creates each missing root with a conditional write.
 *
 * - A restart reads the stored roots and creates none.
 * - Two loaders that start at once use the one root that won each write.
 * - The load fails when any domain still has no root. A node never serves
 *   with a partial set.
 */
class RootKeyLoader<T : Any>(private val store: RootKeyStore<T>, private val ids: IKeyGenerator<T>) {

    fun load(): Mono<Map<ChatDomain, T>> = store.read().flatMap { stored ->
        Flux.fromIterable(ChatDomain.entries)
            .concatMap { domain ->
                stored[domain]?.let { Mono.just(domain to it) }
                    ?: store.createIfAbsent(domain, ids.nextId()).map { domain to it }
            }
            .collectMap({ it.first }, { it.second })
    }.flatMap { roots ->
        val missing = ChatDomain.entries - roots.keys
        if (missing.isEmpty()) Mono.just(roots)
        else Mono.error(ChatException("The root key set is incomplete. Missing: $missing"))
    }
}
