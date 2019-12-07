package com.demo.chat.service

import com.demo.chat.domain.EventKey
import com.demo.chat.domain.Message
import com.demo.chat.domain.TopicMessageKey
import reactor.core.publisher.*
import java.util.*

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

interface BooleanTopicService<T : EventKey, V> : TopicService<T, V> {
    fun add(topic: T, message: V): Mono<EventKey>
    fun compute(topic: T): Mono<Set<V>>
    fun reset(topic: T, startKey: T): Mono<EventKey>
}

interface CountingTopicService<T : EventKey, V> : TopicService<T , V> {
    fun add(topic: T, message: V): Mono<EventKey>
    fun rem(topic: T, message: V): Mono<EventKey>
    fun compute(topic: T): Mono<Set<V>>
    fun reset(topic: T, startKey: T): Mono<EventKey>

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