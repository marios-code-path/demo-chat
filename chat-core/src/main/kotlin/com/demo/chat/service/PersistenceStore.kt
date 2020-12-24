package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


interface PersistenceStore<T, E> {
    fun key(): Mono<out Key<T>>
    fun add(ent: E): Mono<Void>
    fun assemble(ent: E): Mono<E> = add(ent).then(Mono.just(ent))
    fun rem(key: Key<T>): Mono<Void>
    fun get(key: Key<T>): Mono<out E>
    fun all(): Flux<out E>
    fun byIds(keys: List<Key<T>>): Flux<out E> = Flux.empty()
}

interface UserPersistence<T> : PersistenceStore<T, User<T>>

interface TopicPersistence<T> : PersistenceStore<T, MessageTopic<T>>

interface MembershipPersistence<T> : PersistenceStore<T, TopicMembership<T>>

interface MessagePersistence<T, E, V> : PersistenceStore<T, Message<T, V>>