package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * given [T]ype of key, in [E]ntity, [Q]uery request, [W]rite Query request
 */
interface IndexService<T, E, Q, W> {
    fun add(entity: E, criteria: W): Mono<Void>
    fun rem(entity: E): Mono<Void>
    fun findBy(query: Q): Flux<out Key<T>>
}

interface UserIndexService<T> : IndexService<T, User<T>, Map<String, String>, Map<String, String>>

interface TopicIndexService<T> : IndexService<T, MessageTopic<T>, Map<String, String>, Map<String, String>> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService<T> : IndexService<T, Membership<T>, Map<String, T>, Map<T, String>> {
    fun size(roomId: Key<T>): Mono<Int>
    fun addMember(membership: Membership<T>): Mono<Void>
    fun remMember(membership: Membership<T>): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

interface TextMessageIndexService<T> : IndexService<T, TextMessage<T>, Map<String, T>, Map<String, String>> {
    companion object {
        const val TOPIC = "topicId"
        const val USER = "userId"
    }
}