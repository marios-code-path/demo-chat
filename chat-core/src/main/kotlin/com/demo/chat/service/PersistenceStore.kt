package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface PersistenceStore<K, V> {
    fun key(): Mono<out Key<K>>
    fun add(ent: V): Mono<Void>
    fun rem(key: Key<K>): Mono<Void>
    fun get(key: Key<K>): Mono<out V>
    fun all(): Flux<out V>
    fun byIds(keys: List<Key<K>>): Flux<out V> = Flux.empty()
}

interface UserPersistence : PersistenceStore<User, UUID>

interface TopicPersistence<K: UUID> : PersistenceStore<K, MessageTopic<K>>

interface MembershipPersistence<K: UUID> : PersistenceStore<K, Membership<UUIDKey>>

interface TextMessagePersistence<K: UUID> : PersistenceStore<K, TextMessage<K>>

interface KeyPersistence<K> : PersistenceStore<Key<K>, K>