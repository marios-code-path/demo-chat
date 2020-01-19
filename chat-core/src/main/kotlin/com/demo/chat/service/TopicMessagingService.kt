package com.demo.chat.service

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxProcessor
import reactor.core.publisher.Mono

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
interface TopicMessagingService<T, V> {
    fun subscribe(member: T, topic: T): Mono<Void>
    fun unSubscribe(member: T, topic: T): Mono<Void>
    fun unSubscribeAll(member: T): Mono<Void> // then closes
    fun unSubscribeAllIn(topic: T): Mono<Void>
    fun sendMessage(message: Message<T, V>): Mono<Void>
    fun receiveOn(topic: T): Flux<out Message<T, V>>
    // receive externally bound resources by attaching a processor to that resources flux
    // 1.  attach to some external source (like a redis pubsub channel)
    // 2.  create FluxProcessor that will handle fanout from our source to any subscriber fluxes
    // 3.  subscribe our processor to the source
    fun receiveSourcedEvents(topic: T): Flux<out Message<T, V>>

    fun exists(topic: T): Mono<Boolean>
    //fun keyExists(topic: T, key: T): Mono<Boolean>
}

interface ChatTopicMessagingService<T, V> : TopicMessagingService<T, V> {
    fun add(id: T): Mono<Void>
    fun rem(id: T): Mono<Void>
    fun getByUser(uid: T): Flux<T>
    fun getUsersBy(id: T): Flux<T>
    fun getProcessor(id: T): FluxProcessor<out Message<T, V>, out Message<T, V>>
}