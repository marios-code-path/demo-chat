package com.demo.chat.service

import com.demo.chat.domain.UUIDKey
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import reactor.core.publisher.*
import java.util.*

/**
 * GIVEN: PRINCIPLE = TOPIC[MEMBERSHIP, MESSAGE, STAT]
 * GIVEN: ROOM = TOPIC[STAT, MEMBERSHIP, MESSAGE]
 *  ROOM.MEMBERSHIP && ROOM.STAT == true
 *  ROOM.MESSAGE && ROOM.STAT == true
 *  ROOM.STAT != PRINCIPLE.STAT
 *  PRINCIPLE is owner of ROOM. PRINCIPLE creates ROOM
 *  ROOM receives individual ID
 *  thus
 *  createRoom(name) = ROOM.apply {
 *      MEMBERSHIP = createExchangeTopic(MEMBERSHIP)
 *      STAT = this
 *      MESSAGES = createTopic(MESSAGE)
 *  }
 *  to join said room:
 *
 * svc.subscribe to (look up by user_id or room_id) ROOM_ID
 * svc.subscribe to ROOM[MEMBERSHIP]
 * svc.send to ROOM[MEMBERSHIP] with PRINCIPLE[MEMBERSHIP]
 * svc.send ( message ) to ROOM[MESSAGES]
 */
interface TopicService<T, V> {
    fun subscribe(member: T, topic: T): Mono<Void>
    fun unSubscribe(member: T, topic: T): Mono<Void>
    fun unSubscribeAll(member: T): Mono<Void> // then closes
    fun unSubscribeAllIn(topic: T): Mono<Void>
    fun sendMessage(message: V): Mono<Void>
    fun receiveOn(topic: T): Flux<out V>
    // receive externally bound resources by attaching a processor to that resources flux
    // 1.  attach to some external source (like a redis pubsub channel)
    // 2.  create FluxProcessor that will handle fanout from our source to any subscriber fluxes
    // 3.  subscribe our processor to the source
    fun receiveSourcedEvents(topic: T): Flux<out V>
    fun exists(topic: T): Mono<Boolean>
    //fun keyExists(topic: T, key: T): Mono<Boolean>
}

interface BooleanTopicService<T : UUIDKey, V> : TopicService<T, V> {
    fun add(topic: T, message: V): Mono<UUIDKey>
    fun compute(topic: T): Mono<Set<V>>
    fun reset(topic: T, startKey: T): Mono<UUIDKey>
}

interface CountingTopicService<T : UUIDKey, V> : TopicService<T , V> {
    fun add(topic: T, message: V): Mono<UUIDKey>
    fun rem(topic: T, message: V): Mono<UUIDKey>
    fun compute(topic: T): Mono<Set<V>>
    fun reset(topic: T, startKey: T): Mono<UUIDKey>

}

interface ChatTopicService : TopicService<UUID, Message<TopicMessageKey, Any>> {
    fun add(id: UUID): Mono<Void>
    fun rem(id: UUID): Mono<Void>
    fun getTopicsByUser(uid: UUID): Flux<UUID>
    fun getUsersBy(id: UUID): Flux<UUID>
}

interface ChatTopicServiceAdmin {
    fun getProcessor(id: UUID): FluxProcessor<out Message<TopicMessageKey, Any>, out Message<TopicMessageKey, Any>>
}