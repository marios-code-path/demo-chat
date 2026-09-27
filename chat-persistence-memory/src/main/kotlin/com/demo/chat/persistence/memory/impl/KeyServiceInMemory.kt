package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

/**
 * The key registry of the memory backend. It mints each key under the root of
 * its domain, and it records the root of every id it mints. See
 * `CHAT-avduuqwp`.
 */
open class KeyServiceInMemory<T>(private val keyGen: Supplier<T>, private val rootKeys: RootKeys<T>) : IKeyService<T> {
    private val roots = ConcurrentHashMap<T & Any, T & Any>()

    override fun key(domain: ChatDomain): Mono<out Key<T>> = Mono.fromCallable {
        val root = rootKeys.of(domain).id!!
        val id = keyGen.get()!!
        roots[id] = root
        Key.of<T>(id, root)
    }

    override fun rem(key: Key<T>): Mono<Void> =
        if (rootKeys.domainOfRoot(key.id) != null) Mono.error(RootKeyDeletionException(key.id))
        else Mono.fromRunnable { roots.remove(key.id!!) }

    override fun exists(key: Key<T>): Mono<Boolean> = rootOf(key.id).hasElement()

    /** The read waits for a subscriber, so a chain that removes first sees the removal. */
    override fun rootOf(id: T): Mono<T & Any> = Mono.fromCallable {
        if (rootKeys.domainOfRoot(id) != null) id!! else roots[id!!]
    }
}
