package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * for given Type T, Given Key [K], and Query[Q] we will add, remove and seek K's for a given WriteCriteria[WQ]
 */
interface IndexService<K, in T, Q, WQ> {
    fun add(entity: T, criteria: WQ): Mono<Void>
    fun rem(entity: T): Mono<Void>
    fun findBy(query: Q): Flux<out Key<K>>
}

interface MapQueryIndexService<K, T> : IndexService<K, T, Map<String, String>, Map<String, String>>

// Split out specific definitions for visibility elsewhere (where's DDD's idea here? )
interface UserIndexService : IndexService<UUID, User, Map<String, String>, Map<String, String>>

interface TopicIndexService : MapQueryIndexService<UUID, MessageTopic<UUID>> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService : IndexService<UUID, TopicMembership,Map<String, String>, Map<String, String>> {
    fun size(roomId: UUIDKey): Mono<Int>
    fun addMember(membership: TopicMembership): Mono<Void>
    fun remMember(membership: TopicMembership): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

interface MessageIndexService : IndexService<UUID, TextMessage<UUID>,Map<String, String>, Map<String, String>> {
    companion object {
        const val TOPIC = "topicId"
        const val USER = "userId"
    }
}