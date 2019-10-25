package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

interface ChatPersistence<OT, KT : EventKey>{
    fun key(): Mono<out EventKey>
    fun add(o: OT): Mono<Void>
    fun rem(key: EventKey): Mono<Void>
    fun get(key: EventKey): Mono<out OT>
    fun all(): Flux<out OT>
}

interface ChatUserPersistence : ChatPersistence<User, UserKey>

interface ChatRoomPersistence : ChatPersistence<Room, RoomKey>

interface TextMessagePersistence : ChatPersistence<TextMessage, TextMessageKey>