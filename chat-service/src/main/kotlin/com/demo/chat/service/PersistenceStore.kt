package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface PersistenceStore<ENTITY> {
    fun key(): Mono<out UUIDKey>
    fun add(ent: ENTITY): Mono<Void>
    fun rem(key: UUIDKey): Mono<Void>
    fun get(key: UUIDKey): Mono<out ENTITY>
    fun all(): Flux<out ENTITY>
    fun byIds(keys: List<UUIDKey>): Flux<out ENTITY> = Flux.empty()
}

interface UserPersistence : PersistenceStore<User>

interface TopicPersistence : PersistenceStore<EventTopic>

interface MembershipPersistence : PersistenceStore<Membership<UUIDKey>>

interface TextMessagePersistence : PersistenceStore<TextMessage>

interface KeyPersistence : PersistenceStore<UUIDKey>