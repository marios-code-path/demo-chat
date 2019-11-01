package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatPersistence<OT, in KT>{
    fun key(): Mono<out EventKey>
    fun add(o: KT): Mono<Void>
    fun rem(key: EventKey): Mono<Void>
    fun get(key: EventKey): Mono<out OT>
    fun all(): Flux<out OT>
    fun byIds(keys: List<EventKey>): Flux<out OT> = Flux.empty()
}

interface ChatUserPersistence : ChatPersistence<User, User>

interface ChatRoomPersistence : ChatPersistence<Room, Room>

interface ChatMembershipPersistence : ChatPersistence<Membership<EventKey>, Membership<in EventKey>>

interface TextMessagePersistence : ChatPersistence<TextMessage, TextMessage>