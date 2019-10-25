package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Given Key [K], and Query[Q] we will add, remove and seek K's for a given [WQ]
 */
interface ChatIndexService<K : EventKey, Q, WQ> {
    fun add(key: K, criteria: WQ): Mono<Void>
    fun rem(key: K): Mono<Void>
    fun findBy(query: Q): Flux<out K>
}

// Split out specific definitions for visibility elsewhere (where's DDD's idea here? )
interface ChatUserIndexService : ChatIndexService<UserKey, Map<String, String>, Map<String, String>>

interface ChatRoomIndexService : ChatIndexService<RoomKey, Map<String, String>, Map<String, String>> {
    fun size(roomId: EventKey): Mono<Int>
    fun addMember(uid: EventKey, roomId: EventKey): Mono<Void>
    fun remMember(uid: EventKey, roomId: EventKey): Mono<Void>
}

interface ChatMessageIndexService : ChatIndexService<TextMessageKey, Map<String, String>, Map<String, String>>