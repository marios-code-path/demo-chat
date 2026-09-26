package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.RootKeyStore
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * The root key store of the memory backend. It keeps the roots in the process.
 * **It is not stable across a restart**, because the whole memory store is lost
 * at a restart. See `CHAT-avduuqwp`.
 */
class RootKeyStoreInMemory<T : Any> : RootKeyStore<T> {

    private val roots = ConcurrentHashMap<ChatDomain, T>()

    override fun read(): Mono<Map<ChatDomain, T>> = Mono.fromCallable { roots.toMap() }

    override fun createIfAbsent(domain: ChatDomain, id: T): Mono<T> =
        Mono.fromCallable { roots.putIfAbsent(domain, id) ?: id }
}
