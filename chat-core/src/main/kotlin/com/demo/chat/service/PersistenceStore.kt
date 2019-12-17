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
    fun byIds(keys: List<out Key<K>>): Flux<out V> = Flux.empty()
}

interface UserPersistence<K> : PersistenceStore<K, User<K>>

interface TopicPersistence<K> : PersistenceStore<K, MessageTopic<K>>

interface MembershipPersistence<K> : PersistenceStore<K, Membership<K>>

interface TextMessagePersistence<K> : PersistenceStore<K, TextMessage<K>>

interface KeyPersistence<K> : PersistenceStore<K, Key<K>>