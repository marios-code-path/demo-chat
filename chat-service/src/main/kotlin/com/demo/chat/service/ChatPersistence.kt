package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatPersistence<ENTITY> {
    fun key(): Mono<out EventKey>
    fun add(ent: ENTITY): Mono<Void>
    fun rem(key: EventKey): Mono<Void>
    fun get(key: EventKey): Mono<out ENTITY>
    fun all(): Flux<out ENTITY>
    fun byIds(keys: List<EventKey>): Flux<out ENTITY> = Flux.empty()
    // consider setModel(SomeEntityModel)
}

interface UserPersistence : ChatPersistence<User>

interface RoomPersistence : ChatPersistence<Room>

interface MembershipPersistence : ChatPersistence<Membership<EventKey>>

interface TextMessagePersistence : ChatPersistence<TextMessage>

interface KeyPersistence : ChatPersistence<EventKey>