package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * for given Type T, Given Key [K], and Query[Q] we will add, remove and seek K's for a given WriteCriteria[WQ]
 */
interface IndexService<out K : EventKey, in T, Q, WQ> {
    fun add(entity: T, criteria: WQ): Mono<Void>
    fun rem(entity: T): Mono<Void>
    fun findBy(query: Q): Flux<out K>
}

interface MapQueryIndexService<K : EventKey, T> : IndexService<K, T, Map<String, String>, Map<String, String>>

// Split out specific definitions for visibility elsewhere (where's DDD's idea here? )
interface UserIndexService : IndexService<UserKey, User, Map<String, String>, Map<String, String>>

interface RoomIndexService : MapQueryIndexService<RoomKey, Room> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService : IndexService<EventKey, RoomMembership,Map<String, String>, Map<String, String>> {
    fun size(roomId: EventKey): Mono<Int>
    fun addMember(membership: RoomMembership): Mono<Void>
    fun remMember(membership: RoomMembership): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

interface MessageIndexService : IndexService<TextMessageKey, TextMessage,Map<String, String>, Map<String, String>> {
    companion object {
        const val TOPIC = "topicId"
        const val USER = "userId"
    }
}