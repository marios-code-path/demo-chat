package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatPersistence<ENTITY> {
    fun key(): Mono<out UUIDKey>
    fun add(ent: ENTITY): Mono<Void>
    fun rem(key: UUIDKey): Mono<Void>
    fun get(key: UUIDKey): Mono<out ENTITY>
    fun all(): Flux<out ENTITY>
    fun byIds(keys: List<UUIDKey>): Flux<out ENTITY> = Flux.empty()
    // consider setModel(SomeEntityModel)
}

interface UserPersistence : ChatPersistence<User>

interface RoomPersistence : ChatPersistence<EventTopic>

interface MembershipPersistence : ChatPersistence<Membership<UUIDKey>>

interface TextMessagePersistence : ChatPersistence<TextMessage>

interface KeyPersistence : ChatPersistence<UUIDKey>