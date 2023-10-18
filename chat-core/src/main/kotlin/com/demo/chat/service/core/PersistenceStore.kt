package com.demo.chat.service.core

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

interface EnricherPersistenceStore<T, V, E> : PersistenceStore<T, E> {
    fun addEnriched(data: V): Mono<E>
}

interface PersistenceStore<T, E> {
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

interface InitializingKVStore : KeyValueStore<String, String>

interface PersistenceImpl<T, V> : PersistenceStore<T, V>