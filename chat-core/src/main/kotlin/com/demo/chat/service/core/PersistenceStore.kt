package com.demo.chat.service.core

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

interface EnricherPersistenceStore<T, V, E : Any> : PersistenceStore<T, E> {
    fun addEnriched(data: V): Mono<E>
}

interface PersistenceStore<T, E : Any> {
    fun key(): Mono<out Key<T>>
    fun add(ent: E): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
    fun get(key: Key<T>): Mono<out E>
    fun all(): Flux<out E>
    fun byIds(keys: List<Key<T>>): Flux<out E> = Flux.empty()
}

interface UserPersistence<T> : PersistenceStore<T, User<T>>

interface TopicPersistence<T> : PersistenceStore<T, MessageTopic<T>>

interface MembershipPersistence<T> : PersistenceStore<T, TopicMembership<T>>

interface MessagePersistence<T, V> : PersistenceStore<T, Message<T, V>>

interface KeyValueStore<T, V> : PersistenceImpl<T, KeyValuePair<T, V>> {
    fun <E> typedGet(key: Key<T>, typeArgument: Class<E>): Mono<KeyValuePair<T,E>> = Mono.empty()
    fun <E> typedAll(typeArgument: Class<E>): Flux<KeyValuePair<T,E>> = Flux.empty()
    fun <E> typedByIds(ids: List<Key<T>>, typedArgument: Class<E>): Flux<KeyValuePair<T,E>> = Flux.empty()
}

/**
 * A string keyed store for values that a process needs before its domain
 * stores exist, such as the root key snapshot. It holds no domain key. See
 * `CHAT-avduuqwp`.
 */
interface InitializingKVStore {
    /** This method returns the value stored under [name], or an empty Mono. */
    fun read(name: String): Mono<String>

    fun write(name: String, value: String): Mono<Void>

    fun remove(name: String): Mono<Void>

    /** This method returns every stored name. */
    fun names(): Flux<String>
}

interface PersistenceImpl<T, V : Any> : PersistenceStore<T, V>