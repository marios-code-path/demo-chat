package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceStore<T, V> {
    fun key(): Mono<out Key<T>>
    fun add(ent: V): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
    fun get(key: Key<T>): Mono<out V>
    fun all(): Flux<out V>
    fun byIds(keys: List<out Key<T>>): Flux<out V> = Flux.empty()
}

interface UserPersistence<T> : PersistenceStore<T, User<T>>

interface TopicPersistence<T> : PersistenceStore<T, MessageTopic<T>>

interface MembershipPersistence<T> : PersistenceStore<T, TopicMembership<T>>

interface TextMessagePersistence<T> : PersistenceStore<T, Message<T, String>>

interface KeyPersistence<T> : PersistenceStore<T, Key<T>>