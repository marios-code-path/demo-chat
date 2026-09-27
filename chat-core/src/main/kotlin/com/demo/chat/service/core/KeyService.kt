package com.demo.chat.service.core

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import reactor.core.publisher.Mono

/**
 * The key registry. It mints each key with the root of its domain, and it
 * records the root of every id it mints. See `CHAT-avduuqwp`.
 */
interface IKeyService<T> {
    /** This method mints a key under the root of [domain]. It may refuse a domain with `UnsupportedDomainException`. */
    fun key(domain: ChatDomain): Mono<out Key<T>>

    /** This method rejects a root key with `RootKeyDeletionException`. */
    fun rem(key: Key<T>): Mono<Void>

    fun exists(key: Key<T>): Mono<Boolean>

    /** This method returns an empty Mono for an unknown id. It returns the id itself for a root key. */
    fun rootOf(id: T): Mono<T & Any>
}

/** A source of ids. A generator makes ids, and a key service makes keys. */
interface IKeyGenerator<T> {
    fun nextId(): T
}
