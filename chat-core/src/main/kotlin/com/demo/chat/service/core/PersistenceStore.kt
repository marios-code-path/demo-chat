package com.demo.chat.service.core

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface EnricherPersistenceStore<T, V, E>: PersistenceStore<T, E> {
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