package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * for given Type T, Given Key [K], and Query[Q] we will add, remove and seek K's for a given WriteCriteria[WQ]
 */
interface ChatIndexService<out K : EventKey, in T, Q, WQ> {
    fun add(entity: T, criteria: WQ): Mono<Void>
    fun rem(entity: T): Mono<Void>
    fun findBy(query: Q): Flux<out K>
}

// Split out specific definitions for visibility elsewhere (where's DDD's idea here? )
interface ChatUserIndexService : ChatIndexService<UserKey, User, Map<String, String>, Map<String, String>>

interface ChatRoomIndexService : ChatIndexService<RoomKey, Room, Map<String, String>, Map<String, String>> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface ChatMembershipIndexService : ChatIndexService<EventKey, RoomMembership, Map<String, String>, Map<String, String>> {
    fun size(roomId: EventKey): Mono<Int>
    fun addMember(membership: RoomMembership): Mono<Void>
    fun remMember(membership: RoomMembership): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

interface ChatMessageIndexService : ChatIndexService<TextMessageKey, TextMessage, Map<String, String>, Map<String, String>>