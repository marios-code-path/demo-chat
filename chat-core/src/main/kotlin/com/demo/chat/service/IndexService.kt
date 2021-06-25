package com.demo.chat.service

import com.demo.chat.domain.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// needs a codec
// usage: codec.decode(E) -> W
/**
 * given [T]ype of key, [E] entity, [Q]uery
 */
interface IndexService<T, E, Q> {
    fun add(entity: E): Mono<Void>
    fun rem(key: Key<T>): Mono<Void>
    fun findBy(query: Q): Flux<out Key<T>>
    fun findOneBy(query: Q): Mono<out Key<T>>
}

/**
 * TODO: refactor to bootstrapped properties
 *
 * Defaults, these can be auto-generated, but shouldnt affect
 * I.E. Dont use these interfaces downstream for any other reason
 * than directly implementing them ( this is a demo after all )
 */
interface UserIndexService<T, Q> : IndexService<T, User<T>, Q> {
    companion object {
        const val NAME = "name"
        const val IMAGEURI = "imageUri"
        const val ACTIVE = "active"
        const val HANDLE = "handle"
        const val ID = "userId"
    }
}

interface TopicIndexService<T, Q> : IndexService<T, MessageTopic<T>, Q> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService<T, Q> : IndexService<T, TopicMembership<T>, Q> {
    fun size(key: Key<T>): Mono<Int>
    fun addMember(topicMembership: TopicMembership<T>): Mono<Void>
    fun remMember(topicMembership: TopicMembership<T>): Mono<Void>

    companion object {
        const val ID = "ID"
        const val MEMBER = "member"
        const val MEMBEROF = "memberOf"
    }
}

// TODO: that query needs to be Map<String, String> not String, T
interface MessageIndexService<T, V, Q> : IndexService<T, Message<T, V>, Q> {
    companion object {
        const val ID = "msgId"
        const val TOPIC = "topicId"
        const val USER = "userId"
        const val DATA = "data"
    }
}