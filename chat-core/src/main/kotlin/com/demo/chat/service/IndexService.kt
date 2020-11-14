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
}

/**
 * Defaults, these can be auto-generated, but shouldnt affect
 * I.E. Dont use these interfaces downstream for any other reason
 * than directly implementing them ( this is a demo after all )
 */
interface UserIndexService<T> : IndexService<T, User<T>, Map<String, String>> {
    companion object {
        const val NAME = "name"
        const val IMAGEURI = "imageUri"
        const val ACTIVE = "active"
        const val HANDLE = "handle"
        const val ID = "userId"
    }
}

interface TopicIndexService<T> : IndexService<T, MessageTopic<T>, Map<String, String>> {
    companion object {
        const val NAME = "name"
        const val ID = "ID"
        const val IDS = "IDS"
        const val ALL = "ALL"
        const val USERIN = "USERIN"
    }
}

interface MembershipIndexService<T> : IndexService<T, TopicMembership<T>, Map<String, T>> {
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
interface MessageIndexService<T, V> : IndexService<T, Message<T, V>, Map<String, T>> {
    companion object {
        const val ID = "msgId"
        const val TOPIC = "topicId"
        const val USER = "userId"
        const val DATA = "data"
    }
}