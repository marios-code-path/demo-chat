package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.Membership
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * given [T]ype of key, in [E]ntity, [Q]uery request, [W]rite Query request
 */
interface IndexService<E, Q, W> {
    fun add(entity: E, criteria: W): Mono<Void>
    fun rem(entity: E): Mono<Void>
    fun findBy(query: Q): Flux<out Key<out Any>>
}

interface MapQueryIndexService<E> : IndexService<E, Map<String, E>, Map<E, String>>

interface UserIndexService<T> : IndexService<User<T>, Map<String, String>, Map<String, String>>

interface TopicIndexService<T> : MapQueryIndexService<MessageTopic<T>> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService<T> : MapQueryIndexService<Membership<T>> {
    fun size(roomId: Key<T>): Mono<Int>
    fun addMember(membership: Membership<T>): Mono<Void>
    fun remMember(membership: Membership<T>): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

interface MessageIndexService<T> : MapQueryIndexService<Membership<T>> {
    companion object {
        const val TOPIC = "topicId"
        const val USER = "userId"
    }
}